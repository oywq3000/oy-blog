package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章阅读映射器
 */
@Mapper
public interface ArticleLogMapper extends BaseMapper<ArticleLog> {}

