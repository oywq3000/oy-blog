package com.oyproj.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核兜底扫描：防 MQ 消息丢失/消费端挂死导致文章卡死在"AI 审核中"。
 * 每 scanIntervalMs 扫描一次，ai_reviewing 超 stuckTimeoutMinutes 无结果 → 转人工（fail-closed）。
 * 幂等：转人工后状态已非审核中，消费者幂等闸会跳过迟到消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationStuckScanner {

    private static final String STUCK_REASON = "审核超时，转人工审核";

    private final ArticleDao articleDao;
    private final ArticlePendingContentMapper pendingContentMapper;
    private final ModerationService moderationService;
    private final ModerationProperties properties;

    @Scheduled(fixedDelayString = "${oy-blog.article.moderation.scan-interval-ms:300000}",
               initialDelayString = "${oy-blog.article.moderation.scan-interval-ms:300000}")
    public void scanStuck() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(properties.getStuckTimeoutMinutes());
        // 新文章：status=ai_reviewing 且超时
        List<Article> stuckNew = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "ai_reviewing")
                .lt(Article::getUpdateAt, deadline));
        for (Article article : stuckNew) {
            // Java 侧二次校验超时（防查询与更新之间时钟偏差/数据异常误伤新文章）
            if (article.getUpdateAt() == null || !article.getUpdateAt().isBefore(deadline)) {
                continue;
            }
            article.setStatus("pending_review");
            article.setReviewStatus("manual");
            article.setReviewReason(STUCK_REASON);
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_manual", STUCK_REASON, "system");
            log.warn("审核超时转人工（新文章）, articleId: {}", article.getId());
        }

        // 编辑待审：review_status=ai_reviewing 且待生效区行超时
        List<Article> stuckEdits = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .eq(Article::getReviewStatus, "ai_reviewing"));
        for (Article article : stuckEdits) {
            ArticlePendingContent pending = pendingContentMapper.selectById(article.getId());
            if (pending == null || pending.getUpdatedAt() == null || pending.getUpdatedAt().isAfter(deadline)) {
                continue;
            }
            article.setReviewStatus("manual");
            article.setReviewReason(STUCK_REASON);
            article.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(article);
            moderationService.writeLog(article.getId(), "ai_manual", STUCK_REASON, "system");
            log.warn("审核超时转人工（编辑待审）, articleId: {}", article.getId());
        }
    }
}
