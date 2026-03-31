package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleStats;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章统计映射器
 */
@Mapper
public interface ArticleStatsMapper extends BaseMapper<ArticleStats> {}

