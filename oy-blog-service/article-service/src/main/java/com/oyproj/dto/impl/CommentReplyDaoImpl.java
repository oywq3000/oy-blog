package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.oyproj.domain.entity.CommentReply;
import com.oyproj.dto.CommentReplyDao;
import com.oyproj.mapper.CommentReplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 *  评论回复数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class CommentReplyDaoImpl extends ServiceImpl<CommentReplyMapper, CommentReply> implements CommentReplyDao {

    /**
     * 根据评论ID查询回复列表
     *
     * @param commentId 评论ID
     * @return 回复列表
     */
    @Override
    public List<CommentReply> listByCommentId(String commentId) {
        return baseMapper.selectList(new LambdaQueryWrapper<CommentReply>()
                .eq(CommentReply::getCommentId, commentId)
                .orderByAsc(CommentReply::getReplyAt));
    }

    /**
     * 根据评论ID统计回复数量
     *
     * @param commentId 评论ID
     * @return 回复数量
     */
    @Override
    public long countByCommentId(String commentId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<CommentReply>()
                .eq(CommentReply::getCommentId, commentId));
    }

    /**
     * 批量查询回复（每条评论取前 limit 条）
     *
     * @param commentIds 评论ID列表
     * @param limit      每条评论最多取几条回复
     * @return 回复列表
     */
    @Override
    public List<CommentReply> listRepliesByCommentIds(List<String> commentIds, int limit) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return ((CommentReplyMapper) baseMapper).selectRepliesByCommentIds(commentIds, limit);
    }
}

