package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.oyproj.domain.entity.CommentReply;
import org.apache.ibatis.annotations.Mapper;

/**
 *  评论回复映射器
 */
@Mapper
public interface CommentReplyMapper extends BaseMapper<CommentReply> {}

