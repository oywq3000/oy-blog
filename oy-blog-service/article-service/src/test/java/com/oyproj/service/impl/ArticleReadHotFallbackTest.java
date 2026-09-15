package com.oyproj.service.impl;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.config.HotRankProperties;
import com.oyproj.config.HotWeightProperties;
import com.oyproj.config.RecommendProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.vo.ArticleInfoVo;
import com.oyproj.dto.*;
import com.oyproj.mapper.ArticleMapper;
import com.oyproj.mapper.ArticleSeriesItemMapper;
import com.oyproj.mapper.ArticleSeriesMapper;
import com.oyproj.service.HotRankService;
import com.oyproj.service.RecommendationBizService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("/published/hot 的 Redis 优先与 MySQL 降级")
class ArticleReadHotFallbackTest {

    @Mock private ArticleDao articleDao;
    @Mock private ArticleContentDao contentDao;
    @Mock private ArticleChapterDao chapterDao;
    @Mock private ArticleLogDao viewDao;
    @Mock private TagDao tagDao;
    @Mock private ArticleTagDao articleTagDao;
    @Mock private ArticleStatsDao articleStatsDao;
    @Mock private com.oyproj.api.user.client.UserClient userClient;
    @Mock private HotWeightProperties hotWeightProperties;
    @Mock private ArticleMapper articleMapper;
    @Mock private ArticleSeriesMapper seriesMapper;
    @Mock private ArticleSeriesItemMapper seriesItemMapper;
    @Mock private HotRankService hotRankService;
    @Mock private HotRankProperties hotRankProperties;
    @Mock private CommonCache<Object> commonCache;
    @Mock private RecommendationBizService recommendationBizService;
    @Mock private RecommendProperties recommendProperties;

    private ArticleReadBizServiceImpl service;

    /** Result.ok() 依赖 I18nUtils 的静态 messageSource，纯 Mockito 环境必须反射注入 */
    @BeforeAll
    static void initMessageSource() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mock(MessageSource.class));
    }

    @BeforeEach
    void setUp() {
        service = spy(new ArticleReadBizServiceImpl(
                articleDao, contentDao, chapterDao, viewDao, tagDao, articleTagDao, articleStatsDao, userClient,
                hotWeightProperties, articleMapper, seriesMapper, seriesItemMapper,
                hotRankService, hotRankProperties, commonCache, recommendationBizService, recommendProperties));
        lenient().when(hotRankProperties.getWindowSize()).thenReturn(200);
    }

    @Test
    void redisEmpty_fallsBackToMysql() {
        when(hotRankService.topArticleIds(any(), anyInt())).thenReturn(Collections.emptyList());
        when(articleDao.countPublished()).thenReturn(0L);

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByHot(1, 10, "7d");

        assertTrue(result.getIsSuccess());
        verify(articleMapper, never()).selectHotPage(anyInt(), anyInt(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void pageBeyondWindow_fallsBackToMysql() {
        when(articleDao.countPublished()).thenReturn(0L);

        service.listPublishedByHot(100, 10, "7d");   // 100 * 10 = 1000 > window 200

        verify(hotRankService, never()).topArticleIds(any(), anyInt());
    }

    @Test
    void redisHasData_usesRedisOrderAndSkipsUnpublished() {
        when(hotRankService.topArticleIds("hot:article:7d", 200)).thenReturn(List.of("A2", "A1", "A3"));
        when(articleDao.listByIds(anyList())).thenReturn(List.of(
                Article.builder().id("A1").status("published").build(),
                Article.builder().id("A2").status("published").build(),
                Article.builder().id("A3").status("draft").build()));   // 草稿必须被剔除
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByHot(1, 10, "7d");

        List<ArticleInfoVo> data = result.getData().getData();
        assertEquals(2, data.size());
        assertEquals("A2", data.get(0).getId());   // 顺序按 Redis 榜，不按 listByIds 返回顺序
        assertEquals("A1", data.get(1).getId());
    }

    @Test
    void filteredPageEmpty_fallsBackToMysql() {
        // 请求页在窗口内（1×10 ≤ 200），但榜上唯一一篇已变草稿 → Redis 侧过滤后为空，
        // 必须把 MySQL 的结果交给用户，而不是返回空页
        when(hotRankService.topArticleIds("hot:article:7d", 200)).thenReturn(List.of("A1"));
        when(articleDao.listByIds(anyList())).thenReturn(List.of(
                Article.builder().id("A1").status("draft").build()));
        when(articleDao.countPublished()).thenReturn(1L);
        when(articleDao.listPublishedByHot(1, 10, 0L, 0L, 0L, 0L)).thenReturn(List.of(
                Article.builder().id("DB1").status("published").build()));
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByHot(1, 10, "7d");

        assertTrue(result.getIsSuccess());
        assertEquals(1, result.getData().getData().size());
        assertEquals("DB1", result.getData().getData().get(0).getId());
    }

    @Test
    void overflowingPageNum_fallsBackToMysqlInsteadOfThrowing() {
        // normalizePage 不兜 pageNum 上界，而 1073741825 × 2 = 2147483650 会把 int 乘翻负；
        // 若窗口判断用 int 相乘，翻负后"看似在窗口内"，负 offset 会一路带进 subList → 500。
        when(articleDao.countPublished()).thenReturn(0L);

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByHot(1073741825, 2, "7d");

        assertTrue(result.getIsSuccess());
        verify(hotRankService, never()).topArticleIds(any(), anyInt());
    }

    @Test
    void trendEndpoint_readsTrendKey() {
        when(hotRankService.topArticleIds("hot:article:trend", 200)).thenReturn(Collections.emptyList());
        when(articleDao.countPublished()).thenReturn(0L);

        service.listPublishedByTrend(1, 10);

        verify(hotRankService).topArticleIds("hot:article:trend", 200);
    }

    @Test
    void hotPeriod30d_readsMonthKey() {
        when(hotRankService.topArticleIds("hot:article:30d", 200)).thenReturn(Collections.emptyList());
        when(articleDao.countPublished()).thenReturn(0L);

        service.listPublishedByHot(1, 10, "30d");

        verify(hotRankService).topArticleIds("hot:article:30d", 200);
    }

    @Test
    void hotPeriod90d_readsQuarterKey() {
        when(hotRankService.topArticleIds("hot:article:90d", 200)).thenReturn(Collections.emptyList());
        when(articleDao.countPublished()).thenReturn(0L);

        service.listPublishedByHot(1, 10, "90d");

        verify(hotRankService).topArticleIds("hot:article:90d", 200);
    }

    @Test
    void hotPeriodUnknown_fallsBackToWeekKey() {
        when(hotRankService.topArticleIds("hot:article:7d", 200)).thenReturn(Collections.emptyList());
        when(articleDao.countPublished()).thenReturn(0L);

        service.listPublishedByHot(1, 10, "bogus");

        verify(hotRankService).topArticleIds("hot:article:7d", 200);
    }
}
