package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticlePendingContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章待生效编辑映射器
 */
@Mapper
public interface ArticlePendingContentMapper extends BaseMapper<ArticlePendingContent> {}
