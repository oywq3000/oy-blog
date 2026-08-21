package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Tag;
import com.oyproj.domain.vo.TagStatVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 *  标签映射器
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 常用标签文章数统计（仅统计已发布且未软删的文章，SQL 见 resources/mapper/TagMapper.xml）
     *
     * @return 常用标签统计列表（按文章数降序）
     */
    List<TagStatVo> listCommonTagStats();
}

