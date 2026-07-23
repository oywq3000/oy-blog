package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleStats;

import java.util.List;

/**
 * @author oy
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

    /**
     * 根据文章ID列表批量查询统计信息
     *
     * @param articleIds 文章ID列表
     * @return 文章统计列表
     */
    List<ArticleStats> listByArticleIds(List<String> articleIds);
}

