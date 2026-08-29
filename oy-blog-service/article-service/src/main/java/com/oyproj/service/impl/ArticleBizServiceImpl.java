package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.oyproj.api.article.domain.UserArticleStatDto;
import com.oyproj.api.user.client.UserClient;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.util.MarkdownRenderer;
import com.oyproj.common.utils.UUIDUtils;
import com.oyproj.dao.UserArticleStatDao;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.entity.*;
import com.oyproj.dto.*;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.mapper.ArticleTagMapper;
import com.oyproj.service.ArticleBizService;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ArticleMessageProducer;
import com.oyproj.service.ModerationService;
import com.oyproj.service.ModerationVerdict;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 文章业务服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleBizServiceImpl extends ArticleBaseBizService implements ArticleBizService {

    @NotNull private final ArticleDao articleDao;
    @NotNull private final ArticleRevisionDao revisionDao;
    @NotNull private final ArticleContentDao contentDao;
    @NotNull private final ArticleStatsDao statsDao;
    @NotNull private final TagDao tagDao;
    @NotNull private final ArticleTagMapper articleTagMapper;
    @NotNull private final ArticleMessageProducer articleMessageProducer;
    @NotNull private final ArticleIndexMessageService indexMessageService;
    @NotNull private final UserClient userClient;
    @NotNull private final UserArticleStatDao userArticleStatDao;
    @NotNull private final ModerationService moderationService;
    @NotNull private final ArticlePendingContentMapper pendingContentMapper;
    @NotNull private final ArticleChapterService chapterService;

    /**
     * 保存草稿
     *
     * @param dto 文章信息
     * @return 文章ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> saveDraft(ArticleSaveDto dto) {
        Article article = saveArticleBase(dto, "draft");
        String articleId =article.getId();
        saveRevision(articleId, dto.getContentMd());
        return Result.ok(articleId, I18n(ResultCode.SUCCESS));
    }

    /**
     * 发布文章
     *
     * @param dto 文章信息
     * @return 文章ID
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, String>> publish(ArticleSaveDto dto) {
        // contentHtml 为空时，自动从 contentMd 渲染
        if (!StringUtils.hasText(dto.getContentHtml())) {
            dto.setContentHtml(MarkdownRenderer.toHtml(dto.getContentMd()));
        }
        boolean isNew = !StringUtils.hasText(dto.getId());
        Article existing = isNew ? null : articleDao.getById(dto.getId());
        if (!isNew && existing == null) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        boolean editingPublished = existing != null && "published".equals(existing.getStatus());
        // 驳回重发：驳回路径从不发索引消息（未入索引），审核通过后须按新文档 CREATE 建索引
        boolean isRejectedResubmit = existing != null && "rejected".equals(existing.getStatus());
        // 发布态建索引动作：新文章/驳回重发按 CREATE，已发布编辑按 UPDATE
        MQOperation publishOp = (isNew || isRejectedResubmit) ? MQOperation.CREATE : MQOperation.UPDATE;

        // 审核门：开关关闭或豁免用户 → 直接放行
        if (!moderationService.isEnabled() || moderationService.isExempt()) {
            return persistWithReview(dto, "published", "exempt", "审核豁免", publishOp, null);
        }

        // AI 审核（先审后写：已发布文章的编辑在此阶段尚未覆盖内容）
        ModerationVerdict verdict = moderationService.moderate(
                isNew ? "" : dto.getId(), dto.getTitle(), dto.getSummary(), dto.getContentMd());

        if (verdict.isApproved()) {
            return persistWithReview(dto, "published", "approved", verdict.reason(), publishOp, "ai_approve");
        }

        if (verdict.isRejected()) {
            if (editingPublished) {
                // 先审后生效：本次编辑全部丢弃，旧版继续对外展示
                existing.setReviewStatus("rejected");
                existing.setReviewReason(verdict.reason());
                articleDao.updateById(existing);
                moderationService.writeLog(existing.getId(), "ai_reject", verdict.reason(), "ai");
                return publishResult(existing.getId(), "rejected", verdict.reason());
            }
            // 新文章/重发：内容照常保存为已驳回状态，作者可改后重新发布
            return persistWithReview(dto, "rejected", "rejected", verdict.reason(), null, "ai_reject");
        }

        // manual：转人工
        if (editingPublished) {
            // 新版本进待生效区，旧版继续 published；封面/允许评论/标签属未审字段，本次正常生效
            existing.setCoverUrl(dto.getCoverUrl());
            existing.setAllowComment(dto.getAllowComment() != null ? dto.getAllowComment() : 1);
            existing.setUpdateAt(LocalDateTime.now());
            existing.setReviewStatus("manual");
            existing.setReviewReason(verdict.reason());
            articleDao.updateById(existing);
            saveRelations(existing.getId(), dto);
            savePendingContent(existing.getId(), dto, verdict.reason());
            moderationService.writeLog(existing.getId(), "ai_manual", verdict.reason(), "ai");
            return publishResult(existing.getId(), "manual", verdict.reason());
        }
        // 新文章/重发：先落库为待人工审核，管理员审核通过后再发布建索引
        return persistWithReview(dto, "pending_review", "manual", verdict.reason(), null, "ai_manual");
    }

    /**
     * 落库文章并写审核结果（publish 四路分支共用：豁免/通过/驳回/转人工）。
     * 统一完成：基础信息 → 修订 → 内容 → 章节 → 标签 → 审核字段 → 审核日志。
     *
     * @param status       落库状态：published / rejected / pending_review
     * @param reviewStatus 审核状态：exempt / approved / rejected / manual
     * @param reason       审核原因（豁免为固定文案，其余取 AI verdict）
     * @param operation    发布态传 CREATE/UPDATE（同时标记已审并建索引）；非发布态传 null（仅落库不建索引）
     * @param logType      审核日志类型（ai_approve/ai_reject/ai_manual）；null 不写日志（豁免路径）
     */
    private Result<Map<String, String>> persistWithReview(ArticleSaveDto dto, String status, String reviewStatus,
                                                          String reason, MQOperation operation, String logType) {
        Article article = saveArticleBase(dto, status);
        String articleId = article.getId();
        saveRevision(articleId, dto.getContentMd());
        saveContent(articleId, dto.getContentMd(), dto.getContentHtml());
        chapterService.rebuild(articleId, dto.getContentMd());
        saveRelations(articleId, dto);
        article.setReviewStatus(reviewStatus);
        article.setReviewReason(reason);
        if (operation != null) {
            article.setIsReviewed(1);
        }
        articleDao.updateById(article);
        if (operation != null) {
            // 驳回路径从不发索引消息（未入索引），发布态须按新文档 CREATE 建索引
            indexMessageService.sendIndexAfterCommit(article, dto.getTags(), operation);
        }
        if (logType != null) {
            moderationService.writeLog(articleId, logType, reason, "ai");
        }
        return publishResult(articleId, reviewStatus, reason);
    }

    /** 组装 publish 返回：恒含 articleId/verdict/reason，前端据此提示"已驳回+原因/审核中" */
    private Result<Map<String, String>> publishResult(String articleId, String verdict, String reason) {
        Map<String, String> result = new HashMap<>();
        result.put("articleId", articleId);
        result.put("verdict", verdict);
        result.put("reason", reason == null ? "" : reason);
        return Result.ok(result);
    }

    /** 待生效编辑写入（一篇最多一份：已存在则覆盖） */
    private void savePendingContent(String articleId, ArticleSaveDto dto, String reason) {
        LocalDateTime now = LocalDateTime.now();
        ArticlePendingContent pending = pendingContentMapper.selectById(articleId);
        if (pending == null) {
            pending = ArticlePendingContent.builder()
                    .articleId(articleId)
                    .createdAt(now)
                    .build();
        }
        pending.setPendingTitle(dto.getTitle());
        pending.setPendingSummary(dto.getSummary());
        pending.setPendingContentMd(dto.getContentMd());
        pending.setPendingContentHtml(dto.getContentHtml());
        pending.setReviewReason(reason);
        pending.setUpdatedAt(now);
        if (pendingContentMapper.selectById(articleId) == null) {
            pendingContentMapper.insert(pending);
        } else {
            pendingContentMapper.updateById(pending);
        }
    }



    private String getAuthorName(String authorId) {
        return null;
    }


    /**
     * 删除文章
     *
     * @param id 文章ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> delete(String id) {
        // 软删除
        Article article = articleDao.getById(id);
        if (article != null) {
            article.setDeletedAt(LocalDateTime.now());
            articleDao.updateById(article);
            // 事务提交后同步删除ES索引
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            articleMessageProducer.sendArticleDeleteMessage(id);
                        }
                    }
            );
        }
        return Result.ok(true);
    }

    /**
     * 判断是否为当前用户的文章
     *
     * @param articleId 文章ID
     * @return 是否为当前用户的文章
     */
    @Override
    public Result<Boolean> checkOwnership(String articleId) {
        Article article = articleDao.getById(articleId);
        if (article == null) {
            return Result.ok(false);
        }
        return Result.ok(article.getAuthorId().equals(getUserId()));
    }

    /**
     * 获取当前用户文章统计
     *
     * @return 统计信息
     */
    @Override
    public Result<UserArticleStatDto> getMyStats() {
        return getUserStats(getUserId());
    }

    /**
     * 获取指定用户文章统计
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    @Override
    public Result<UserArticleStatDto> getUserStats(String userId) {
        UserArticleStatDto articleStat = userArticleStatDao.getArticleStatById(userId);
        return Result.ok(articleStat);
    }

    /**
     * 保存文章基础信息（草稿或发布）
     *
     * @param dto 文章信息
     * @param status 文章状态（draft或published）
     * @return 文章ID
     */
    private Article saveArticleBase(ArticleSaveDto dto, String status) {
        Article article;
        boolean isNew = false;
        if (StringUtils.hasText(dto.getId())) {
            article = articleDao.getById(dto.getId());
            if (article == null) {
                throw new NotFoundException(I18nUtils.t("article.not_found"));
            }
        } else {
            article = new Article();
            article.setId(getId());
            isNew = true;
        }

        article.setTitle(dto.getTitle());
        article.setSummary(dto.getSummary());
        article.setStatus(status);
        article.setCoverUrl(dto.getCoverUrl());
        article.setAllowComment(dto.getAllowComment() != null ? dto.getAllowComment() : 1);
        article.setUpdateAt(LocalDateTime.now());
        
        if (isNew) {
            article.setAuthorId(getUserId());
            article.setCreatedAt(LocalDateTime.now());
            article.setIsReviewed(0); // 默认未审核
            // 生成Slug (简单处理，实际应用可能需要更复杂的逻辑)
            if (!StringUtils.hasText(article.getSlug())) {
                article.setSlug(getId());
            }
            articleDao.save(article);
            
            // 初始化统计
            ArticleStats stats = ArticleStats.builder()
                    .articleId(article.getId())
                    .views(0L).likes(0L).comments(0L).favorites(0L)
                    .build();
            statsDao.save(stats);
        } else {
            articleDao.updateById(article);
        }
        
        if ("published".equals(status)) {
            article.setPublishAt(LocalDateTime.now());
            articleDao.updateById(article);
        }
        
        return article;
    }

    /**
     * 保存文章修订版本
     *
     * @param articleId 文章ID
     * @param contentMd 文章内容（Markdown格式）
     */
    private void saveRevision(String articleId, String contentMd) {
        ArticleRevision revision = ArticleRevision.builder()
                .id(getId())
                .articleId(articleId)
                .contentSnapshot(contentMd)
                .savedAt(LocalDateTime.now())
                .savedBy(getUserId())
                .build();
        revisionDao.save(revision);
    }

    /**
     * 保存文章内容
     *
     * @param articleId 文章ID
     * @param contentMd 文章内容（Markdown格式）
     * @param contentHtml 文章内容（HTML格式）
     */
    private void saveContent(String articleId, String contentMd, String contentHtml) {
        ArticleContent contentEntity = contentDao.getById(articleId);
        if (contentEntity == null) {
            contentEntity = ArticleContent.builder()
                    .articleId(articleId)
                    .build();
        }
        contentEntity.setContentMd(contentMd);
        contentEntity.setContentHtml(contentHtml);
        contentEntity.setWordsCount(contentMd != null ? contentMd.length() : 0);
        contentEntity.setUpdatedAt(LocalDateTime.now());
        
        contentDao.saveOrUpdate(contentEntity);
    }

    /**
     * 保存文章关联关系（标签）
     *
     * @param articleId 文章ID
     * @param dto 文章保存DTO，包含标签列表
     */
    private void saveRelations(String articleId, ArticleSaveDto dto) {
        // Tags（常用标签与自创标签走同一路径：不存在则自动创建）
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            for (String tagName : dto.getTags()) {
                 Tag tag = tagDao.getOrCreateByName(tagName);
                 if (tag != null) {
                     ArticleTag at = ArticleTag.builder()
                             .id(UUIDUtils.getId())
                             .articleId(articleId)
                             .tagId(tag.getId())
                             .createdAt(LocalDateTime.now())
                             .build();
                     articleTagMapper.insert(at);
                 }
            }
        }
    }

}

