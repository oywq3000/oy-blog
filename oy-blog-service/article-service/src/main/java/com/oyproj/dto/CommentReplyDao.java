package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.CommentReply;

import java.util.List;

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
     * 批量查询回复（按评论ID列表）
     *
     * @param commentIds 评论ID列表
     * @return 回复列表
     */
    List<CommentReply> listRepliesByCommentIds(List<String> commentIds);
}

