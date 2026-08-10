package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.MqRetryLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqRetryLogMapper extends BaseMapper<MqRetryLog> {
}
