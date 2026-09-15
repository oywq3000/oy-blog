package com.oyproj.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.config.HotRankProperties;
import com.oyproj.config.HotWeightProperties;
import com.oyproj.config.RecommendProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleLog;
import com.oyproj.domain.entity.ArticleSeries;
import com.oyproj.domain.entity.ArticleSeriesItem;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.vo.ArticleInfoVo;
import com.oyproj.domain.vo.SeriesArticleLinkVo;
import com.oyproj.domain.vo.SeriesDetailVo;
import com.oyproj.domain.vo.SeriesMemberCountVo;
import com.oyproj.domain.vo.SeriesReadVo;
import com.oyproj.dto.ArticleChapterDao;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleLogDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.dto.TagDao;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ArticleReadBizServiceImpl 中 listHistory（浏览历史）的单元测试：
 * 空历史、按最近浏览时间倒序去重、viewedAt 回填、统计/作者信息补全、脏数据容错。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleReadBizServiceImpl.listHistory")
class ArticleReadBizServiceImplTest {

    @Mock
    private ArticleDao articleDao;
    @Mock
    private ArticleContentDao contentDao;
    @Mock
    private ArticleChapterDao chapterDao;
    @Mock
    private ArticleLogDao viewDao;
    @Mock
    private TagDao tagDao;
    @Mock
    private ArticleTagDao articleTagDao;
    @Mock
    private ArticleStatsDao articleStatsDao;
    @Mock
    private UserClient userClient;
    @Mock
    private HotWeightProperties hotWeightProperties;
    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private ArticleSeriesMapper seriesMapper;
    @Mock
    private ArticleSeriesItemMapper seriesItemMapper;
    /** Task 8 起 listPublishedByHot 优先读 Redis 榜（这两个 mock 未被 stub → 回退 MySQL，既有断言不变） */
    @Mock
    private HotRankService hotRankService;
    @Mock
    private HotRankProperties hotRankProperties;
    @Mock
    private CommonCache<Object> commonCache;
    @Mock
    private RecommendationBizService recommendationBizService;
    @Mock
    private RecommendProperties recommendProperties;

    private ArticleReadBizServiceImpl service;

    /**
     * Result.ok() 内部走 I18nUtils.from()，依赖 Spring 注入的静态 MessageSource。
     * 纯 Mockito 单测无 Spring 上下文，这里用反射注入 mock 使其可调用。
     */
    @BeforeAll
    static void initMessageSource() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mock(MessageSource.class));
    }

    @BeforeEach
    void setUp() {
        // 分页参数从请求上下文读取（TableSupport），设置模拟请求避免 NPE
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        service = spy(new ArticleReadBizServiceImpl(
                articleDao, contentDao, chapterDao, viewDao, tagDao, articleTagDao, articleStatsDao, userClient,
                hotWeightProperties, articleMapper, seriesMapper, seriesItemMapper,
                hotRankService, hotRankProperties, commonCache, recommendationBizService, recommendProperties));
    }

    /**
     * 无浏览记录时返回空列表
     */
    @Test
    void listHistory_empty_returnsEmptyList() {
        when(viewDao.listHistoryLogs(any(), any(Page.class))).thenReturn(Collections.emptyList());

        Result<List<ArticleInfoVo>> result = service.listHistory();

        assertTrue(result.getIsSuccess());
        assertTrue(result.getData().isEmpty());
    }

    /**
     * 同一文章多次浏览只保留最近一次，整体按最近浏览时间倒序，viewedAt 回填
     */
    @Test
    void listHistory_dedupOrdersByLatestView_withViewedAt() {
        LocalDateTime latestA1 = LocalDateTime.of(2026, 8, 10, 10, 0);
        LocalDateTime viewA2 = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime olderA1 = LocalDateTime.of(2026, 8, 10, 8, 0);

        // 倒序日志：a1(10:00) -> a2(09:00) -> a1(08:00)，去重后应保留 a1@10:00、a2@09:00
        when(viewDao.listHistoryLogs(any(), any(Page.class))).thenReturn(List.of(
                ArticleLog.builder().articleId("a1").viewAt(latestA1).build(),
                ArticleLog.builder().articleId("a2").viewAt(viewA2).build(),
                ArticleLog.builder().articleId("a1").viewAt(olderA1).build()));

        // 故意乱序返回，验证按日志顺序重排
        when(articleDao.listByIds(List.of("a1", "a2"))).thenReturn(List.of(
                Article.builder().id("a2").title("T2").authorId("author-9").summary("S2").build(),
                Article.builder().id("a1").title("T1").authorId("author-9").summary("S1").build()));

        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(List.of(
                ArticleStats.builder().articleId("a2").views(7L).likes(2L).comments(0L).favorites(0L).build()));
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        UserDTO author = new UserDTO();
        author.setId("author-9");
        author.setUsername("author-x");
        author.setAvatarUrl("http://avatar");
        Result<List<UserDTO>> userResult = new Result<>();
        userResult.setIsSuccess(true);
        userResult.setData(List.of(author));
        when(userClient.getUserDTOs(anyList())).thenReturn(userResult);

        Result<List<ArticleInfoVo>> result = service.listHistory();

        assertTrue(result.getIsSuccess());
        List<ArticleInfoVo> data = result.getData();
        // 重复浏览的文章只出现一次
        assertEquals(2, data.size());
        // 顺序：a1(最近) -> a2
        assertEquals("a1", data.get(0).getId());
        assertEquals("a2", data.get(1).getId());
        // viewedAt 取最近一次浏览时间
        assertEquals(latestA1, data.get(0).getViewedAt());
        assertEquals(viewA2, data.get(1).getViewedAt());
        // 统计补全（a2 有统计，a1 无）
        assertEquals(7L, data.get(1).getViewCount());
        assertNull(data.get(0).getViewCount());
        // 作者信息补全
        assertEquals("author-x", data.get(0).getAuthorName());
        assertEquals("author-x", data.get(1).getAuthorName());
    }

    /**
     * 浏览记录对应的文章已被删除时，跳过该条而非报错
     */
    @Test
    void listHistory_skipsMissingArticles() {
        when(viewDao.listHistoryLogs(any(), any(Page.class))).thenReturn(List.of(
                ArticleLog.builder().articleId("a1").viewAt(LocalDateTime.of(2026, 8, 10, 10, 0)).build(),
                ArticleLog.builder().articleId("gone").viewAt(LocalDateTime.of(2026, 8, 10, 9, 0)).build()));

        when(articleDao.listByIds(anyList())).thenReturn(List.of(
                Article.builder().id("a1").title("T1").authorId("author-9").build()));
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());
        Result<List<UserDTO>> userResult = new Result<>();
        userResult.setIsSuccess(true);
        userResult.setData(Collections.emptyList());
        when(userClient.getUserDTOs(anyList())).thenReturn(userResult);

        Result<List<ArticleInfoVo>> result = service.listHistory();

        assertTrue(result.getIsSuccess());
        assertEquals(1, result.getData().size());
        assertEquals("a1", result.getData().get(0).getId());
    }

    // ==================== getById / getBySlug 详情查询 ====================

    /**
     * getById：文章详情带统计（浏览/点赞/收藏）与作者信息补全
     * —— 回归保护：getById 必须与 getBySlug 一样注入统计数据（详情页依赖）
     */
    @Test
    void getById_returnsArticleWithStatsAndAuthor() {
        // 存量用例对齐：ddeec8f 起公开读取经 canView 门控（仅 published/作者本人），
        // 详情页回归测试按"公开已发布"建模补 status
        when(articleDao.getById("a1")).thenReturn(
                Article.builder().id("a1").title("T1").authorId("author-9").summary("S1")
                        .status("published").build());
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(List.of(
                ArticleStats.builder().articleId("a1").views(15L).likes(3L).comments(1L).favorites(2L).build()));
        when(articleTagDao.listTagNamesByArticleIds(anyList()))
                .thenReturn(Map.of("a1", List.of("Java", "Spring")));

        UserDTO author = new UserDTO();
        author.setId("author-9");
        author.setUsername("author-x");
        author.setAvatarUrl("http://avatar");
        Result<List<UserDTO>> userResult = new Result<>();
        userResult.setIsSuccess(true);
        userResult.setData(List.of(author));
        when(userClient.getUserDTOs(anyList())).thenReturn(userResult);

        Result<ArticleInfoVo> result = service.getById("a1");

        assertTrue(result.getIsSuccess());
        ArticleInfoVo vo = result.getData();
        assertEquals("a1", vo.getId());
        assertEquals(15L, vo.getViewCount());
        assertEquals(3L, vo.getLikeCount());
        assertEquals(2L, vo.getFavorites());
        assertEquals("author-x", vo.getAuthorName());
        assertEquals("http://avatar", vo.getAuthorAvatar());
        // 标签注入（列表/详情统一走 enrichWithTags）
        assertEquals(List.of("Java", "Spring"), vo.getTags());
    }

    /**
     * getBySlug：按 slug 查询同样带统计与作者信息（legacy 端点行为保持）
     */
    @Test
    void getBySlug_returnsArticleWithStatsAndAuthor() {
        when(articleDao.getBySlug("my-slug")).thenReturn(
                Article.builder().id("a1").title("T1").authorId("author-9").slug("my-slug")
                        .status("published").build());
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(List.of(
                ArticleStats.builder().articleId("a1").views(20L).likes(4L).comments(0L).favorites(5L).build()));
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        UserDTO author = new UserDTO();
        author.setId("author-9");
        author.setUsername("author-x");
        author.setAvatarUrl("http://avatar");
        Result<List<UserDTO>> userResult = new Result<>();
        userResult.setIsSuccess(true);
        userResult.setData(List.of(author));
        when(userClient.getUserDTOs(anyList())).thenReturn(userResult);

        Result<ArticleInfoVo> result = service.getBySlug("my-slug");

        assertTrue(result.getIsSuccess());
        ArticleInfoVo vo = result.getData();
        assertEquals("a1", vo.getId());
        assertEquals(20L, vo.getViewCount());
        assertEquals(4L, vo.getLikeCount());
        assertEquals(5L, vo.getFavorites());
        assertEquals("author-x", vo.getAuthorName());
        // 无标签关联时返回空列表而非 null
        assertEquals(Collections.emptyList(), vo.getTags());
    }

    // ==================== listPublished / listPublishedByHot 分页列表 ====================

    /**
     * listPublished：分页元数据正确（total=25, pageSize=10 → totalPages=3），enrich 正常注入
     */
    @Test
    void listPublished_paged_returnsPageVo() {
        when(articleDao.countPublished()).thenReturn(25L);
        when(articleDao.listPublishedByTime(1, 10)).thenReturn(
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(i -> Article.builder().id("a" + i).title("T" + i).authorId("author-9").build())
                        .collect(java.util.stream.Collectors.toList()));
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublished(1, 10);

        assertTrue(result.getIsSuccess());
        PageVo<List<ArticleInfoVo>> pageVo = result.getData();
        assertEquals(1, pageVo.getCurrentPage());
        assertEquals(10, pageVo.getPageSize());
        assertEquals(25L, pageVo.getTotal());
        assertEquals(3, pageVo.getTotalPages());
        assertEquals(10, pageVo.getData().size());
        // 未命中统计行时 viewCount 为 null（enrich 短路），列表不为空
        assertEquals("a0", pageVo.getData().get(0).getId());
        assertNull(pageVo.getData().get(0).getViewCount());
    }

    /**
     * listPublishedByHot：热度权重经配置类透传到 DAO 查询
     */
    @Test
    void listPublishedByHot_passesWeightsFromConfig() {
        when(articleDao.countPublished()).thenReturn(1L);
        doReturn(1L).when(hotWeightProperties).getViews();
        doReturn(2L).when(hotWeightProperties).getLikes();
        doReturn(5L).when(hotWeightProperties).getComments();
        doReturn(3L).when(hotWeightProperties).getFavorites();
        when(articleDao.listPublishedByHot(1, 10, 1L, 2L, 5L, 3L)).thenReturn(List.of(
                Article.builder().id("a1").title("T1").authorId("author-9").build()));
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByHot(1, 10, "7d");

        assertTrue(result.getIsSuccess());
        assertEquals(1, result.getData().getData().size());
        // 权重参数原样透传（隐式 by-value 断言，stub 不匹配即返回 null 导致 NPE）
        verify(articleDao).listPublishedByHot(1, 10, 1L, 2L, 5L, 3L);
    }

    /**
     * total == 0 时不查询列表，直接返回空分页（totalPages=0）
     */
    @Test
    void listPublishedByHot_empty_noDaoListCall() {
        when(articleDao.countPublished()).thenReturn(0L);

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByHot(3, 10, "7d");

        assertTrue(result.getIsSuccess());
        assertTrue(result.getData().getData().isEmpty());
        assertEquals(0, result.getData().getTotalPages());
        verify(articleDao, never()).listPublishedByHot(anyInt(), anyInt(), anyLong(), anyLong(), anyLong(), anyLong());
        verify(articleStatsDao, never()).listByArticleIds(anyList());
    }

    /**
     * 分页参数归一化：pageNum < 1 提升为 1，pageSize > 100 截断为 100
     */
    @Test
    void listPublished_normalizesPageParams() {
        when(articleDao.countPublished()).thenReturn(0L);

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublished(0, 500);

        assertTrue(result.getIsSuccess());
        verify(articleDao, never()).listPublishedByTime(anyInt(), anyInt());
        assertEquals(1, result.getData().getCurrentPage());
        assertEquals(100, result.getData().getPageSize());
    }

    // ==================== listPublishedByAuthor 按作者分页 ====================

    /**
     * listPublishedByAuthor：按作者过滤的分页元数据正确（total=15, pageSize=10 → totalPages=2）
     */
    @Test
    void listPublishedByAuthor_paged_returnsPageVo() {
        when(articleDao.countPublishedByAuthor("author-9")).thenReturn(15L);
        when(articleDao.listPublishedByAuthor("author-9", 1, 10)).thenReturn(
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(i -> Article.builder().id("a" + i).title("T" + i).authorId("author-9").build())
                        .collect(java.util.stream.Collectors.toList()));
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByAuthor("author-9", 1, 10);

        assertTrue(result.getIsSuccess());
        PageVo<List<ArticleInfoVo>> pageVo = result.getData();
        assertEquals(1, pageVo.getCurrentPage());
        assertEquals(10, pageVo.getPageSize());
        assertEquals(15L, pageVo.getTotal());
        assertEquals(2, pageVo.getTotalPages());
        assertEquals(10, pageVo.getData().size());
        assertEquals("a0", pageVo.getData().get(0).getId());
    }

    /**
     * 该作者无已发布文章：不查询列表，直接返回空分页（totalPages=0）
     */
    @Test
    void listPublishedByAuthor_empty_noDaoListCall() {
        when(articleDao.countPublishedByAuthor("author-9")).thenReturn(0L);

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByAuthor("author-9", 1, 10);

        assertTrue(result.getIsSuccess());
        assertTrue(result.getData().getData().isEmpty());
        assertEquals(0, result.getData().getTotalPages());
        verify(articleDao, never()).listPublishedByAuthor(anyString(), anyInt(), anyInt());
        verify(articleStatsDao, never()).listByArticleIds(anyList());
    }

    /**
     * 分页参数归一化：pageNum < 1 提升为 1，pageSize > 100 截断为 100
     */
    @Test
    void listPublishedByAuthor_normalizesPageParams() {
        when(articleDao.countPublishedByAuthor("author-9")).thenReturn(0L);

        Result<PageVo<List<ArticleInfoVo>>> result = service.listPublishedByAuthor("author-9", 0, 500);

        assertTrue(result.getIsSuccess());
        assertEquals(1, result.getData().getCurrentPage());
        assertEquals(100, result.getData().getPageSize());
    }

    // ==================== 专栏前台读（列表 / 详情分页 / 详情带专栏链接） ====================

    /**
     * getSeriesDetail：按 sort_order 分页返回专栏的已发布成员（SQL 层过滤草稿），
     * 专栏元数据 + 分页信息正确，offset 按 (page-1)*size 显式换算
     */
    @Test
    @DisplayName("getSeriesDetail should return published members paged by sort_order")
    void getSeriesDetailShouldFilterAndOrder() {
        ArticleSeries s = new ArticleSeries();
        s.setId("s1");
        s.setName("ES 实战");
        when(seriesMapper.selectById("s1")).thenReturn(s);
        Article pub = new Article();
        pub.setId("a1");
        pub.setStatus("published");
        Article draft = new Article();
        draft.setId("a2");
        draft.setStatus("draft");
        // 草稿不返回（SQL 层过滤），返回行仅 a1
        when(articleMapper.selectSeriesMemberPage(eq("s1"), eq(0), eq(10))).thenReturn(List.of(pub));
        when(articleMapper.selectSeriesMemberCount("s1")).thenReturn(1L);
        when(userClient.getUserDTOs(any())).thenReturn(null); // 跳过作者 enrich

        Result<SeriesDetailVo> res = service.getSeriesDetail("s1", 1, 10);

        assertTrue(res.getIsSuccess());
        assertEquals("ES 实战", res.getData().getName());
        assertEquals(1, res.getData().getPageNum());
        assertEquals(10, res.getData().getPageSize());
        assertEquals(1L, res.getData().getTotal());
        assertEquals(1, res.getData().getTotalPages());
        assertEquals(1, res.getData().getArticles().size());
        assertEquals("a1", res.getData().getArticles().get(0).getId());
        // offset 显式换算：page=1 → offset 0
        verify(articleMapper).selectSeriesMemberPage("s1", 0, 10);
    }

    /**
     * listSeriesRead：每栏附已发布成员数；SQL 统计行缺失的专栏按 0 兜底
     */
    @Test
    @DisplayName("listSeriesRead should attach published article count per series")
    void listSeriesReadShouldAttachCount() {
        ArticleSeries s1 = new ArticleSeries();
        s1.setId("s1");
        s1.setName("ES 实战");
        ArticleSeries s2 = new ArticleSeries();
        s2.setId("s2");
        s2.setName("MySQL 调优");
        when(seriesMapper.selectList(any())).thenReturn(List.of(s1, s2));

        SeriesMemberCountVo c1 = new SeriesMemberCountVo();
        c1.setSeriesId("s1");
        c1.setArticleCount(2L);
        SeriesMemberCountVo c2 = new SeriesMemberCountVo();
        c2.setSeriesId("s2");
        c2.setArticleCount(0L);
        when(articleMapper.selectPublishedCountGroupBySeries()).thenReturn(List.of(c1, c2));

        Result<List<SeriesReadVo>> res = service.listSeriesRead();

        assertTrue(res.getIsSuccess());
        assertEquals(2, res.getData().size());
        assertEquals("s1", res.getData().get(0).getId());
        assertEquals("ES 实战", res.getData().get(0).getName());
        assertEquals(2L, res.getData().get(0).getArticleCount());
        assertEquals("s2", res.getData().get(1).getId());
        assertEquals("MySQL 调优", res.getData().get(1).getName());
        assertEquals(0L, res.getData().get(1).getArticleCount());
    }

    /**
     * randomSeriesRead（首页随机专栏）：只含有已发布文章的专栏参与随机，上限 8；
     * 空专栏（统计行缺失 → 0 兜底）必须被排除
     */
    @Test
    @DisplayName("randomSeriesRead should exclude empty series and cap at 8")
    void randomSeriesReadShouldExcludeEmptySeriesAndCapAtEight() {
        List<ArticleSeries> all = List.of(
                seriesWithId("s1"), seriesWithId("s2"), seriesWithId("s3"), seriesWithId("s4"),
                seriesWithId("s5"), seriesWithId("s6"), seriesWithId("s7"), seriesWithId("s8"),
                seriesWithId("s9"), seriesWithId("s10"));
        when(seriesMapper.selectList(any())).thenReturn(all);
        // s1~s9 有文章，s10 统计行缺失（等同空专栏，0 兜底）
        when(articleMapper.selectPublishedCountGroupBySeries()).thenReturn(
                List.of(countVo("s1", 1), countVo("s2", 2), countVo("s3", 3), countVo("s4", 4),
                        countVo("s5", 5), countVo("s6", 6), countVo("s7", 7), countVo("s8", 8),
                        countVo("s9", 9)));

        Result<List<SeriesReadVo>> res = service.randomSeriesRead();

        assertTrue(res.getIsSuccess());
        List<SeriesReadVo> data = res.getData();
        assertEquals(8, data.size());
        // 随机顺序不可断言，断言集合性质：不含空专栏、无重复、全量取自有效集
        Set<String> ids = data.stream().map(SeriesReadVo::getId).collect(Collectors.toSet());
        assertEquals(8, ids.size());
        assertFalse(ids.contains("s10"));
        assertTrue(data.stream().allMatch(vo -> vo.getArticleCount() > 0));
    }

    /**
     * randomSeriesRead：有效专栏不足 8 时全量返回（不做补位）
     */
    @Test
    @DisplayName("randomSeriesRead should return all non-empty series when fewer than 8")
    void randomSeriesReadShouldReturnAllWhenFewerThanLimit() {
        when(seriesMapper.selectList(any())).thenReturn(
                List.of(seriesWithId("s1"), seriesWithId("s2"), seriesWithId("s3"), seriesWithId("s-empty")));
        when(articleMapper.selectPublishedCountGroupBySeries()).thenReturn(
                List.of(countVo("s1", 2), countVo("s2", 1), countVo("s3", 5)));

        Result<List<SeriesReadVo>> res = service.randomSeriesRead();

        assertTrue(res.getIsSuccess());
        assertEquals(3, res.getData().size());
        assertTrue(res.getData().stream().allMatch(vo -> !"s-empty".equals(vo.getId())));
    }

    /**
     * randomSeriesRead：没有任何专栏含已发布文章时返回空列表（前端整块不渲染）
     */
    @Test
    @DisplayName("randomSeriesRead should return empty list when no series has articles")
    void randomSeriesReadShouldReturnEmptyWhenNoContent() {
        when(seriesMapper.selectList(any())).thenReturn(List.of(seriesWithId("s1"), seriesWithId("s2")));
        when(articleMapper.selectPublishedCountGroupBySeries())
                .thenReturn(List.of(countVo("s1", 0), countVo("s2", 0)));

        Result<List<SeriesReadVo>> res = service.randomSeriesRead();

        assertTrue(res.getIsSuccess());
        assertTrue(res.getData().isEmpty());
    }

    private ArticleSeries seriesWithId(String id) {
        ArticleSeries s = new ArticleSeries();
        s.setId(id);
        s.setName(id);
        return s;
    }

    private SeriesMemberCountVo countVo(String seriesId, long count) {
        SeriesMemberCountVo vo = new SeriesMemberCountVo();
        vo.setSeriesId(seriesId);
        vo.setArticleCount(count);
        return vo;
    }

    /**
     * getById：详情带 seriesList（每栏名称/封面/文章内 sort_order/栏内已发布总数），
     * 无专栏关联时 seriesList 为 null（前端判空隐藏）
     */
    @Test
    @DisplayName("getById should enrich seriesList links")
    void getByIdShouldEnrichSeries() {
        when(articleDao.getById("a1")).thenReturn(
                Article.builder().id("a1").title("T1").authorId("author-9").status("published").build());
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(Collections.emptyList());
        when(articleTagDao.listTagNamesByArticleIds(anyList())).thenReturn(Collections.emptyMap());
        UserDTO author = new UserDTO();
        author.setId("author-9");
        author.setUsername("author-x");
        Result<List<UserDTO>> userResult = new Result<>();
        userResult.setIsSuccess(true);
        userResult.setData(List.of(author));
        when(userClient.getUserDTOs(anyList())).thenReturn(userResult);

        // a1 挂在 s1(sort 2)、s2(sort 1) 两个专栏
        when(seriesItemMapper.selectList(any())).thenReturn(List.of(
                ArticleSeriesItem.builder().id("i1").seriesId("s1").articleId("a1").sortOrder(2).build(),
                ArticleSeriesItem.builder().id("i2").seriesId("s2").articleId("a1").sortOrder(1).build()));
        ArticleSeries s1 = new ArticleSeries();
        s1.setId("s1");
        s1.setName("ES 实战");
        s1.setCoverUrl("http://cover/1");
        ArticleSeries s2 = new ArticleSeries();
        s2.setId("s2");
        s2.setName("MySQL 调优");
        s2.setCoverUrl("http://cover/2");
        when(seriesMapper.selectBatchIds(any())).thenReturn(List.of(s1, s2));
        SeriesMemberCountVo c1 = new SeriesMemberCountVo();
        c1.setSeriesId("s1");
        c1.setArticleCount(3L);
        SeriesMemberCountVo c2 = new SeriesMemberCountVo();
        c2.setSeriesId("s2");
        c2.setArticleCount(1L);
        when(articleMapper.selectPublishedCountGroupBySeries()).thenReturn(List.of(c1, c2));

        Result<ArticleInfoVo> res = service.getById("a1");

        assertTrue(res.getIsSuccess());
        List<SeriesArticleLinkVo> links = res.getData().getSeriesList();
        assertEquals(2, links.size());
        // 稳定展示序：按 seriesId 升序
        assertEquals("s1", links.get(0).getSeriesId());
        assertEquals("ES 实战", links.get(0).getName());
        assertEquals("http://cover/1", links.get(0).getCoverUrl());
        assertEquals(2, links.get(0).getSortOrder());
        assertEquals(3L, links.get(0).getTotalCount());
        assertEquals("s2", links.get(1).getSeriesId());
        assertEquals("MySQL 调优", links.get(1).getName());
        assertEquals("http://cover/2", links.get(1).getCoverUrl());
        assertEquals(1, links.get(1).getSortOrder());
        assertEquals(1L, links.get(1).getTotalCount());
    }

    /**
     * getSeriesDetail 分页钳制：page<1 归 1，size>100 归 100（公开端点防超大 LIMIT）
     */
    @Test
    @DisplayName("getSeriesDetail should clamp page below 1 and oversized size")
    void getSeriesDetailShouldClampPageAndSize() {
        ArticleSeries s = new ArticleSeries();
        s.setId("s1");
        s.setName("ES 实战");
        when(seriesMapper.selectById("s1")).thenReturn(s);
        when(articleMapper.selectSeriesMemberPage(eq("s1"), eq(0), eq(100))).thenReturn(Collections.emptyList());
        when(articleMapper.selectSeriesMemberCount("s1")).thenReturn(0L);

        Result<SeriesDetailVo> res = service.getSeriesDetail("s1", -5, 999);

        assertTrue(res.getIsSuccess());
        verify(articleMapper).selectSeriesMemberPage("s1", 0, 100);
        assertEquals(1, res.getData().getPageNum());
        assertEquals(100, res.getData().getPageSize());
        assertEquals(0L, res.getData().getTotal());
    }

    /**
     * getSeriesDetail 深分页钳制：page>10000 归 10000（offset=(10000-1)*100≈1e6，钳制后 int 无溢出）
     */
    @Test
    @DisplayName("getSeriesDetail should clamp deep page to 10000")
    void getSeriesDetailShouldClampDeepPage() {
        ArticleSeries s = new ArticleSeries();
        s.setId("s1");
        s.setName("ES 实战");
        when(seriesMapper.selectById("s1")).thenReturn(s);
        when(articleMapper.selectSeriesMemberPage(eq("s1"), eq(999900), eq(100))).thenReturn(Collections.emptyList());
        when(articleMapper.selectSeriesMemberCount("s1")).thenReturn(0L);

        Result<SeriesDetailVo> res = service.getSeriesDetail("s1", 20000, 200);

        assertTrue(res.getIsSuccess());
        verify(articleMapper).selectSeriesMemberPage("s1", 999900, 100);
        assertEquals(10000, res.getData().getPageNum());
        assertEquals(100, res.getData().getPageSize());
    }

    /**
     * getSeriesDetail：专栏归属作者 enrich（author_id → 作者名/头像，随详情返回）
     */
    @Test
    @DisplayName("getSeriesDetail should enrich series author name and avatar")
    void getSeriesDetailShouldEnrichSeriesAuthor() {
        ArticleSeries s = new ArticleSeries();
        s.setId("s1");
        s.setName("ES 实战");
        s.setAuthorId("author-9");
        when(seriesMapper.selectById("s1")).thenReturn(s);
        when(articleMapper.selectSeriesMemberPage(eq("s1"), eq(0), eq(10))).thenReturn(Collections.emptyList());
        when(articleMapper.selectSeriesMemberCount("s1")).thenReturn(0L);
        UserDTO author = new UserDTO();
        author.setId("author-9");
        author.setUsername("author-x");
        author.setAvatarUrl("http://avatar/9");
        Result<UserDTO> userResult = new Result<>();
        userResult.setIsSuccess(true);
        userResult.setData(author);
        when(userClient.getUserDTO("author-9")).thenReturn(userResult);

        Result<SeriesDetailVo> res = service.getSeriesDetail("s1", 1, 10);

        assertTrue(res.getIsSuccess());
        assertEquals("author-9", res.getData().getAuthorId());
        assertEquals("author-x", res.getData().getAuthorName());
        assertEquals("http://avatar/9", res.getData().getAuthorAvatar());
    }

    // ============ 猜你喜欢 recommend ============

    /** 设置当前登录用户（x-user-id 头；getUserId() 经 RequestContextHolder 读请求头） */
    private void setLoginUser(String userId) {
        MockHttpServletRequest req = (MockHttpServletRequest) ((ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes()).getRequest();
        req.addHeader("X-User-Id", userId);
    }

    private Article published(String id) {
        return Article.builder().id(id).status("published").deletedAt(null).build();
    }

    /** 引擎冷启动（画像空）→ 回退热榜链路，不写缓存、不报错 */
    @Test
    void recommend_coldStart_fallsBackToHotNoCache() {
        setLoginUser("u1");
        when(recommendationBizService.recommendArticleIds("u1", false)).thenReturn(List.of());

        Result<PageVo<List<ArticleInfoVo>>> r = service.recommend(1, 10);

        assertTrue(r.getIsSuccess());
        assertTrue(r.getData().getData().isEmpty());
        verify(recommendationBizService).recommendArticleIds("u1", false);
        verify(commonCache, never()).put(eq("rec:result:user:u1"), anyString(), anyLong());
    }

    /** 结果缓存命中 → 直接用缓存 id 列表组装分页，不再调引擎 */
    @Test
    void recommend_cacheHit_skipsEngine() {
        setLoginUser("u1");
        when(commonCache.getString("rec:result:user:u1")).thenReturn("a1,a2");
        when(articleDao.listByIds(List.of("a1", "a2"))).thenReturn(List.of(published("a1"), published("a2")));
        when(articleStatsDao.listByArticleIds(List.of("a1", "a2"))).thenReturn(List.of());
        when(articleTagDao.listTagNamesByArticleIds(List.of("a1", "a2"))).thenReturn(Map.of());

        Result<PageVo<List<ArticleInfoVo>>> r = service.recommend(1, 10);

        assertTrue(r.getIsSuccess());
        assertEquals(2, r.getData().getData().size());
        verifyNoInteractions(recommendationBizService);
    }

    /** 引擎有结果 → 写结果缓存后返回分页 */
    @Test
    void recommend_engineHit_writesCache() {
        setLoginUser("u1");
        when(recommendProperties.getResultCacheTtlSeconds()).thenReturn(600L);
        when(recommendationBizService.recommendArticleIds("u1", false)).thenReturn(List.of("a1"));
        when(articleDao.listByIds(List.of("a1"))).thenReturn(List.of(published("a1")));
        when(articleStatsDao.listByArticleIds(List.of("a1"))).thenReturn(List.of());
        when(articleTagDao.listTagNamesByArticleIds(List.of("a1"))).thenReturn(Map.of());

        Result<PageVo<List<ArticleInfoVo>>> r = service.recommend(1, 10);

        assertTrue(r.getIsSuccess());
        assertEquals(1, r.getData().getData().size());
        verify(commonCache).put("rec:result:user:u1", "a1", 600L);
    }

    /** 极端 pageNum × pageSize 溢出：from 用 long 计算，优雅落到空页而非 subList 抛异常 */
    @Test
    void recommend_overflowingPageNum_returnsEmptyNoThrow() {
        setLoginUser("u1");
        when(commonCache.getString("rec:result:user:u1")).thenReturn("a1,a2");
        when(articleDao.listByIds(anyList())).thenReturn(List.of(published("a1"), published("a2")));

        Result<PageVo<List<ArticleInfoVo>>> r = service.recommend(1073741825, 10);

        assertTrue(r.getIsSuccess());
        assertTrue(r.getData().getData().isEmpty());
    }

    /** 深页越界（第 2 页超出仅 2 条的推荐列表）：返回空页而非 Result.ok(null) */
    @Test
    void recommend_pageBeyondRankedList_returnsEmptyPage() {
        setLoginUser("u1");
        when(commonCache.getString("rec:result:user:u1")).thenReturn("a1,a2");
        when(articleDao.listByIds(anyList())).thenReturn(List.of(published("a1"), published("a2")));

        Result<PageVo<List<ArticleInfoVo>>> r = service.recommend(2, 10);

        assertTrue(r.getIsSuccess());
        assertTrue(r.getData().getData().isEmpty());
    }
}
