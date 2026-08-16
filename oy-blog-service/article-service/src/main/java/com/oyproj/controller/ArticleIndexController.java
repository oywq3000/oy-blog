package com.oyproj.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.common.util.MarkdownSanitizer;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleCategory;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.entity.ArticleTag;
import com.oyproj.domain.entity.Category;
import com.oyproj.domain.entity.Tag;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.mapper.ArticleCategoryMapper;
import com.oyproj.mapper.ArticleTagMapper;
import com.oyproj.mapper.CategoryMapper;
import com.oyproj.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final UserClient userClient;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final ArticleCategoryMapper articleCategoryMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 分页返回已发布文章的索引快照数据
     */
    @GetMapping("/internal/index/snapshot")
    public Result<PageVo<List<ArticleIndexMessage>>> getIndexSnapshot(
            @RequestParam(defaultValue = "0") int pageNum,
            @RequestParam(defaultValue = "100") int pageSize) {

        // 分页查询已发布、未删除的文章
        List<Article> articles = articleDao.listPublished(pageNum, pageSize);
        long total = articleDao.countPublished();

        if (articles.isEmpty()) {
            PageVo<List<ArticleIndexMessage>> pageVo = new PageVo<>(pageNum, pageSize, total,
                    (int) Math.ceil((double) total / pageSize), new ArrayList<>());
            return Result.ok(pageVo);
        }

        // 批量加载相关数据
        List<String> articleIds = articles.stream().map(Article::getId).toList();

        Map<String, ArticleContent> contentMap = contentDao.listByIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleContent::getArticleId, c -> c, (a, b) -> a));
        Map<String, ArticleStats> statsMap = statsDao.listByArticleIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleStats::getArticleId, s -> s, (a, b) -> a));

        // 批量加载作者信息
        Map<String, String> authorNameMap = new HashMap<>();
        Map<String, String> authorAvatarMap = new HashMap<>();
        List<String> authorIds = articles.stream().map(Article::getAuthorId).distinct().toList();
        try {
            Result<List<UserDTO>> userResult = userClient.getUserDTOs(authorIds);
            if (userResult != null && userResult.getData() != null) {
                userResult.getData().forEach(u -> {
                    authorNameMap.put(u.getId(), u.getUsername());
                    authorAvatarMap.put(u.getId(), u.getAvatarUrl());
                });
            }
        } catch (Exception e) {
            log.warn("批量获取作者信息失败", e);
        }

        // 批量加载标签
        Map<String, List<String>> tagMap = new HashMap<>();
        List<ArticleTag> allArticleTags = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIds));
        if (!allArticleTags.isEmpty()) {
            List<String> tagIds = allArticleTags.stream().map(ArticleTag::getTagId).distinct().toList();
            Map<String, String> tagIdToName = new HashMap<>();
            tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds))
                    .forEach(t -> tagIdToName.put(t.getId(), t.getName()));
            for (ArticleTag at : allArticleTags) {
                String name = tagIdToName.get(at.getTagId());
                if (name != null) {
                    tagMap.computeIfAbsent(at.getArticleId(), k -> new ArrayList<>()).add(name);
                }
            }
        }

        // 批量加载分类
        Map<String, String> categoryMap = new HashMap<>();
        List<ArticleCategory> allArticleCategories = articleCategoryMapper.selectList(
                new LambdaQueryWrapper<ArticleCategory>().in(ArticleCategory::getArticleId, articleIds));
        if (!allArticleCategories.isEmpty()) {
            List<String> catIds = allArticleCategories.stream().map(ArticleCategory::getCategoryId).distinct().toList();
            Map<String, String> catIdToCode = new HashMap<>();
            categoryMapper.selectList(new LambdaQueryWrapper<Category>().in(Category::getId, catIds))
                    .forEach(c -> catIdToCode.put(c.getId(), c.getCode()));
            for (ArticleCategory ac : allArticleCategories) {
                String code = catIdToCode.get(ac.getCategoryId());
                if (code != null) {
                    categoryMap.put(ac.getArticleId(), code); // 一篇文章一个分类
                }
            }
        }

        // 转换为索引消息
        List<ArticleIndexMessage> messages = articles.stream().map(article -> {
            ArticleIndexMessage msg = new ArticleIndexMessage();
            msg.setOperation(MQOperation.CREATE);
            msg.setArticleId(article.getId());
            msg.setSlug(article.getSlug());
            msg.setTitle(article.getTitle());
            msg.setSummary(article.getSummary());
            msg.setAuthorId(article.getAuthorId());
            msg.setAuthorName(authorNameMap.getOrDefault(article.getAuthorId(), article.getAuthorId()));
            msg.setAuthorAvatar(authorAvatarMap.get(article.getAuthorId()));
            msg.setTags(tagMap.getOrDefault(article.getId(), new ArrayList<>()));
            msg.setCategory(categoryMap.get(article.getId()));
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

        int totalPages = (int) Math.ceil((double) total / pageSize);
        PageVo<List<ArticleIndexMessage>> pageVo = new PageVo<>(pageNum, pageSize, total, totalPages, messages);
        return Result.ok(pageVo);
    }
}
