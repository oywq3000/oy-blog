package com.oyproj.service.impl;

import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.entity.Tag;
import com.oyproj.domain.vo.ArticleChapterVo;
import com.oyproj.domain.vo.ArticleContentVo;
import com.oyproj.domain.vo.ArticleVo;
import com.oyproj.domain.vo.TagStatVo;
import com.oyproj.dto.*;
import com.oyproj.service.ArticleReadBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章阅读查询业务服务实现类
 */
@Service
@RequiredArgsConstructor
public class ArticleReadBizServiceImpl extends ArticleBaseBizService implements ArticleReadBizService {

    @NotNull private final ArticleDao articleDao;
    @NotNull private final ArticleContentDao contentDao;
    @NotNull private final ArticleChapterDao chapterDao;
    @NotNull private final ArticleLogDao viewDao;
    @NotNull private final TagDao tagDao;
    @NotNull private final ArticleStatsDao articleStatsDao;

    /**
     * 根据slug查询文章
     *
     * @param slug SEO别名
     * @return 文章
     */
    @Override
    public Result<ArticleVo> getBySlug(String slug) {
        return Result.ok(copyProperties(articleDao.getBySlug(slug), ArticleVo.class));
    }
    
    /**
     * 查询文章内容
     *
     * @param articleId 文章ID
     * @return 文章内容
     */
    @Override
    public Result<ArticleContentVo> getContent(String articleId) {
        return Result.ok(copyProperties(contentDao.getById(articleId), ArticleContentVo.class));
    }

    /**
     * 查询文章章节目录
     *
     * @param articleId 文章ID
     * @return 章节列表
     */
    @Override
    public Result<List<ArticleChapterVo>> listChapters(String articleId) {
        return Result.ok(copyList(chapterDao.listByArticle(articleId), ArticleChapterVo.class));
    }

    /**
     * 查询已发布文章列表（分页）
     *
     * @return 文章列表
     */
    @Override
    @Transactional
    public Result<List<ArticleVo>> listPublished() {
        List<ArticleVo> voList = getPage(articleDao::listPublished, ArticleVo.class);
        enrichWithStats(voList);
        return Result.ok(voList);
    }

    /**
     * 查询用户浏览历史
     *
     * @return 文章列表
     */
    @Override
    public Result<List<ArticleVo>> listHistory() {
        List<String> articleIds = getPage(() -> viewDao.listHistoryArticleIds(getUserId()), String.class);
        if (articleIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Article> articles = articleDao.listByIds(articleIds);
        List<Article> sortedArticles = new ArrayList<>();
        for (String id : articleIds) {
            articles.stream().filter(a -> a.getId().equals(id)).findFirst().ifPresent(sortedArticles::add);
        }
        List<ArticleVo> voList = copyList(sortedArticles, ArticleVo.class);
        enrichWithStats(voList);
        return Result.ok(voList);
    }

    /**
     * 为文章VO列表批量注入统计数据
     *
     * @param voList 文章VO列表
     */
    private void enrichWithStats(List<ArticleVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<String> articleIds = voList.stream()
                .map(ArticleVo::getId)
                .collect(Collectors.toList());
        List<ArticleStats> statsList = articleStatsDao.listByArticleIds(articleIds);
        Map<String, ArticleStats> statsMap = statsList.stream()
                .collect(Collectors.toMap(ArticleStats::getArticleId, Function.identity()));
        for (ArticleVo vo : voList) {
            ArticleStats stats = statsMap.get(vo.getId());
            if (stats != null) {
                vo.setViewCount(stats.getViews());
                vo.setLikeCount(stats.getLikes());
                vo.setCommentCount(stats.getComments());
                vo.setFavorites(stats.getFavorites());
            }
        }
    }

    /**
     * 查询热门标签
     *
     * @return 标签列表
     */
    @Override
    public Result<List<TagStatVo>> listPopularTags() {
        // 暂时查询所有标签，实际应查询热门
        List<Tag> tags = tagDao.list();
        List<TagStatVo> vos = tags.stream().map(tag -> TagStatVo.builder()
                .id(tag.getId())
                .name(tag.getName())
                .code(tag.getCode())
                .articleCount(0L) // 暂无统计
                .build()).collect(Collectors.toList());
        return Result.ok(vos);
    }

     /**
      * 根据文章Id查询文章基础信息
      *
      * @param articleId 文章ID
      * @return 文章信息
      */
    @Override
    public Result<ArticleVo> getById(String articleId) {
        return Result.ok(copyProperties(articleDao.getById(articleId), ArticleVo.class));
    }
}

