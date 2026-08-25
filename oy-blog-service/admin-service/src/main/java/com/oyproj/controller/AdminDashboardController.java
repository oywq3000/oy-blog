package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.DashboardOverviewVo;
import com.oyproj.domain.vo.TopArticleVo;
import com.oyproj.service.AdminDashboardBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端统计看板控制器
 */
@Tag(name = "管理端统计看板控制器", description = "总览卡片、访问趋势与热门文章")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardBizService biz;

    @GetMapping("/overview")
    @RequirePermission("admin:dashboard:read")
    @Operation(summary = "总览统计")
    public Result<DashboardOverviewVo> overview() {
        return biz.overview();
    }

    @GetMapping("/trend")
    @RequirePermission("admin:dashboard:read")
    @Operation(summary = "近30天访问趋势")
    public Result<List<DailyTrendVo>> trend() {
        return biz.trend();
    }

    @GetMapping("/top-articles")
    @RequirePermission("admin:dashboard:read")
    @Operation(summary = "热门文章TOP10")
    public Result<List<TopArticleVo>> topArticles() {
        return biz.topArticles();
    }
}
