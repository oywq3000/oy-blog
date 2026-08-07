package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.oyproj.domain.entity.CommentReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 *  评论回复映射器
 */
@Mapper
public interface CommentReplyMapper extends BaseMapper<CommentReply> {

    /**
     * 批量查询回复（每条评论取前 limit 条，按时间升序）
     */
    @Select("<script>" +
        "SELECT * FROM (" +
        "  SELECT cr.*, ROW_NUMBER() OVER (PARTITION BY comment_id ORDER BY reply_at ASC) AS rn" +
        "  FROM comment_reply cr" +
        "  WHERE cr.comment_id IN " +
        "  <foreach collection='commentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
        ") t WHERE t.rn &lt;= #{limit}" +
        "</script>")
    List<CommentReply> selectRepliesByCommentIds(@Param("commentIds") List<String> commentIds, @Param("limit") int limit);
}

