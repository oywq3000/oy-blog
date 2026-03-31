package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.CommentReaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论反应映射器
 */
@Mapper
public interface CommentReactionMapper extends BaseMapper<CommentReaction> {}

