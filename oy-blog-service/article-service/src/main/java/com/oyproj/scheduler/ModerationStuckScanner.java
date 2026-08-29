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
        // 新文章：status=ai_reviewing 且超时（已删文章不扫描，避免转人工写噪音日志）
        List<Article> stuckNew = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "ai_reviewing")
                .isNull(Article::getDeletedAt)
                .lt(Article::getUpdateAt, deadline));
        for (Article article : stuckNew) {
            // Java 侧二次校验超时（防查询与更新之间时钟偏差/数据异常误伤新文章）
            if (article.getUpdateAt() == null || !article.getUpdateAt().isBefore(deadline)) {
                continue;
            }
            // 更新前状态复核：防毫秒级窗口内消费者刚落 approve/人工已处理 → 不覆盖结论
            Article latest = articleDao.getById(article.getId());
            if (latest == null || latest.getDeletedAt() != null
                    || !"ai_reviewing".equals(latest.getStatus())
                    || !"ai_reviewing".equals(latest.getReviewStatus())) {
                continue;
            }
            latest.setStatus("pending_review");
            latest.setReviewStatus("manual");
            latest.setReviewReason(STUCK_REASON);
            latest.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(latest);
            moderationService.writeLog(latest.getId(), "ai_manual", STUCK_REASON, "system");
            log.warn("审核超时转人工（新文章）, articleId: {}", latest.getId());
        }

        // 编辑待审：review_status=ai_reviewing 且待生效区行超时（已删文章不扫描）
        List<Article> stuckEdits = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .eq(Article::getReviewStatus, "ai_reviewing")
                .isNull(Article::getDeletedAt));
        for (Article article : stuckEdits) {
            ArticlePendingContent pending = pendingContentMapper.selectById(article.getId());
            if (pending == null || pending.getUpdatedAt() == null || pending.getUpdatedAt().isAfter(deadline)) {
                continue;
            }
            // 更新前状态复核：防覆盖消费者刚落的 approve
            Article latest = articleDao.getById(article.getId());
            if (latest == null || latest.getDeletedAt() != null
                    || !"published".equals(latest.getStatus())
                    || !"ai_reviewing".equals(latest.getReviewStatus())) {
                continue;
            }
            latest.setReviewStatus("manual");
            latest.setReviewReason(STUCK_REASON);
            latest.setUpdateAt(LocalDateTime.now());
            articleDao.updateById(latest);
            moderationService.writeLog(latest.getId(), "ai_manual", STUCK_REASON, "system");
            log.warn("审核超时转人工（编辑待审）, articleId: {}", latest.getId());
        }
    }
}
