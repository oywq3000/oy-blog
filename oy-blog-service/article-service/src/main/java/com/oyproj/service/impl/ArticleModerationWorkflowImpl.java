package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ArticleModerationWorkflow;
import com.oyproj.service.ModerationRetrySender;
import com.oyproj.service.ModerationService;
import com.oyproj.service.ModerationVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 审核消息处理流程（自 ArticleModerationConsumer 迁出并改造为两段式）。
 *
 * 两段式动机：AI 审核是 3~15 秒的外部 HTTP 调用（超时上限 30s），原来的写法把它搂在一个长事务里，
 * 期间一直占着一条 DB 连接 + 文章行的 FOR UPDATE 行锁——并发度一提就自伤。改造后：
 *   TX1（毫秒级）读闸 + 取审核输入 → 事务外调 AI → TX2（毫秒级）重读 + 落结论。
 *
 * 幂等铁律不变：一切动作前先查 DB 状态闸；重复消息/过期任务直接返回。
 * 判定权威统一到 TX2 的重读状态：审核输入取"提交那一刻"的内容，但走哪条 apply 分支只看重读结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleModerationWorkflowImpl implements ArticleModerationWorkflow {

    private static final String MANUAL_FALLBACK_REASON = "审核服务不可用，转人工审核";

    private final ArticleDao articleDao;
    private final ArticleContentDao contentDao;
    private final ArticlePendingContentMapper pendingContentMapper;
    private final ModerationService moderationService;
    private final ArticleIndexMessageService indexMessageService;
    private final ArticleChapterService chapterService;
    private final ModerationRetrySender retrySender;
    private final ModerationProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void process(String articleId, int attempt) {
        ModerationTask task = transactionTemplate.execute(status -> openTask(articleId));
        if (task == null) {
            return; // 早退：文章已删或状态已非审核中（openTask 内已按需收尾）
        }
        log.debug("审核任务开始, articleId: {}, editing: {}, attempt: {}", articleId, task.editing(), attempt);

        // AI 调用在事务外：不占 DB 连接、不持行锁
        ModerationVerdict verdict;
        try {
            verdict = moderationService.moderate(articleId, task.title(), task.summary(), task.content());
        } catch (Exception e) {
            log.warn("AI 审核调用失败, articleId: {}, attempt: {}, 错误: {}", articleId, attempt, e.getMessage());
            transactionTemplate.executeWithoutResult(status -> handleFailure(articleId, attempt));
            return;
        }

        transactionTemplate.executeWithoutResult(status -> apply(articleId, verdict));
    }

    /**
     * TX1：幂等闸 + 取审核输入。返回 null 表示早退（不调 AI、不进 TX2）。
     * 审核输入取"提交审核那一刻"的内容：编辑场景用待生效区，新文章用正文表。
     */
    private ModerationTask openTask(String articleId) {
        Article article = articleDao.getById(articleId);
        if (article == null || article.getDeletedAt() != null) {
            // 作者撤稿：清理待生效区收尾
            pendingContentMapper.deleteById(articleId);
            return null;
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
            return null;
        }
        if (editReviewing) {
            return new ModerationTask(pending.getPendingTitle(), pending.getPendingSummary(),
                    pending.getPendingContentMd(), true);
        }
        return new ModerationTask(article.getTitle(), article.getSummary(), loadContentMd(articleId), false);
    }

    /**
     * TX2：FOR UPDATE 当前读 + 竞态闸 + 落结论。分支只认重读状态，不认 TX1 的判定。
     * 重读必须用 FOR UPDATE：本方法在事务内，MySQL 默认 REPEATABLE READ 下普通重读走首读快照，
     * 窗口内已提交的软删/人工处置不可见 → 闸放行 → updateById 复活软删行 + ES 索引消息照发。
     */
    private void apply(String articleId, ModerationVerdict verdict) {
        Article latest = articleDao.getOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .last("FOR UPDATE"));
        if (latest == null || latest.getDeletedAt() != null) {
            pendingContentMapper.deleteById(articleId);
            return;
        }
        ArticlePendingContent latestPending = pendingContentMapper.selectById(articleId);

        if ("published".equals(latest.getStatus()) && "ai_reviewing".equals(latest.getReviewStatus())) {
            if (latestPending == null) {
                // 待生效区已空：没有可生效的内容，不写状态
                log.debug("审核结论跳过（待生效区已空）, articleId: {}", articleId);
                return;
            }
            applyEditVerdict(latest, latestPending, verdict);
            return;
        }
        if ("ai_reviewing".equals(latest.getStatus()) && "ai_reviewing".equals(latest.getReviewStatus())) {
            if (latestPending != null) {
                // 理论不可达：审核中的新文章不允许编辑（发布/编辑守卫拦截）；真出现则记录并继续按新文章处理
                log.warn("新文章审核中却存在待生效区，按新文章分支处理, articleId: {}", articleId);
            }
            applyNewVerdict(latest, verdict);
            return;
        }
        // 其余：人工/兜底已处置、或状态已变 —— 收尾但不写状态
        if (latestPending != null) {
            pendingContentMapper.deleteById(articleId);
        }
        log.debug("审核结论跳过（状态已非审核中）, articleId: {}", articleId);
    }

    /** 新文章三态流转 */
    private void applyNewVerdict(Article article, ModerationVerdict verdict) {
        if (verdict.isApproved()) {
            article.setStatus("published");
            article.setPublishAt(LocalDateTime.now());
            article.setReviewStatus("approved");
            article.setReviewReason(verdict.reason());
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
     * TX2b 失败路径：attempt < maxAttempt → 延迟重试；否则转人工（fail-closed）。
     * 转人工以重读状态为准：失败期间人工可能已处理，重读后仍非审核中则直接放弃，绝不覆盖人工结论。
     */
    private void handleFailure(String articleId, int attempt) {
        if (attempt < properties.getMaxAttempt()) {
            retrySender.sendRetry(articleId, attempt + 1);
            return;
        }
        // 重读同样用 FOR UPDATE 当前读：同事务内 RR 快照会掩盖窗口内软删/状态变更
        Article article = articleDao.getOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getId, articleId)
                .last("FOR UPDATE"));
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

    /** TX1 的产物：审核输入（内容取自提交审核那一刻；editing 仅用于日志排查） */
    private record ModerationTask(String title, String summary, String content, boolean editing) {
    }
}
