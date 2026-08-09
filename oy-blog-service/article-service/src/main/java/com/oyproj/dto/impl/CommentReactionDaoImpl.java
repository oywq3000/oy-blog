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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论反应数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class CommentReactionDaoImpl extends ServiceImpl<CommentReactionMapper, CommentReaction> implements CommentReactionDao {

    /**
     * 对评论进行反应（toggle 逻辑）
     * - 无表态 → INSERT
     * - 同类型 → DELETE（取消）
     * - 不同类型 → UPDATE（切换）
     */
    @Override
    public void reactToComment(String articleId, String commentId, String userId, String type) {
        CommentReaction existing = baseMapper.selectOne(new LambdaQueryWrapper<CommentReaction>()
                .eq(CommentReaction::getCommentId, commentId)
                .eq(CommentReaction::getUserId, userId));

        if (existing == null) {
            // 无表态 → 新增
            CommentReaction r = CommentReaction.builder()
                    .id(UUIDUtils.getId())
                    .articleId(articleId)
                    .commentId(commentId)
                    .userId(userId)
                    .reactionType(type)
                    .build();
            baseMapper.insert(r);
        } else if (existing.getReactionType().equals(type)) {
            // 同类型 → 取消（删除）
            baseMapper.deleteById(existing.getId());
        } else {
            // 不同类型 → 切换
            existing.setReactionType(type);
            baseMapper.updateById(existing);
        }
    }

    /**
     * 对回复进行反应（toggle 逻辑）
     */
    @Override
    public void reactToReply(String articleId, String replyId, String userId, String type) {
        CommentReaction existing = baseMapper.selectOne(new LambdaQueryWrapper<CommentReaction>()
                .eq(CommentReaction::getReplyId, replyId)
                .eq(CommentReaction::getUserId, userId));

        if (existing == null) {
            // 无表态 → 新增
            CommentReaction r = CommentReaction.builder()
                    .id(UUIDUtils.getId())
                    .articleId(articleId)
                    .replyId(replyId)
                    .userId(userId)
                    .reactionType(type)
                    .build();
            baseMapper.insert(r);
        } else if (existing.getReactionType().equals(type)) {
            baseMapper.deleteById(existing.getId());
        } else {
            existing.setReactionType(type);
            baseMapper.updateById(existing);
        }
    }

    /**
     * 取消对评论或回复的反应
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

    /**
     * 批量获取评论/回复的 like/dislike 计数
     */
    @Override
    public Map<String, Map<String, Long>> getReactionCounts(List<String> commentIds, List<String> replyIds) {
        Map<String, Map<String, Long>> result = new HashMap<>();

        // 查评论的 reaction 计数
        if (commentIds != null && !commentIds.isEmpty()) {
            List<CommentReaction> list = baseMapper.selectList(new LambdaQueryWrapper<CommentReaction>()
                    .in(CommentReaction::getCommentId, commentIds));
            for (CommentReaction r : list) {
                result.computeIfAbsent(r.getCommentId(), k -> new HashMap<>())
                        .merge(r.getReactionType(), 1L, Long::sum);
            }
        }

        // 查回复的 reaction 计数
        if (replyIds != null && !replyIds.isEmpty()) {
            List<CommentReaction> list = baseMapper.selectList(new LambdaQueryWrapper<CommentReaction>()
                    .in(CommentReaction::getReplyId, replyIds));
            for (CommentReaction r : list) {
                result.computeIfAbsent(r.getReplyId(), k -> new HashMap<>())
                        .merge(r.getReactionType(), 1L, Long::sum);
            }
        }

        return result;
    }

    /**
     * 获取当前用户对一批目标的表态类型
     */
    @Override
    public Map<String, String> getUserReactions(List<String> commentIds, List<String> replyIds, String userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();

        if (commentIds != null && !commentIds.isEmpty()) {
            List<CommentReaction> list = baseMapper.selectList(new LambdaQueryWrapper<CommentReaction>()
                    .in(CommentReaction::getCommentId, commentIds)
                    .eq(CommentReaction::getUserId, userId));
            list.forEach(r -> result.put(r.getCommentId(), r.getReactionType()));
        }

        if (replyIds != null && !replyIds.isEmpty()) {
            List<CommentReaction> list = baseMapper.selectList(new LambdaQueryWrapper<CommentReaction>()
                    .in(CommentReaction::getReplyId, replyIds)
                    .eq(CommentReaction::getUserId, userId));
            list.forEach(r -> result.put(r.getReplyId(), r.getReactionType()));
        }

        return result;
    }
}
