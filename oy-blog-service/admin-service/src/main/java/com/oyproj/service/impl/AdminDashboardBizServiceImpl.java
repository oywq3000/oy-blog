package com.oyproj.service.impl;

import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.dao.DashboardDao;
import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.DashboardOverviewVo;
import com.oyproj.domain.vo.TopArticleVo;
import com.oyproj.service.AdminDashboardBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardBizServiceImpl extends AdminBizBase implements AdminDashboardBizService {

    private final DashboardDao dashboardDao;

    @Override
    public Result<DashboardOverviewVo> overview() {
        DashboardOverviewVo vo = new DashboardOverviewVo(
                dashboardDao.countPublishedArticles(),
                dashboardDao.sumViews(),
                dashboardDao.sumLikes(),
                dashboardDao.sumComments(),
                dashboardDao.countUsers());
        return Result.ok(vo);
    }

    @Override
    public Result<List<DailyTrendVo>> trend() {
        return Result.ok(dashboardDao.listDailyViews());
    }

    @Override
    public Result<List<TopArticleVo>> topArticles() {
        return Result.ok(dashboardDao.listTopArticles());
    }
}
