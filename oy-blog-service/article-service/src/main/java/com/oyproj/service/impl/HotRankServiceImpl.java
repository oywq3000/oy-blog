package com.oyproj.service.impl;

import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.util.FaultLogThrottle;
import com.oyproj.config.HotRankProperties;
import com.oyproj.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 热榜服务实现。
 *
 * <p>用 Redis 日桶 ZSet 而不是 Kafka Streams 做窗口聚合：消费者因此变成<b>纯加法、零状态</b>，
 * 重启瞬间恢复，且"清空 ZSet + 重置消费组位点"即可完整重放重建榜单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotRankServiceImpl implements HotRankService {

    /** 近 7 天榜 */
    public static final String KEY_SEVEN_DAY = "hot:article:7d";
    /** 趋势榜（正在暴涨的） */
    public static final String KEY_TREND = "hot:article:trend";

    private static final String DAY_KEY_PREFIX = "hot:article:";
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int WINDOW_DAYS = 7;

    private final CommonCache<Object> commonCache;
    private final HotRankProperties hotRankProperties;

    /** 故障日志限流：畸形事件是逐条来的，不限流时它自己就是一场日志风暴 */
    private final FaultLogThrottle parseFailureLog = new FaultLogThrottle();
    /** 故障日志限流：Redis 挂掉时 /published/hot 每个请求都会走这里 */
    private final FaultLogThrottle readFailureLog = new FaultLogThrottle();

    @Override
    public void recordEvent(ArticleBehaviorEvent event) {
        if (event == null || event.getArticleId() == null || event.getArticleId().isEmpty()) {
            return;
        }
        String dayKey;
        try {
            // 按事件发生时间归日，而非处理时间：消息延迟到达时窗口归属才稳定
            dayKey = dayKey(OffsetDateTime.parse(event.getOccurredAt()).toLocalDate());
        } catch (Exception e) {
            // 限流：畸形事件通常成批出现（上游格式变更/时钟异常），逐条打堆栈会淹掉日志管道
            if (parseFailureLog.allow()) {
                log.warn("行为事件时间戳无法解析，跳过（本窗口已抑制 {} 条同类故障）, eventId: {}",
                        parseFailureLog.drainSuppressed(), event.getEventId(), e);
            }
            return;
        }
        commonCache.incrementScore(dayKey, event.getArticleId(), event.getWeight());
        commonCache.expire(dayKey, (long) hotRankProperties.getRetentionDays() * 24 * 3600);
    }

    @Override
    public void recompute() {
        List<String> days = recentDayKeys();

        // 近 7 天榜：7 个日桶等权相加
        commonCache.zUnionStore(KEY_SEVEN_DAY, days, new int[]{1, 1, 1, 1, 1, 1, 1});

        // 趋势榜：18 × 今天 − 前 6 天之和
        // 数学上等于 6 × (3 × 今天 − 前6天均值)。排行榜只看相对大小，系数不影响名次，
        // 而 ZUNIONSTORE 不支持除法，所以用这个等价形式。
        commonCache.zUnionStore(KEY_TREND, days, new int[]{18, -1, -1, -1, -1, -1, -1});
    }

    @Override
    public List<String> topArticleIds(String rankKey, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        try {
            Set<ZSetOperations.TypedTuple<Object>> tuples =
                    commonCache.reverseRangeWithScores(rankKey, 0, limit - 1);
            if (tuples == null || tuples.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> ids = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                if (tuple != null && tuple.getValue() != null) {
                    ids.add(String.valueOf(tuple.getValue()));
                }
            }
            return ids;
        } catch (Exception e) {
            if (readFailureLog.allow()) {
                log.warn("读取热榜失败，降级为 MySQL 榜（本窗口已抑制 {} 条同类故障）, rankKey: {}",
                        readFailureLog.drainSuppressed(), rankKey, e);
            }
            return Collections.emptyList();
        }
    }

    /** 最近 7 天的日桶 key，今天在最前（权重顺序与之对应） */
    private List<String> recentDayKeys() {
        LocalDate today = LocalDate.now();
        List<String> keys = new ArrayList<>(WINDOW_DAYS);
        for (int i = 0; i < WINDOW_DAYS; i++) {
            keys.add(dayKey(today.minusDays(i)));
        }
        return keys;
    }

    private String dayKey(LocalDate day) {
        return DAY_KEY_PREFIX + day.format(DAY_FORMAT);
    }
}
