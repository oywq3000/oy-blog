package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章点赞映射器
 */
@Mapper
public interface ArticleLikeMapper extends BaseMapper<ArticleLike> {}

