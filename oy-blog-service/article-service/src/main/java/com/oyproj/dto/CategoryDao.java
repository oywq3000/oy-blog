package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.Category;

/**
 * @description 分类数据访问接口
 */
public interface CategoryDao extends IService<Category> {

    /**
     * 根据编码查询分类
     *
     * @param code 分类唯一编码
     * @return 分类实体
     */
    Category getByCode(String code);
}

