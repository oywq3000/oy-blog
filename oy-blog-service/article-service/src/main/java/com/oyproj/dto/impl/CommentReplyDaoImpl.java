package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.oyproj.domain.entity.CommentReply;
import com.oyproj.dto.CommentReplyDao;
import com.oyproj.mapper.CommentReplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public List<CommentReply> listByCommentId(String commentId, Page<CommentReply> page) {
        return baseMapper.selectPage(page, new LambdaQueryWrapper<CommentReply>()
                .eq(CommentReply::getCommentId, commentId)
                .eq(CommentReply::getStatus, 1)
                .orderByAsc(CommentReply::getReplyAt))
                .getRecords();
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

    /**
     * 批量统计每条评论的回复数量
     */
    @Override
    public Map<String, Long> countByCommentIds(List<String> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = baseMapper.selectMaps(new QueryWrapper<CommentReply>()
                .select("comment_id, COUNT(*) AS cnt")
                .in("comment_id", commentIds)
                .groupBy("comment_id"));
        return rows.stream().collect(Collectors.toMap(
                row -> (String) row.get("comment_id"),
                row -> (Long) row.get("cnt")));
    }

    /**
     * 统计文章下所有回复数量
     */
    @Override
    public long countByArticleId(String articleId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<CommentReply>()
                .eq(CommentReply::getArticleId, articleId));
    }
}

