package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleSeriesItem;

import java.util.List;

/**
 * @author LX
 * @date 2025/12/03
 * @description 文章系列项数据访问接口
 */
public interface ArticleSeriesItemDao extends IService<ArticleSeriesItem> {

    /**
     * 根据系列ID查询项列表
     *
     * @param seriesId 系列ID
     * @return 项列表
     */
    List<ArticleSeriesItem> listItems(Long seriesId);
}

