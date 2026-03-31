package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleContent;
import org.apache.ibatis.annotations.Mapper;

/**
 *  文章内容映射器
 */
@Mapper
public interface ArticleContentMapper extends BaseMapper<ArticleContent> {}
