package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.common.utils.UUIDUtils;
import com.oyproj.domain.entity.ArticleLike;
import com.oyproj.dto.ArticleLikeDao;
import com.oyproj.mapper.ArticleLikeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * @description 文章点赞数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleLikeDaoImpl extends ServiceImpl<ArticleLikeMapper, ArticleLike> implements ArticleLikeDao {

    /**
     * 是否已点赞
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return 是否已点赞
     */
    @Override
    public boolean hasLiked(String articleId, String userId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId)
                .eq(ArticleLike::getUserId, userId)) > 0;
    }

    /**
     * 点赞
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     */
    @Override
    public void like(String articleId, String userId) {
        if (!hasLiked(articleId, userId)) {
            ArticleLike like = ArticleLike.builder()
                    .id(UUIDUtils.getId())
                    .articleId(articleId)
                    .userId(userId)
                    .build();
            baseMapper.insert(like);
        }
    }

    /**
     * 取消点赞
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     */
    @Override
    public void unlike(String articleId, String userId) {
        baseMapper.delete(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId)
                .eq(ArticleLike::getUserId, userId));
    }

    /**
     * 统计点赞数量
     *
     * @param articleId 文章ID
     * @return 点赞数量
     */
    @Override
    public long likeCount(String articleId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<ArticleLike>()
                .eq(ArticleLike::getArticleId, articleId));
    }
}

