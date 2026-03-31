package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ModerationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核日志映射器
 */
@Mapper
public interface ModerationLogMapper extends BaseMapper<ModerationLog> {}

