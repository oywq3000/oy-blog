package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.common.util.MarkdownSanitizer;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleStatsDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章索引内部接口
 * 供 search-service 全量对账重建索引使用（不经过网关鉴权）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ArticleIndexController {

    private final ArticleDao articleDao;
    private final ArticleContentDao contentDao;
    private final ArticleStatsDao statsDao;

    /**
     * 分页返回已发布文章的索引快照数据
     */
    @GetMapping("/internal/index/snapshot")
    public Result<PageVo<List<ArticleIndexMessage>>> getIndexSnapshot(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        // 分页查询已发布、未删除的文章
        List<Article> articles = articleDao.listPublished(page, size);
        long total = articleDao.countPublished();

        if (articles.isEmpty()) {
            PageVo<List<ArticleIndexMessage>> pageVo = new PageVo<>(page, size, total,
                    (int) Math.ceil((double) total / size), new ArrayList<>());
            return Result.ok(pageVo);
        }

        // 批量加载相关数据
        List<String> articleIds = articles.stream().map(Article::getId).toList();

        Map<String, ArticleContent> contentMap = contentDao.listByIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleContent::getArticleId, c -> c, (a, b) -> a));
        Map<String, ArticleStats> statsMap = statsDao.listByArticleIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleStats::getArticleId, s -> s, (a, b) -> a));

        // 转换为索引消息
        List<ArticleIndexMessage> messages = articles.stream().map(article -> {
            ArticleIndexMessage msg = new ArticleIndexMessage();
            msg.setOperation(MQOperation.CREATE);
            msg.setArticleId(article.getId());
            msg.setTitle(article.getTitle());
            msg.setSummary(article.getSummary());
            msg.setAuthorId(article.getAuthorId());
            msg.setAuthor(article.getAuthorId()); // 对账场景用 authorId
            msg.setCreatedAt(article.getCreatedAt());
            msg.setUpdatedAt(article.getUpdateAt());
            msg.setStatus(article.getStatus());

            ArticleContent content = contentMap.get(article.getId());
            if (content != null) {
                msg.setContentMd(MarkdownSanitizer.sanitize(content.getContentMd()));
            }

            ArticleStats stats = statsMap.get(article.getId());
            if (stats != null) {
                msg.setViewCount(stats.getViews());
                msg.setLikeCount(stats.getLikes());
                msg.setCommentCount(stats.getComments());
            }

            return msg;
        }).collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) total / size);
        PageVo<List<ArticleIndexMessage>> pageVo = new PageVo<>(page, size, total, totalPages, messages);
        return Result.ok(pageVo);
    }
}
