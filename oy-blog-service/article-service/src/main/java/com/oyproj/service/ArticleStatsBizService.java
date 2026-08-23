package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.vo.ArticleStatsVo;
import com.oyproj.domain.vo.HeatmapDayVo;

import java.util.List;

public interface ArticleStatsBizService {
    /**
     * 当前登录用户最近12个月活跃度热力图（游客返回空列表）
     */
    Result<List<HeatmapDayVo>> getMyHeatmap();

    /**
     * 全库文章数据统计（已发布文章数、阅读量总和、点赞数总和、标签总数）
     */
    Result<ArticleStatsVo> getGlobalStats();
}
