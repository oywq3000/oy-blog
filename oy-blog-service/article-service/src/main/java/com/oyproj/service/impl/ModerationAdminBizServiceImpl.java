package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticlePendingContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.mapper.ArticlePendingContentMapper;
import com.oyproj.service.ArticleChapterService;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ModerationAdminBizService;
import com.oyproj.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文章人工审核后台业务实现（照抄评论审核的 audit → ModerationLog 模式）
 */
@Service
@RequiredArgsConstructor
public class ModerationAdminBizServiceImpl extends ArticleBaseBizService implements ModerationAdminBizService {

    private final ArticleDao articleDao;
    private final ArticleContentDao contentDao;
    private final ArticleTagDao articleTagDao;
    private final ArticlePendingContentMapper pendingContentMapper;
    private final ModerationService moderationService; // 复用 writeLog
    private final ArticleIndexMessageService indexMessageService;
    private final ArticleChapterService chapterService; // 待审编辑生效后重建章节目录

    @Override
    public Result<PageVo<List<ArticleModerationItemVo>>> adminPage(ArticleModerationPageDto dto) {
        int page = dto.getPage() == null ? 1 : dto.getPage();
        int size = dto.getSize() == null ? 10 : dto.getSize();
        List<ArticleModerationItemVo> items = new ArrayList<>();

        // 类型一：待审新文章（status=pending_review）
        List<Article> newArticles = articleDao.list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "pending_review")
                .isNull(Article::getDeletedAt)
                .orderByDesc(Article::getCreatedAt));
        for (Article a : newArticles) {
            ArticleModerationItemVo vo = new ArticleModerationItemVo();
            vo.setArticleId(a.getId());
            vo.setKind("NEW");
            vo.setTitle(a.getTitle());
            vo.setAuthorId(a.getAuthorId());
            vo.setSummary(a.getSummary());
            vo.setReviewReason(a.getReviewReason());
            vo.setCreatedAt(a.getCreatedAt());
            items.add(vo);
        }

        // 类型二：已发布文章的待审编辑
        List<ArticlePendingContent> pendings = pendingContentMapper.selectList(null);
        for (ArticlePendingContent p : pendings) {
            Article a = articleDao.getById(p.getArticleId());
            if (a == null || a.getDeletedAt() != null) {
                continue;
            }
            ArticleModerationItemVo vo = new ArticleModerationItemVo();
            vo.setArticleId(p.getArticleId());
            vo.setKind("EDIT");
            vo.setTitle(a.getTitle());     // 当前对外展示的旧标题
            vo.setAuthorId(a.getAuthorId());
            vo.setSummary(a.getSummary()); // 当前对外摘要
            vo.setReviewReason(p.getReviewReason());
            vo.setCreatedAt(p.getUpdatedAt());
            vo.setPendingTitle(p.getPendingTitle());
            vo.setPendingSummary(p.getPendingSummary());
            items.add(vo);
        }

        items.sort((x, y) -> y.getCreatedAt().compareTo(x.getCreatedAt()));
        int total = items.size();
        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        List<ArticleModerationItemVo> pageItems =
                from >= total ? Collections.emptyList() : items.subList(from, to);
        int pages = total == 0 ? 0 : (total + size - 1) / size;
        // total 为 int，PageVo 第 3 参是 Long（构造器是 (Integer, Integer, Long, Integer, T)），需显式加宽
        return Result.ok(new PageVo<>(page, size, (long) total, pages, pageItems));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> audit(ArticleModerationAuditDto dto) {
        String operatorId = getUserId();
        String reason = dto.getReason();
        Article article = articleDao.getById(dto.getArticleId());
        ArticlePendingContent pending = pendingContentMapper.selectById(dto.getArticleId());

        // 类型一：待审新文章
        if (article != null && "pending_review".equals(article.getStatus())) {
            if (Boolean.TRUE.equals(dto.getApprove())) {
                article.setStatus("published");
                article.setPublishAt(LocalDateTime.now());
                article.setReviewStatus("approved");
                article.setReviewReason(reason);
                article.setIsReviewed(1);
                article.setUpdateAt(LocalDateTime.now());
                articleDao.updateById(article);
                indexMessageService.sendIndexAfterCommit(article, listTagNames(article.getId()), MQOperation.CREATE);
                moderationService.writeLog(article.getId(), "manual_approve", reason, operatorId);
            } else {
                article.setStatus("rejected");
                article.setReviewStatus("rejected");
                article.setReviewReason(reason);
                article.setUpdateAt(LocalDateTime.now());
                articleDao.updateById(article);
                moderationService.writeLog(article.getId(), "manual_reject", reason, operatorId);
            }
            return Result.ok(true);
        }

        // 类型二：已发布文章的待审编辑
        if (pending != null) {
            // 防御：待审记录存在但文章已不存在（如被硬删），无法执行替换，按"审核项不存在"返回
            if (article == null) {
                return Result.error("审核项不存在");
            }
            if (Boolean.TRUE.equals(dto.getApprove())) {
                // 待审内容替换生效
                article.setTitle(pending.getPendingTitle());
                article.setSummary(pending.getPendingSummary());
                article.setReviewStatus("approved");
                article.setReviewReason(reason);
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

                chapterService.rebuild(article.getId(), pending.getPendingContentMd()); // 章节目录随新内容重建
                pendingContentMapper.deleteById(pending.getArticleId());
                indexMessageService.sendIndexAfterCommit(article, listTagNames(article.getId()), MQOperation.UPDATE);
                moderationService.writeLog(article.getId(), "manual_approve", reason, operatorId);
            } else {
                // 驳回本次编辑：文章保持旧版不动
                pendingContentMapper.deleteById(pending.getArticleId());
                article.setReviewStatus("rejected");
                article.setReviewReason(reason);
                article.setUpdateAt(LocalDateTime.now());
                articleDao.updateById(article);
                moderationService.writeLog(article.getId(), "manual_reject", reason, operatorId);
            }
            return Result.ok(true);
        }

        return Result.error("审核项不存在");
    }

    /** 文章标签名列表（索引消息用） */
    private List<String> listTagNames(String articleId) {
        return articleTagDao.listTagNamesByArticleIds(Collections.singletonList(articleId))
                .getOrDefault(articleId, Collections.emptyList());
    }
}
