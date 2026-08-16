package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.vo.HeatmapDayVo;

import java.util.List;

public interface ArticleStatsBizService {
    /**
     * 当前登录用户最近12个月活跃度热力图（游客返回空列表）
     */
    Result<List<HeatmapDayVo>> getMyHeatmap();
}
