package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 *  标签映射器
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {}

