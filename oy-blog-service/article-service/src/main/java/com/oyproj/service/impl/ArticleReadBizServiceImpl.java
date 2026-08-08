package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.entity.Tag;
import com.oyproj.domain.vo.ArticleChapterVo;
import com.oyproj.domain.vo.ArticleContentVo;
import com.oyproj.domain.vo.ArticleInfoVo;
import com.oyproj.domain.vo.TagStatVo;
import com.oyproj.dto.*;
import com.oyproj.service.ArticleReadBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章阅读查询业务服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleReadBizServiceImpl extends ArticleBaseBizService implements ArticleReadBizService {

    @NotNull private final ArticleDao articleDao;
    @NotNull private final ArticleContentDao contentDao;
    @NotNull private final ArticleChapterDao chapterDao;
    @NotNull private final ArticleLogDao viewDao;
    @NotNull private final TagDao tagDao;
    @NotNull private final ArticleStatsDao articleStatsDao;
    @NotNull private final UserClient userClient;

    /**
     * 根据slug查询文章
     *
     * @param slug SEO别名
     * @return 文章
     */
    @Override
    public Result<ArticleInfoVo> getBySlug(String slug) {
        ArticleInfoVo vo = copyProperties(articleDao.getBySlug(slug), ArticleInfoVo.class);
        enrichWithAuthorInfo(Collections.singletonList(vo));
        enrichWithStats(Collections.singletonList(vo));
        return Result.ok(vo);
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
    public Result<List<ArticleInfoVo>> listPublished() {
        List<ArticleInfoVo> voList = getPage(articleDao::listPublished, ArticleInfoVo.class);
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        return Result.ok(voList);
    }

    /**
     * 查询用户浏览历史
     *
     * @return 文章列表
     */
    @Override
    public Result<List<ArticleInfoVo>> listHistory() {
        List<String> articleIds = getPage(() -> viewDao.listHistoryArticleIds(getUserId()), String.class);
        if (articleIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Article> articles = articleDao.listByIds(articleIds);
        List<Article> sortedArticles = new ArrayList<>();
        for (String id : articleIds) {
            articles.stream().filter(a -> a.getId().equals(id)).findFirst().ifPresent(sortedArticles::add);
        }
        List<ArticleInfoVo> voList = copyList(sortedArticles, ArticleInfoVo.class);
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        return Result.ok(voList);
    }

    /**
     * 为文章VO列表批量注入统计数据
     *
     * @param voList 文章VO列表
     */
    private void enrichWithStats(List<ArticleInfoVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<String> articleIds = voList.stream()
                .map(ArticleInfoVo::getId)
                .collect(Collectors.toList());
        List<ArticleStats> statsList = articleStatsDao.listByArticleIds(articleIds);
        Map<String, ArticleStats> statsMap = statsList.stream()
                .collect(Collectors.toMap(ArticleStats::getArticleId, Function.identity()));
        for (ArticleInfoVo vo : voList) {
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
     * 为文章VO列表批量注入作者信息（名称和头像）
     *
     * @param voList 文章VO列表
     */
    private void enrichWithAuthorInfo(List<ArticleInfoVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<String> authorIds = voList.stream()
                .map(ArticleInfoVo::getAuthorId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, UserDTO> userMap = new HashMap<>();
        try {
            Result<List<UserDTO>> result = userClient.getUserDTOs(authorIds);
            if (result != null && result.getIsSuccess() && result.getData() != null) {
                result.getData().forEach(dto -> userMap.put(dto.getId(), dto));
            }
        } catch (Exception e) {
            log.warn("批量获取作者信息失败, authorIds: {}", authorIds, e);
        }
        for (ArticleInfoVo vo : voList) {
            UserDTO user = userMap.get(vo.getAuthorId());
            if (user != null) {
                vo.setAuthorName(user.getUsername());
                vo.setAuthorAvatar(user.getAvatarUrl());
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
    public Result<ArticleInfoVo> getById(String articleId) {
        ArticleInfoVo vo = copyProperties(articleDao.getById(articleId), ArticleInfoVo.class);
        enrichWithAuthorInfo(Collections.singletonList(vo));
        return Result.ok(vo);
    }
}

