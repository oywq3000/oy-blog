package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleStats;

/**
 * @author LX
 * @date 2025/12/03
 * @description 文章统计数据访问接口
 */
public interface ArticleStatsDao extends IService<ArticleStats> {

    /**
     * 增加阅读量
     *
     * @param articleId 文章ID
     * @param delta 增量
     */
    void incViews(String articleId, long delta);
}

