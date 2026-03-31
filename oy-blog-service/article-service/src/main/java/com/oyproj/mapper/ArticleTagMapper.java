package com.oyproj.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章-标签关联映射器
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {}

