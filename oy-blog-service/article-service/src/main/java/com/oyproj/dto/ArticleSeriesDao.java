package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleSeries;

/**
 * @author LX
 * @date 2025/12/03
 * @description 文章系列数据访问接口
 */
public interface ArticleSeriesDao extends IService<ArticleSeries> {

    /**
     * 根据唯一编码查询系列
     *
     * @param code 唯一编码
     * @return 文章系列
     */
    ArticleSeries getByCode(String code);
}

