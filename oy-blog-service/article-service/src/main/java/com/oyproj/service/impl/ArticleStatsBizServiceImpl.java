package com.oyproj.service.impl;

import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.dao.UserActivityHeatmapDao;
import com.oyproj.domain.vo.ArticleStatsVo;
import com.oyproj.domain.vo.HeatmapDayVo;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.dto.TagDao;
import com.oyproj.service.ArticleStatsBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleStatsBizServiceImpl extends ArticleBaseBizService implements ArticleStatsBizService {

    @NotNull
    private final UserActivityHeatmapDao activityHeatmapDao;

    @NotNull
    private final ArticleDao articleDao;

    @NotNull
    private final ArticleStatsDao articleStatsDao;

    @NotNull
    private final TagDao tagDao;

    @Override
    public Result<List<HeatmapDayVo>> getMyHeatmap() {
        String userId = getUserId();
        // 游客或未带 X-User-Id 一律空列表
        if (userId == null || userId.startsWith(CachePrefix.GUEST_ID.getPrefix())) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(activityHeatmapDao.listActivityDays(userId, LocalDate.now().minusMonths(12)));
    }

    /**
     * 指定用户最近12个月活跃度热力图（公开，不受当前登录态影响）
     */
    @Override
    public Result<List<HeatmapDayVo>> getUserHeatmap(String userId) {
        return Result.ok(activityHeatmapDao.listActivityDays(userId, LocalDate.now().minusMonths(12)));
    }

    @Override
    public Result<ArticleStatsVo> getGlobalStats() {
        ArticleStatsVo vo = new ArticleStatsVo();
        vo.setArticleCount(nvl(articleDao.countPublished()));
        vo.setViewCount(nvl(articleStatsDao.sumViews()));
        vo.setLikeCount(nvl(articleStatsDao.sumLikes()));
        vo.setTagCount(nvl(tagDao.count()));
        return Result.ok(vo);
    }

    private Long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
