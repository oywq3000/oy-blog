package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.Tag;
import com.oyproj.domain.vo.TagStatVo;

import java.util.List;


/**
 * 标签数据访问接口
 */
public interface TagDao extends IService<Tag> {

    /**
     * 按名称查询标签，不存在则自动创建（is_common=0 自创标签）
     *
     * @param name 标签名称
     * @return 标签实体；name 为空时返回 null
     */
    Tag getOrCreateByName(String name);

    /**
     * 常用标签文章数统计（仅统计已发布且未软删的文章）
     *
     * @return 常用标签统计列表（按文章数降序）
     */
    List<TagStatVo> listCommonTagStats();
}
