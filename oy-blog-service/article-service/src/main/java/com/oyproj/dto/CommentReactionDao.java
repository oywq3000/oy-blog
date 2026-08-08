package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.CommentReaction;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评论反应数据访问接口
 */
public interface CommentReactionDao extends IService<CommentReaction> {

    /**
     * 对评论进行反应（toggle：同类型取消，不同类型切换，无则新增）
     *
     * @param articleId 文章ID
     * @param commentId 评论ID
     * @param userId 用户ID
     * @param type 反应类型（like/dislike）
     */
    void reactToComment(String articleId, String commentId, String userId, String type);

    /**
     * 对回复进行反应（toggle）
     *
     * @param articleId 文章ID
     * @param replyId 回复ID
     * @param userId 用户ID
     * @param type 反应类型（like/dislike）
     */
    void reactToReply(String articleId, String replyId, String userId, String type);

    /**
     * 取消对评论或回复的反应
     *
     * @param commentId 评论ID
     * @param replyId 回复ID
     * @param userId 用户ID
     */
    void cancelReaction(String commentId, String replyId, String userId);

    /**
     * 获取用户对指定评论列表中进行了某种反应的评论ID集合
     *
     * @param commentIds 评论ID列表
     * @param userId 用户ID
     * @param type 反应类型
     * @return 评论ID集合
     */
    Set<String> getCommentIdsByReaction(List<String> commentIds, String userId, String type);

    /**
     * 获取用户对指定回复列表中进行了某种反应的回复ID集合
     *
     * @param replyIds 回复ID列表
     * @param userId 用户ID
     * @param type 反应类型
     * @return 回复ID集合
     */
    Set<String> getReplyIdsByReaction(List<String> replyIds, String userId, String type);

    /**
     * 批量获取评论/回复的 like/dislike 计数
     *
     * @param commentIds 评论ID列表
     * @param replyIds 回复ID列表
     * @return Map&lt;targetId, Map&lt;reactionType, count&gt;&gt;
     */
    Map<String, Map<String, Long>> getReactionCounts(List<String> commentIds, List<String> replyIds);

    /**
     * 获取当前用户对一批目标的表态类型
     *
     * @param commentIds 评论ID列表
     * @param replyIds 回复ID列表
     * @param userId 用户ID
     * @return Map&lt;targetId, reactionType&gt;
     */
    Map<String, String> getUserReactions(List<String> commentIds, List<String> replyIds, String userId);
}

