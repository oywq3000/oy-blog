package com.oyproj.service.impl;

import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.service.ArticleIndexControllerProvider;
import com.oyproj.service.ArticleIndexSeedService;
import com.oyproj.service.ArticleIndexSeedService.SeedResult;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleIndexSeedService 播种")
class ArticleIndexSeedServiceImplTest {

    @Mock private ArticleIndexControllerProvider controllerProvider;
    @Mock private ObjectProvider<ArticleIndexControllerProvider> snapshotProvider;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

    private ArticleIndexSeedServiceImpl service;

    private MockedStatic<I18nUtils> i18nMock;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger seedLogger;

    @BeforeEach
    void setUp() {
        // Result.ok(data) 依赖 I18nUtils 静态 messageSource（单测无 Spring 上下文，否则 NPE）
        i18nMock = Mockito.mockStatic(I18nUtils.class);
        i18nMock.when(() -> I18nUtils.from(any(ResultCode.class))).thenReturn("success");
        // 生产里是 ObjectProvider 懒解析（避免 controller↔本 bean 构造器成环，见实现类 javadoc）
        when(snapshotProvider.getObject()).thenReturn(controllerProvider);
        service = new ArticleIndexSeedServiceImpl(snapshotProvider, kafkaTemplate);

        seedLogger = (Logger) LoggerFactory.getLogger(ArticleIndexSeedServiceImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        seedLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (seedLogger != null && logAppender != null) {
            seedLogger.detachAppender(logAppender);
            logAppender.stop();
        }
        if (i18nMock != null) {
            i18nMock.close();
        }
    }

    /** 本测试期间实现类打出的 WARN 文案 */
    private List<String> warnings() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static ArticleIndexMessage msg(String id) {
        ArticleIndexMessage m = new ArticleIndexMessage();
        m.setArticleId(id);
        return m;
    }

    private void stubPage(int pageNum, int total, List<ArticleIndexMessage> data) {
        PageVo<List<ArticleIndexMessage>> page =
                new PageVo<>(pageNum, 100, (long) total, (int) Math.ceil(total / 100.0), data);
        // Result.ok 内部求值会命中 mockStatic 的 I18nUtils → 在 stubbing 窗口内求值会 UnfinishedStubbing，
        // 故先求值再用 doReturn 形式（仓库既有套路，见 ArticleControllerTest:178）
        Result<PageVo<List<ArticleIndexMessage>>> snapshotResult = Result.ok(page);
        doReturn(snapshotResult).when(controllerProvider).snapshot(pageNum, 100);
    }

    @Test
    void seed_sendsEveryArticle_withArticleIdAsKey() {
        stubPage(0, 2, List.of(msg("A1"), msg("A2")));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        SeedResult result = service.seedIndexTopic();

        assertEquals(2, result.total());
        assertEquals(2, result.succeeded());
        assertTrue(result.failedArticleIds().isEmpty());
        verify(kafkaTemplate).send(eq("article.index"), eq("A1"), any());
        verify(kafkaTemplate).send(eq("article.index"), eq("A2"), any());
    }

    @Test
    void seed_iteratesAllPages() {
        stubPage(0, 150, List.of(msg("A1")));
        stubPage(1, 150, List.of(msg("A2")));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        SeedResult result = service.seedIndexTopic();

        assertEquals(2, result.total());
        verify(controllerProvider).snapshot(0, 100);
        verify(controllerProvider).snapshot(1, 100);
    }

    @Test
    void seed_reportsFailedArticleIds_ratherThanPretendingSuccess() {
        stubPage(0, 2, List.of(msg("A1"), msg("A2")));
        when(kafkaTemplate.send(eq("article.index"), eq("A1"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("article.index"), eq("A2"), any()))
                .thenThrow(new RuntimeException("broker down"));

        SeedResult result = service.seedIndexTopic();

        assertEquals(2, result.total());
        assertEquals(1, result.succeeded());
        assertEquals(List.of("A2"), result.failedArticleIds());
    }

    @Test
    void seed_emptyArticleSet_returnsZeroes() {
        stubPage(0, 0, List.of());

        SeedResult result = service.seedIndexTopic();

        assertEquals(0, result.total());
        assertEquals(0, result.succeeded());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void seed_totalChangingBetweenPages_warnsAboutPagingDrift() {
        // 第一页 150 篇；跑到第二页时库里的总数变成 149（期间有人删了一篇）→ offset 分页已漂移，
        // 可能有文章被整篇跳过。跳过既不在 succeeded 也不在 failedArticleIds 里，只能靠这个诊断。
        stubPage(0, 150, List.of(msg("A1")));
        stubPage(1, 149, List.of(msg("A2")));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.seedIndexTopic();

        assertTrue(warnings().stream().anyMatch(m -> m.contains("文章总数发生变化")),
                () -> "分页期间总数变化必须告警，实际 WARN 日志: " + warnings());
    }

    @Test
    void seed_totalStableAcrossPages_noDriftWarning() {
        stubPage(0, 150, List.of(msg("A1")));
        stubPage(1, 150, List.of(msg("A2")));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.seedIndexTopic();

        assertTrue(warnings().stream().noneMatch(m -> m.contains("文章总数发生变化")),
                () -> "总数未变时不应告警（避免狼来了），实际 WARN 日志: " + warnings());
    }
}
