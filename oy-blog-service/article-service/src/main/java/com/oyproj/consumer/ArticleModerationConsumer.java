package com.oyproj.consumer;

import com.oyproj.common.mq.constants.ArticleMQConstant;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleModerationMessage;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ModerationRetrySender;
import com.oyproj.service.ModerationService;
import com.oyproj.service.ModerationVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 文章 AI 审核消费者：消息驱动后台审核。
 * 幂等铁律：一切动作前先查 DB 状态闸；重复消息/过期任务直接返回（自动确认）。
 * 失败路径：attempt < maxAttempt → 延迟重试；>= maxAttempt → 转人工（fail-closed）。
 * 任何异常都被吞掉（不抛出 → 不触发 RabbitMQ 无限 requeue），重试由 RetrySender/兜底扫描负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleModerationConsumer {

    private static final String ATTEMPT_HEADER = "x-attempt";
    private static final String MANUAL_FALLBACK_REASON = "审核服务不可用，转人工审核";

    private final ArticleDao articleDao;
    private final ArticleContentDao contentDao;
    private final ArticlePendingContentMapper pendingContentMapper;
    private final ModerationService moderationService;
    private final ArticleIndexMessageService indexMessageService;
    private final ArticleChapterService chapterService;
    private final ModerationRetrySender retrySender;
    private final ModerationProperties properties;

    @RabbitListener(queues = ArticleMQConstant.ARTICLE_MODERATION_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(ArticleModerationMessage body, @Header(name = ATTEMPT_HEADER, required = false) Integer attempt) {
        // 消息体由默认 Jackson converter 解析；为兼容未带头的首投，attempt 缺省 0。
        // 事务必须放在监听器方法：容器经代理调用，事务真实生效；
        // this.handle() 是同类自调用，代理不拦截，注解放 handle 上从未生效（每条写独立提交）。
        // HTTP 审核调用期间持连接：最坏 30s（moderation.timeout-ms），博客量级可接受。
        handle(body.getArticleId(), attempt == null ? 0 : attempt);
    }

    /** 主处理入口（测试直接调用此方法；生产经 onMessage 的事务代理进入） */
    public void handle(String articleId, int attempt) {
        try {
            Article article = articleDao.getById(articleId);
            if (article == null || article.getDeletedAt() != null) {
                // 作者撤稿：清理待生效区收尾
                pendingContentMapper.deleteById(articleId);
                return;
            }
            ArticlePendingContent pending = pendingContentMapper.selectById(articleId);

            // 幂等闸：只处理"审核中"状态；其余（草稿/已驳回/已人工处理/重复消息）直接返回
            boolean newReviewing = "ai_reviewing".equals(article.getStatus())
                    && "ai_reviewing".equals(article.getReviewStatus());
            boolean editReviewing = "published".equals(article.getStatus())
                    && "ai_reviewing".equals(article.getReviewStatus())
                    && pending != null;
            if (!newReviewing && !editReviewing) {
                log.debug("审核消息跳过（状态已非审核中）, articleId: {}", articleId);
                return;
            }

            // 组装审核输入：编辑场景用待生效区内容
            String title = editReviewing ? pending.getPendingTitle() : article.getTitle();
            String summary = editReviewing ? pending.getPendingSummary() : article.getSummary();
            String content = editReviewing ? pending.getPendingContentMd() : loadContentMd(articleId);

            ModerationVerdict verdict;
            try {
                verdict = moderationService.moderate(articleId, title, summary, content);
            } catch (Exception e) {
                log.warn("AI 审核调用失败, articleId: {}, attempt: {}, 错误: {}", articleId, attempt, e.getMessage());
                onModerateFailure(articleId, attempt);
                return;
            }

            // 竞态闸：moderate 耗时 3~15 秒，窗口内作者可能删稿（软删）、兜底扫描或人工可能已处理。
            // apply 前重读，任一条件不满足 → 清待生效区收尾后直接返回：
            // 不 updateById（不复活已删文章）、不发 ES 索引消息（否则已删文章重新出现在搜索里）。
            Article latest = articleDao.getById(articleId);
            if (latest == null || latest.getDeletedAt() != null) {
                pendingContentMapper.deleteById(articleId);
                return;
            }
            if (editReviewing) {
                ArticlePendingContent latestPending = pendingContentMapper.selectById(articleId);
                if (latestPending == null
                        || !("published".equals(latest.getStatus()) && "ai_reviewing".equals(latest.getReviewStatus()))) {
                    pendingContentMapper.deleteById(articleId);
                    return;
                }
                applyEditVerdict(latest, latestPending, verdict);
            } else {
                if (!("ai_reviewing".equals(latest.getStatus()) && "ai_reviewing".equals(latest.getReviewStatus()))) {
                    pendingContentMapper.deleteById(articleId);
                    return;
                }
                applyNewVerdict(latest, verdict);
            }
        } catch (Exception e) {
            // DB 异常等一律吞掉（不抛 → 不无限 requeue）。
            // 注意不走 onModerateFailure：状态可能已被 apply* 改过，重走转人工会污染结果；
            // 由兜底扫描对"仍卡在审核中"的文章收尾。
            log.error("审核消费处理异常, articleId: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }

    /** 新文章三态流转 */
    private void applyNewVerdict(Article article, ModerationVerdict verdict) {
        if (verdict.isApproved()) {
            article.setStatus("published");
            article.setPublishAt(LocalDateTime.now());
            article.setReviewStatus("approved");
            article.setReviewReason(verdict.reason());
            article.setIsReviewed(1);
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            indexMessageService.sendIndexAfterCommit(article, indexMessageService.loadTagNames(article.getId()), MQOperation.CREATE);
            moderationService.writeLog(article.getId(), "ai_approve", verdict.reason(), "ai");
            return;
        }
        if (verdict.isRejected()) {
            article.setStatus("rejected");
            article.setReviewStatus("rejected");
            article.setReviewReason(verdict.reason());
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_reject", verdict.reason(), "ai");
            return;
        }
        // manual：转人工队列（现有人工审核直接接管）
        article.setStatus("pending_review");
        article.setReviewStatus("manual");
        article.setReviewReason(verdict.reason());
        article.setUpdateAt(LocalDateTime.now());
        articleDao.updateById(article);
        moderationService.writeLog(article.getId(), "ai_manual", verdict.reason(), "ai");
    }

    /** 编辑三态流转（先审后生效语义保留：reject/manual 不碰旧版内容） */
    private void applyEditVerdict(Article article, ArticlePendingContent pending, ModerationVerdict verdict) {
        if (verdict.isApproved()) {
            // 待生效内容替换生效
            article.setTitle(pending.getPendingTitle());
            article.setSummary(pending.getPendingSummary());
            article.setReviewStatus("approved");
            article.setReviewReason(verdict.reason());
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);

            ArticleContent content = contentDao.getById(article.getId());
            if (content == null) {
                content = ArticleContent.builder().articleId(article.getId()).build();
            }
            content.setContentMd(pending.getPendingContentMd());
            content.setContentHtml(pending.getPendingContentHtml());
            content.setWordsCount(pending.getPendingContentMd() != null ? pending.getPendingContentMd().length() : 0);
            content.setUpdatedAt(LocalDateTime.now());
            contentDao.saveOrUpdate(content);

            chapterService.rebuild(article.getId(), pending.getPendingContentMd());
            pendingContentMapper.deleteById(pending.getArticleId());
            indexMessageService.sendIndexAfterCommit(article, indexMessageService.loadTagNames(article.getId()), MQOperation.UPDATE);
            moderationService.writeLog(article.getId(), "ai_approve", verdict.reason(), "ai");
            return;
        }
        if (verdict.isRejected()) {
            // 本次编辑丢弃：清待生效区，旧版继续展示
            pendingContentMapper.deleteById(pending.getArticleId());
            article.setReviewStatus("rejected");
            article.setReviewReason(verdict.reason());
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_reject", verdict.reason(), "ai");
            return;
        }
        // manual：待生效区保留，进人工队列；理由同步到待生效区（人工队列 EDIT 类目读 pending.reviewReason）
        article.setReviewStatus("manual");
        article.setReviewReason(verdict.reason());
        article.setUpdateAt(LocalDateTime.now());
        articleDao.updateById(article);
        pending.setReviewReason(verdict.reason());
        pendingContentMapper.updateById(pending);
        moderationService.writeLog(article.getId(), "ai_manual", verdict.reason(), "ai");
    }

    /**
     * 失败路径：attempt < maxAttempt → 延迟重试；否则转人工（fail-closed）。
     * 转人工以重读状态为准（不依赖调用前的 newReviewing/editReviewing 参数）：
     * 失败期间人工可能已处理，重读后仍非审核中则直接放弃，绝不覆盖人工结论。
     */
    private void onModerateFailure(String articleId, int attempt) {
        if (attempt < properties.getMaxAttempt()) {
            retrySender.sendRetry(articleId, attempt + 1);
            return;
        }
        Article article = articleDao.getById(articleId);
        if (article == null || article.getDeletedAt() != null) {
            pendingContentMapper.deleteById(articleId);
            return;
        }
        if (!"ai_reviewing".equals(article.getReviewStatus())) {
            log.debug("审核失败兜底跳过（状态已被人工变更）, articleId: {}", articleId);
            return;
        }
        if ("ai_reviewing".equals(article.getStatus())) {
            // 新文章：审核中 → 转人工队列
            article.setStatus("pending_review");
        } else {
            // 编辑场景（published + 审核中）：旧版继续展示，仅标记人工；理由同步到待生效区
            ArticlePendingContent pending = pendingContentMapper.selectById(articleId);
            if (pending != null) {
                pending.setReviewReason(MANUAL_FALLBACK_REASON);
                pendingContentMapper.updateById(pending);
            }
        }
        article.setReviewStatus("manual");
        article.setReviewReason(MANUAL_FALLBACK_REASON);
        article.setUpdateAt(LocalDateTime.now());
        articleDao.updateById(article);
        moderationService.writeLog(articleId, "ai_manual", MANUAL_FALLBACK_REASON, "ai");
    }

    /** 读正文（新文章审核用；读不到时给空串，由 AI 对空正文判 manual/approve） */
    private String loadContentMd(String articleId) {
        ArticleContent content = contentDao.getById(articleId);
        return content != null && content.getContentMd() != null ? content.getContentMd() : "";
    }
}
