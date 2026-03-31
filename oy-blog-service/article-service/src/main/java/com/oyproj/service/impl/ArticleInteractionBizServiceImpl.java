package com.oyproj.service.impl;


import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.dto.ArticleFavoriteDao;
import com.oyproj.dto.ArticleLikeDao;
import com.oyproj.dto.ArticleLogDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.service.ArticleInteractionBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文章互动业务服务实现类
 */
@Service
@RequiredArgsConstructor
public class ArticleInteractionBizServiceImpl extends ArticleBaseBizService implements ArticleInteractionBizService {

    @NotNull private final ArticleLogDao viewDao;
    @NotNull private final ArticleStatsDao statsDao;
    @NotNull private final ArticleLikeDao likeDao;
    @NotNull private final ArticleFavoriteDao favoriteDao;

    /**
     * 点赞文章
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> like(String articleId) {
        likeDao.like(articleId, getUserId());
        return Result.ok();
    }

    /**
     * 取消点赞文章
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> unlike(String articleId) {
        likeDao.unlike(articleId, getUserId());
        return Result.ok();
    }

    /**
     * 收藏文章
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> favorite(String articleId) {
        favoriteDao.favorite(articleId, getUserId());
        return Result.ok();
    }

    /**
     * 取消收藏文章
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> unfavorite(String articleId) {
        favoriteDao.unfavorite(articleId, getUserId());
        return Result.ok();
    }

     /**
      * 检查用户是否点赞文章
      *
      * @param articleId 文章ID
      * @return 是否点赞
      */
    @Override
    public Result<Boolean> isLiked(String articleId) {
        return Result.ok(likeDao.hasLiked(articleId, getUserId()));
    }

    /**
     * 检查用户是否收藏文章
     *
     * @param articleId 文章ID
     * @return 是否收藏
     */
    @Override
    public Result<Boolean> isFavorited(String articleId) {
        return Result.ok(favoriteDao.hasFavorited(articleId, getUserId()));
    }

    /**
     * 统计文章点赞数量
     *
     * @param articleId 文章ID
     * @return 点赞数量
     */
    @Override
    public Result<Long> likeCount(String articleId) {
        return Result.ok(likeDao.likeCount(articleId));
    }

     /**
      * 统计文章收藏数量
      *
      * @param articleId 文章ID
      * @return 收藏数量
      */
    @Override
    public Result<Long> favoriteCount(String articleId) {
        return Result.ok(favoriteDao.favoriteCount(articleId));
    }
}
