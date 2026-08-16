package com.oyproj.service.impl;

import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.dao.UserActivityHeatmapDao;
import com.oyproj.domain.vo.HeatmapDayVo;
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

    @Override
    public Result<List<HeatmapDayVo>> getMyHeatmap() {
        String userId = getUserId();
        // 游客或未带 X-User-Id 一律空列表
        if (userId == null || userId.startsWith(CachePrefix.GUEST_ID.getPrefix())) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(activityHeatmapDao.listActivityDays(userId, LocalDate.now().minusMonths(12)));
    }
}
