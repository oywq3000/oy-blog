package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.DashboardOverviewVo;
import com.oyproj.domain.vo.TopArticleVo;

import java.util.List;

/**
 * 统计看板业务（只读聚合）
 */
public interface AdminDashboardBizService {

    Result<DashboardOverviewVo> overview();

    Result<List<DailyTrendVo>> trend();

    Result<List<TopArticleVo>> topArticles();
}
