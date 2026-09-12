package com.oyproj.scheduler;

import com.oyproj.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热榜重算调度器：把日桶合并成 7 天榜与趋势榜。
 *
 * <p>失败只记日志——榜单是装饰功能，重算失败时接口会继续读上一次的结果，
 * 下一次调度自动补上。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotRankScheduler {

    private final HotRankService hotRankService;

    @Scheduled(fixedDelayString = "${oy-blog.article.hot-rank.recompute-interval-ms:300000}",
               initialDelayString = "${oy-blog.article.hot-rank.recompute-initial-delay-ms:60000}")
    public void recompute() {
        try {
            hotRankService.recompute();
        } catch (Exception e) {
            log.warn("热榜重算失败，保留上一次结果", e);
        }
    }
}
