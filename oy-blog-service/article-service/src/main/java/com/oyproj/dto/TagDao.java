package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.Tag;


/**
 * 标签数据访问接口
 */
public interface TagDao extends IService<Tag> {

    /**
     * 根据编码查询标签
     *
     * @param code 标签唯一编码
     * @return 标签实体
     */
    Tag getByCode(String code);

    /**
     * 查询热门标签
     * @param limit 限制数量
     * @return 标签列表
     */
    // List<TagStatVo> listPopularTags(int limit); // 暂时返回Tag实体或自定义VO，这里先不做复杂统计，简单返回所有
    // 实际应查询 article_tag 关联表统计
    // 暂且保留基础方法
}

