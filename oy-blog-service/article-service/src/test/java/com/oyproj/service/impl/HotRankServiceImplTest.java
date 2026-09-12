package com.oyproj.service.impl;

import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.common.service.CommonCache;
import com.oyproj.config.HotRankProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HotRankService 日桶写入与窗口合并")
class HotRankServiceImplTest {

    @Mock private CommonCache<Object> commonCache;

    private HotRankProperties props;
    private HotRankServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new HotRankProperties();
        props.setWindowSize(200);
        props.setRetentionDays(8);
        service = new HotRankServiceImpl(commonCache, props);
    }

    private static ArticleBehaviorEvent event(String articleId, int weight, LocalDate day) {
        return ArticleBehaviorEvent.builder()
                .eventId("e-" + articleId + "-" + weight)
                .eventType(ArticleBehaviorType.VIEW)
                .articleId(articleId)
                .occurredAt(day.atTime(12, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString())
                .weight(weight)
                .build();
    }

    @Test
    void recordEvent_incrementsDailyBucketWithWeight() {
        LocalDate day = LocalDate.of(2026, 9, 12);

        service.recordEvent(event("A1", 5, day));

        String expectedKey = "hot:article:" + day.format(DateTimeFormatter.BASIC_ISO_DATE);
        verify(commonCache).incrementScore(expectedKey, "A1", 5);
    }

    @Test
    void recordEvent_usesEventDate_notToday() {
        LocalDate old = LocalDate.of(2026, 9, 5);

        service.recordEvent(event("A1", 1, old));

        verify(commonCache).incrementScore("hot:article:20260905", "A1", 1);
    }

    @Test
    void recordEvent_negativeWeight_isPassedThrough() {
        LocalDate day = LocalDate.of(2026, 9, 12);

        service.recordEvent(event("A1", -2, day));

        verify(commonCache).incrementScore(eq("hot:article:20260912"), eq("A1"), eq(-2));
    }

    @Test
    void recordEvent_setsBucketTtl() {
        service.recordEvent(event("A1", 1, LocalDate.of(2026, 9, 12)));

        verify(commonCache).expire("hot:article:20260912", 8L * 24 * 3600);
    }

    @Test
    void recordEvent_nullOrBlankArticleId_isIgnored() {
        service.recordEvent(null);
        service.recordEvent(event("", 1, LocalDate.of(2026, 9, 12)));

        verify(commonCache, never()).incrementScore(any(), any(), anyInt());
    }

    @Test
    void recordEvent_malformedDate_isSwallowed() {
        ArticleBehaviorEvent bad = ArticleBehaviorEvent.builder()
                .articleId("A1").weight(1).occurredAt("not-a-date").build();

        assertDoesNotThrow(() -> service.recordEvent(bad));
        verify(commonCache, never()).incrementScore(any(), any(), anyInt());
    }

    /**
     * 期望的 7 个日桶 key，从「今天」往前推：{@code [today, today-1, ..., today-6]}。
     *
     * <p>必须逐项断言全量，不能只查 {@code size==7} 和 {@code keys[0]}：那样只钉住了
     * 下标 0，1~6 是自由的。把 {@code today.minusDays(i)} 写成 {@code plusDays(i)}
     * （窗口指向未来）时两个断言都还是绿的，而 7 天榜会静默变空、接口静默回退
     * MySQL 全时段榜 —— 没有任何报错。</p>
     */
    private static List<String> expectedDayKeys() {
        LocalDate today = LocalDate.now();
        List<String> keys = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            keys.add("hot:article:" + today.minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE));
        }
        return keys;
    }

    @Test
    void recompute_sevenDay_usesEqualWeightsOverSevenDays() {
        service.recompute();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<int[]> weightsCaptor = ArgumentCaptor.forClass(int[].class);
        verify(commonCache).zUnionStore(eq(HotRankServiceImpl.KEY_SEVEN_DAY),
                keysCaptor.capture(), weightsCaptor.capture());

        assertArrayEquals(new int[]{1, 1, 1, 1, 1, 1, 1}, weightsCaptor.getValue());
        // 顺序：今天在最前，往前推 6 天 —— 全量逐项比对（下标 1~6 也要钉住）
        assertEquals(expectedDayKeys(), keysCaptor.getValue());
    }

    @Test
    void recompute_trend_weightsToday18AndEachPastDayMinus1() {
        service.recompute();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<int[]> weightsCaptor = ArgumentCaptor.forClass(int[].class);
        verify(commonCache).zUnionStore(eq(HotRankServiceImpl.KEY_TREND),
                keysCaptor.capture(), weightsCaptor.capture());

        assertArrayEquals(new int[]{18, -1, -1, -1, -1, -1, -1}, weightsCaptor.getValue());
        // 权重是按位置跟 key 对齐的：18 必须落在今天、-1 落在过去 6 天，
        // 所以这里的全量 key 断言同时保护了权重的对齐关系
        assertEquals(expectedDayKeys(), keysCaptor.getValue());
    }

    @Test
    void topArticleIds_readsWindowAndReturnsIds() {
        when(commonCache.reverseRangeWithScores("hot:article:7d", 0, 199))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(
                        // 必须显式写 <Object> 类型见证：of() 是 static <V> TypedTuple<V> of(V, Double)，
                        // 不写会从实参推断成 TypedTuple<String>，与 mock 声明的 TypedTuple<Object> 不兼容、编译不过。
                        // 见证只能写在方法名上：Java 没有"给限定表达式里的嵌套泛型类型带类型参数"的语法，
                        // 写成 ZSetOperations.<Object>TypedTuple.of(...) 是语法错误（javac: 非法的表达式开始）。
                        org.springframework.data.redis.core.ZSetOperations.TypedTuple.<Object>of("A1", 9.0),
                        org.springframework.data.redis.core.ZSetOperations.TypedTuple.<Object>of("A2", 5.0))));

        List<String> ids = service.topArticleIds("hot:article:7d", 200);

        assertEquals(List.of("A1", "A2"), ids);
    }

    @Test
    void topArticleIds_redisFailure_returnsEmpty() {
        when(commonCache.reverseRangeWithScores(any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("redis down"));

        assertTrue(service.topArticleIds("hot:article:7d", 200).isEmpty());
    }
}
