package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类映射器
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {}

