package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章收藏映射器
 */
@Mapper
public interface ArticleFavoriteMapper extends BaseMapper<ArticleFavorite> {}

