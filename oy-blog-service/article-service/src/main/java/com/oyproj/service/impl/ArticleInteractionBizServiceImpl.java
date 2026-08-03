package com.oyproj.service.impl;


import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.constant.CachePrefix;
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
        String userId = getUserId();
        if (!likeDao.hasLiked(articleId, userId)) {
            likeDao.like(articleId, userId);
            statsDao.incLikes(articleId, 1);
        }
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
        String userId = getUserId();
        if (likeDao.hasLiked(articleId, userId)) {
            likeDao.unlike(articleId, userId);
            statsDao.incLikes(articleId, -1);
        }
        return Result.ok();
    }

    /**
     * 收藏文章（仅认证用户）
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> favorite(String articleId) {
        if (isGuest()) {
            return Result.error("游客不支持收藏");
        }
        String userId = getUserId();
        if (!favoriteDao.hasFavorited(articleId, userId)) {
            favoriteDao.favorite(articleId, userId);
            statsDao.incFavorites(articleId, 1);
        }
        return Result.ok();
    }

    /**
     * 取消收藏文章（仅认证用户）
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> unfavorite(String articleId) {
        if (isGuest()) {
            return Result.error("游客不支持收藏");
        }
        String userId = getUserId();
        if (favoriteDao.hasFavorited(articleId, userId)) {
            favoriteDao.unfavorite(articleId, userId);
            statsDao.incFavorites(articleId, -1);
        }
        return Result.ok();
    }

    /**
     * 判断当前请求是否为游客
     */
    private boolean isGuest() {
        String userId = getUserId();
        return userId != null && userId.startsWith(CachePrefix.GUEST_ID.getPrefix());
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
