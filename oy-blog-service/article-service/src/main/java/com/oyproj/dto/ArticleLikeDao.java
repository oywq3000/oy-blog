package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleLike;

import java.util.List;

/**
 * @description 文章点赞数据访问接口
 */
public interface ArticleLikeDao extends IService<ArticleLike> {

    /**
     * 是否已点赞
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    boolean hasLiked(String articleId, String userId);

    /**
     * 点赞
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     */
    void like(String articleId, String userId);

    /**
     * 取消点赞
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     */
    void unlike(String articleId, String userId);

    /**
     * 统计点赞数量
     *
     * @param articleId 文章ID
     * @return 点赞数量
     */
    long likeCount(String articleId);

    /**
     * 查询用户点赞过的文章ID列表（去重）
     *
     * @param userId 用户ID
     * @return 文章ID列表
     */
    List<String> listLikedArticleIds(String userId);
}

