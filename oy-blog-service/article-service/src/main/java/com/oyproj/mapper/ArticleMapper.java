package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Article;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章映射器
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {}

