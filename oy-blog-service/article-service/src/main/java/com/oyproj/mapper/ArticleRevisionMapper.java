package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleRevision;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章修订映射器
 */
@Mapper
public interface ArticleRevisionMapper extends BaseMapper<ArticleRevision> {}

