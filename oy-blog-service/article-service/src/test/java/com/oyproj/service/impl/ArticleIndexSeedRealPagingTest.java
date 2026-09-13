package com.oyproj.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.controller.ArticleIndexController;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.dto.impl.ArticleDaoImpl;
import com.oyproj.mapper.ArticleMapper;
import com.oyproj.mapper.ArticleTagMapper;
import com.oyproj.mapper.TagMapper;
import com.oyproj.service.ArticleIndexControllerProvider;
import com.oyproj.service.ArticleIndexSeedService.SeedResult;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 播种的<b>真实分页</b>测试（H2 内存库 + 真实 {@link ArticleIndexController#getIndexSnapshot} +
 * 与生产 {@code MybatisPlusConfig} 一致的 MP 分页拦截器）。
 *
 * <p>存在理由（Task 6 端到端验证发现的 Critical）：播种循环靠"某页为空"终止，但
 * <ul>
 *   <li>{@code getIndexSnapshot} 的 {@code pageNum} 是 0-based，而 MP 的 {@code Page} 是 1-based
 *       （{@code IPage.offset()}: {@code current <= 1 → 0}）；</li>
 *   <li>拦截器 {@code overflow=true}（生产配置）在 {@code current > pages} 时
 *       {@code handlerOverflow()} 会 {@code setCurrent(1)}（<b>回到第一页</b>）。</li>
 * </ul>
 * 于是"页号"根本不能靠"空页"探测终点，循环永不结束（实测 5 分钟灌了 24040 条）。
 * 这类缺陷<b>只有驱动真实分页才能看见</b>——纯 Mockito 测试里 provider 是 mock，页永远"取之不尽"。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("播种 - 真实分页（H2 + 真实快照/MP 拦截器）")
class ArticleIndexSeedRealPagingTest {

    private static final int PUBLISHED = 150;

    private static JdbcTemplate jdbc;
    private static SqlSessionFactory sqlSessionFactory;

    private ArticleIndexController controller;
    private ArticleIndexSeedServiceImpl service;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private MockedStatic<I18nUtils> i18nMock;

    @BeforeAll
    static void setUpDb() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:seed_real_paging_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE article (
                    id VARCHAR(64) PRIMARY KEY,
                    title VARCHAR(255),
                    author_id VARCHAR(64),
                    status VARCHAR(32),
                    summary VARCHAR(512),
                    visibility VARCHAR(32),
                    is_top INT,
                    slug VARCHAR(255),
                    cover_url VARCHAR(512),
                    language VARCHAR(16),
                    meta_title VARCHAR(255),
                    meta_description VARCHAR(512),
                    canonical_url VARCHAR(512),
                    source_url VARCHAR(512),
                    source_type VARCHAR(32),
                    reading_time_minutes INT,
                    word_count_total INT,
                    allow_comment INT,
                    review_status VARCHAR(32),
                    review_reason VARCHAR(512),
                    scheduled_publish_at TIMESTAMP,
                    featured_at TIMESTAMP,
                    publish_at TIMESTAMP,
                    update_at TIMESTAMP,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    deleted_at TIMESTAMP
                )
                """);

        // 150 篇已发布（> 页大小 100 → 真实两页）。
        // created_at 必须**逐行不同**：排序键有并列时 SQL 的页序不稳定，分页断言会变成偶发绿。
        for (int i = 1; i <= PUBLISHED; i++) {
            String id = String.format("P%03d", i);
            String createdAt = LocalDateTime.of(2026, 7, 1, 0, 0).plusSeconds(i)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            jdbc.update("INSERT INTO article (id, title, status, author_id, publish_at, created_at) "
                            + "VALUES (?, ?, 'published', 'U1', ?, ?)",
                    id, "文章" + i, createdAt, createdAt);
        }
        // 不参与播种的两篇
        jdbc.update("INSERT INTO article (id, title, status, author_id, created_at) VALUES ('DRAFT', '草稿', 'draft', 'U1', '2026-07-02 00:00:00')");
        jdbc.update("INSERT INTO article (id, title, status, author_id, created_at, deleted_at) VALUES ('DELETED', '已删', 'published', 'U1', '2026-07-03 00:00:00', '2026-07-04 00:00:00')");

        // 与生产 MybatisPlusConfig 完全一致：PaginationInnerInterceptor(MYSQL) + overflow=true
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setOverflow(true);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(ds);
        factoryBean.setPlugins(new Interceptor[]{interceptor});
        factoryBean.afterPropertiesSet();
        sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().addMapper(ArticleMapper.class);
    }

    @AfterAll
    static void tearDownDb() {
        jdbc.execute("DROP TABLE article");
    }

    @BeforeEach
    void setUp() throws Exception {
        i18nMock = Mockito.mockStatic(I18nUtils.class);
        i18nMock.when(() -> I18nUtils.from(any(ResultCode.class))).thenReturn("success");

        ArticleDaoImpl articleDao = new ArticleDaoImpl();
        Field field = ArticleDaoImpl.class.getSuperclass().getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(articleDao, sqlSessionFactory.openSession().getMapper(ArticleMapper.class));

        // 真实 controller（articleDao 走真实 H2 + 真实 MP 分页）；其余依赖与本次无关，给 mock
        controller = new ArticleIndexController(articleDao,
                mock(ArticleContentDao.class), mock(ArticleStatsDao.class), mock(UserClient.class),
                mock(ArticleTagMapper.class), mock(TagMapper.class), mock(com.oyproj.service.ArticleIndexSeedService.class));

        // 真实 provider：ObjectProvider.getObject() 直接给真实 controller（生产里由 Spring 提供）
        ObjectProvider<ArticleIndexControllerProvider> provider = new ObjectProvider<>() {
            @Override
            public ArticleIndexControllerProvider getObject() {
                return controller;
            }
        };
        service = new ArticleIndexSeedServiceImpl(provider, kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        i18nMock.close();
    }

    private Set<String> idsOf(int pageNum, int pageSize) {
        PageVo<List<ArticleIndexMessage>> page = controller.getIndexSnapshot(pageNum, pageSize).getData();
        return page.getData().stream().map(ArticleIndexMessage::getArticleId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("真实分页：pageNum=0 与 pageNum=1 是同一页（0-based 调用 + MP 1-based 的坑）")
    void realPaging_pageNumZeroAndOneAreTheSamePage() {
        assertEquals(idsOf(0, 100), idsOf(1, 100),
                "pageNum=0 与 1 应命中同一页（IPage.offset(): current<=1 → 0）—— 这正是播种死循环的根因");
    }

    @Test
    @DisplayName("真实分页：单页时继续翻页永远拿到同一页（overflow=true 回到第一页）")
    void realPaging_singlePage_neverDrainsWhenPagingOn() {
        // pageSize 大于总数 → totalPages == 1，正是生产语料（24 篇）的形态
        PageVo<List<ArticleIndexMessage>> first = controller.getIndexSnapshot(1, 1000).getData();

        assertEquals(1, first.getTotalPages(), "单页语料的 totalPages 应为 1");
        assertEquals(PUBLISHED, first.getData().size());
        assertEquals(idsOf(1, 1000), idsOf(2, 1000),
                "totalPages=1 时继续翻页会因 overflow 回到第一页 —— 空页永远不会出现，只能靠页数上界终止");
    }

    @Test
    @DisplayName("播种端到端：150 篇全部被播种、无重复、能终止")
    void seed_realPaging_sendsEveryPublishedArticleExactlyOnceAndTerminates() {
        Set<String> sent = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();
        when(kafkaTemplate.send(any(), any(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(1);
            if (!sent.add(key)) {
                duplicates.add(key);
            }
            return CompletableFuture.completedFuture(null);
        });

        SeedResult result = service.seedIndexTopic();

        Set<String> expected = new LinkedHashSet<>();
        for (int i = 1; i <= PUBLISHED; i++) {
            expected.add(String.format("P%03d", i));
        }
        assertEquals(expected, sent, "应把 150 篇已发布文章全部播种（草稿/软删除除外）");
        assertTrue(duplicates.isEmpty(), () -> "不应重复播种，实际重复: " + duplicates);
        assertEquals(expected.size(), result.total(), "SeedResult.total = 读到的记录条数（应等于 150）");
        assertEquals(expected.size(), result.succeeded());
        assertTrue(result.failedArticleIds().isEmpty());
    }

    @Test
    @DisplayName("诊断：分页期间总数变化（真实 SQL）")
    void realPaging_totalIsReportedPerPage() {
        // 供上一条用例之外的交叉核对：每页都会重新 count
        assertEquals(150L, controller.getIndexSnapshot(0, 100).getData().getTotal());
        assertEquals(150L, controller.getIndexSnapshot(1, 100).getData().getTotal());
    }
}
