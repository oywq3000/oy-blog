package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论映射器
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {}

