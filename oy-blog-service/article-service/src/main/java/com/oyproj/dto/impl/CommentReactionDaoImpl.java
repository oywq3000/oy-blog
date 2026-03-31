package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.oyproj.common.utils.UUIDUtils;
import com.oyproj.domain.entity.CommentReaction;
import com.oyproj.dto.CommentReactionDao;
import com.oyproj.mapper.CommentReactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  评论反应数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class CommentReactionDaoImpl extends ServiceImpl<CommentReactionMapper, CommentReaction> implements CommentReactionDao {

    /**
     * 对评论进行反应（点赞/取消点赞）
     *
     * @param commentId 评论ID
     * @param userId 用户ID
     * @param type 反应类型（like/dislike）
     */
    @Override
    public void reactToComment(String commentId, String userId, String type) {
        // 先删除同用户对该评论的历史反应，再插入新的
        baseMapper.delete(new LambdaQueryWrapper<CommentReaction>()
                .eq(CommentReaction::getCommentId, commentId)
                .eq(CommentReaction::getUserId, userId));
        CommentReaction r = CommentReaction.builder()
                .id(UUIDUtils.getId())
                .commentId(commentId)
                .userId(userId)
                .reactionType(type)
                .build();
        baseMapper.insert(r);
    }

    /**
     * 对回复进行反应（点赞/取消点赞）
     *
     * @param replyId 回复ID
     * @param userId 用户ID
     * @param type 反应类型（like/dislike）
     */
    @Override
    public void reactToReply(String replyId, String userId, String type) {
        baseMapper.delete(new LambdaQueryWrapper<CommentReaction>()
                .eq(CommentReaction::getReplyId, replyId)
                .eq(CommentReaction::getUserId, userId));
        CommentReaction r = CommentReaction.builder()
                .id(UUIDUtils.getId())
                .replyId(replyId)
                .userId(userId)
                .reactionType(type)
                .build();
        baseMapper.insert(r);
    }

     /**
     * 取消对评论或回复的反应
     *
     * @param commentId 评论ID（可空）
     * @param replyId 回复ID（可空）
     * @param userId 用户ID
     */
    @Override
    public void cancelReaction(String commentId, String replyId, String userId) {
        baseMapper.delete(new LambdaQueryWrapper<CommentReaction>()
                .eq(commentId != null, CommentReaction::getCommentId, commentId)
                .eq(replyId != null, CommentReaction::getReplyId, replyId)
                .eq(CommentReaction::getUserId, userId));
    }

    /**
     * 获取用户对指定评论列表中进行了某种反应的评论ID集合
     *
     * @param commentIds 评论ID列表
     * @param userId 用户ID
     * @param type 反应类型
     * @return 评论ID集合
     */
    @Override
    public Set<String> getCommentIdsByReaction(List<String> commentIds, String userId, String type) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<CommentReaction> list = baseMapper.selectList(new LambdaQueryWrapper<CommentReaction>()
                .in(CommentReaction::getCommentId, commentIds)
                .eq(CommentReaction::getUserId, userId)
                .eq(CommentReaction::getReactionType, type));
        return list.stream().map(CommentReaction::getCommentId).collect(Collectors.toSet());
    }

    /**
     * 获取用户对指定回复列表中进行了某种反应的回复ID集合
     *
     * @param replyIds 回复ID列表
     * @param userId 用户ID
     * @param type 反应类型
     * @return 回复ID集合
     */
    @Override
    public Set<String> getReplyIdsByReaction(List<String> replyIds, String userId, String type) {
        if (replyIds == null || replyIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<CommentReaction> list = baseMapper.selectList(new LambdaQueryWrapper<CommentReaction>()
                .in(CommentReaction::getReplyId, replyIds)
                .eq(CommentReaction::getUserId, userId)
                .eq(CommentReaction::getReactionType, type));
        return list.stream().map(CommentReaction::getReplyId).collect(Collectors.toSet());
    }
}

