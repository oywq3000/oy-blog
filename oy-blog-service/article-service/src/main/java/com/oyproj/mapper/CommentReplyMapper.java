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
     * 批量查询回复（按评论ID列表）
     */
    @Select("<script>" +
        "SELECT * FROM comment_reply WHERE comment_id IN " +
        "<foreach collection='commentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
        " ORDER BY reply_at ASC" +
        "</script>")
    List<CommentReply> selectRepliesByCommentIds(@Param("commentIds") List<String> commentIds);
}

