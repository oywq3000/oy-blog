package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章-分类关联映射器
 */
@Mapper
public interface ArticleCategoryMapper extends BaseMapper<ArticleCategory> {
}
