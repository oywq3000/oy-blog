package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.CommentReply;

import java.util.List;
import java.util.Map;

/**
 * 评论回复数据访问接口
 */
public interface CommentReplyDao extends IService<CommentReply> {

    /**
     * 根据评论ID查询回复列表
     *
     * @param commentId 评论ID
     * @return 回复列表
     */
    List<CommentReply> listByCommentId(String commentId);

    /**
     * 根据评论ID统计回复数量
     *
     * @param commentId 评论ID
     * @return 回复数量
     */
    long countByCommentId(String commentId);

    /**
     * 批量查询回复（每条评论取前 limit 条）
     *
     * @param commentIds 评论ID列表
     * @param limit      每条评论最多取几条回复
     * @return 回复列表
     */
    List<CommentReply> listRepliesByCommentIds(List<String> commentIds, int limit);

    /**
     * 批量统计每条评论的回复数量
     *
     * @param commentIds 评论ID列表
     * @return Map&lt;commentId, count&gt;
     */
    Map<String, Long> countByCommentIds(List<String> commentIds);

    /**
     * 统计文章下所有回复数量（用于计算总评论数）
     *
     * @param articleId 文章ID
     * @return 回复总数
     */
    long countByArticleId(String articleId);
}

