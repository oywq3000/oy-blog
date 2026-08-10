package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论映射器
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 热度排序分页查询评论（置顶优先 + likeCount + replyCount*2 降序）
     *
     * @param articleId 文章ID
     * @param offset    偏移量
     * @param size      页大小
     * @return 当前页评论列表（已排好序）
     */
    @Select("SELECT c.* FROM comment c " +
        "LEFT JOIN (SELECT comment_id, COUNT(*) AS reply_count " +
        "  FROM comment_reply WHERE article_id = #{articleId} GROUP BY comment_id) rp " +
        "  ON c.id = rp.comment_id " +
        "LEFT JOIN (SELECT comment_id, " +
        "  SUM(CASE WHEN reaction_type = 'like' THEN 1 ELSE 0 END) AS like_count " +
        "  FROM comment_reaction WHERE article_id = #{articleId} AND comment_id IS NOT NULL " +
        "  GROUP BY comment_id) rc ON c.id = rc.comment_id " +
        "WHERE c.article_id = #{articleId} " +
        "ORDER BY c.is_pinned DESC, " +
        "  (COALESCE(rc.like_count, 0) + COALESCE(rp.reply_count, 0) * 2) DESC " +
        "LIMIT #{offset}, #{size}")
    List<Comment> selectHotPage(@Param("articleId") String articleId,
                                 @Param("offset") int offset,
                                 @Param("size") int size);
}
