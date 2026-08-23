package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.Comment;

import java.util.List;

/**
 * 评论数据访问接口
 */
public interface CommentDao extends IService<Comment> {

     /**
     * 统计文章评论数量
     *
     * @param articleId 文章ID
     * @return 评论数量
     */
    long countByArticle(String articleId);

    /**
     * 根据文章ID查询评论列表
     *
     * @param articleId 文章ID
     * @return 评论列表（按楼层升序）
     */
    List<Comment> listByArticle(String articleId);

    /**
     * 根据文章ID查询评论列表（按最新排序）
     * 置顶优先，然后按评论时间倒序
     *
     * @param articleId 文章ID
     * @return 评论列表
     */
    List<Comment> listByArticleOrderByNewest(String articleId, Page<Comment> page);

    /**
     * 根据文章ID + 热度排序分页查询评论
     * 排序规则：置顶优先，然后按 likeCount + replyCount*2 降序
     *
     * @param articleId 文章ID
     * @param offset    偏移量
     * @param size      页大小
     * @return 当前页评论列表（已排序）
     */
    List<Comment> listByArticleOrderByHot(String articleId, int offset, int size);

    /**
     * 根据文章ID查询全部评论（无排序，供热度排序后内存处理）
     *
     * @param articleId 文章ID
     * @return 全量评论列表
     */
    List<Comment> listAllByArticle(String articleId);

    /**
     * 置顶评论
     *
     * @param commentId 评论ID
     */
    void pin(String commentId);

    /**
     * 取消置顶评论
     *
     * @param commentId 评论ID
     */
    void unpin(String commentId);

    /**
     * 获取文章当前最大楼层
     * @param articleId 文章ID
     * @return 最大楼层
     */
    Integer getMaxFloor(String articleId);
}

