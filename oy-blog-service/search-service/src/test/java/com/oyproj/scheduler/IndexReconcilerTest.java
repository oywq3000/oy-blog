package com.oyproj.scheduler;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.util.ObjectBuilder;
import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.api.article.client.ArticleIndexClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.ArticleDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD: IndexReconciler publishAt 补全测试
 * <p>
 * Red → Green → Refactor
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IndexReconciler - 对账链路 publishAt 传递")
class IndexReconcilerTest {

    @Mock
    private ArticleIndexClient articleIndexClient;

    @Mock
    private ArticleSearchRepository articleSearchRepository;

    @Mock
    private ElasticsearchClient esClient;

    @InjectMocks
    private IndexReconciler indexReconciler;

    private MockedStatic<I18nUtils> i18nMock;

    @BeforeEach
    void setUp() {
        // Mock static I18nUtils to avoid Spring context dependency
        i18nMock = Mockito.mockStatic(I18nUtils.class);
        i18nMock.when(() -> I18nUtils.from(any(ResultCode.class))).thenReturn("success");
    }

    @AfterEach
    void tearDown() {
        if (i18nMock != null) {
            i18nMock.close();
        }
    }

    /**
     * Helper: mock ES scroll 查询返回空 hits，跳过僵尸文档清理分支。
     */
    @SuppressWarnings("unchecked")
    private void mockEmptyEsSearch() throws Exception {
        HitsMetadata<ArticleDocument> hitsMeta = HitsMetadata.of(h -> h
                .hits(List.of())
                .total(TotalHits.of(t -> t.value(0L).relation(TotalHitsRelation.Eq)))
        );
        SearchResponse<ArticleDocument> response = SearchResponse.of(r -> r
                .hits(hitsMeta)
                .took(1)
                .timedOut(false)
                .shards(sh -> sh.total(1).successful(1).failed(0))
        );
        when(esClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                eq(ArticleDocument.class)
        )).thenReturn(response);
    }

    @Test
    @DisplayName("should carry publishAt into ES document during reconcile")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldCarryPublishAtDuringReconcile() throws Exception {
        // Arrange
        LocalDateTime publishAt = LocalDateTime.of(2026, 8, 1, 9, 30);
        ArticleIndexMessage message = new ArticleIndexMessage();
        message.setOperation(MQOperation.CREATE);
        message.setArticleId("a1");
        message.setSlug("hello-world");
        message.setTitle("Hello World");
        message.setSummary("summary");
        message.setContentMd("content");
        message.setStatus("published");
        message.setCreatedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        message.setPublishAt(publishAt);
        message.setTags(List.of("Java"));

        PageVo<List<ArticleIndexMessage>> pageVo =
                new PageVo<>(1, 100, 1L, 1, List.of(message));
        // doReturn 风格：参数在 stubbing 窗口外求值，避免 Result.ok 内部调用静态 mock I18nUtils 触发 UnfinishedStubbing
        Result<PageVo<List<ArticleIndexMessage>>> snapshotResult = Result.ok(pageVo);
        doReturn(snapshotResult).when(articleIndexClient).getIndexSnapshot(1, 100);
        mockEmptyEsSearch();

        ArgumentCaptor<List<ArticleDocument>> captor = ArgumentCaptor.forClass((Class) List.class);

        // Act
        indexReconciler.reconcile();

        // Assert
        verify(articleSearchRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(publishAt, captor.getValue().get(0).getPublishAt());
    }

    /**
     * Helper: mock ES scroll 查询返回给定 id 的文档，用于验证僵尸文档清理分支。
     * lenient：修复后快照失败路径不再查 ES，stub 未被使用不应判失败。
     */
    @SuppressWarnings("unchecked")
    private void mockEsSearchWithIds(String... ids) throws Exception {
        List<Hit<ArticleDocument>> hits = List.of(ids).stream()
                .map(id -> Hit.<ArticleDocument>of(h -> h.index("articles").id(id)))
                .toList();
        HitsMetadata<ArticleDocument> hitsMeta = HitsMetadata.of(h -> h
                .hits(hits)
                .total(TotalHits.of(t -> t.value((long) ids.length).relation(TotalHitsRelation.Eq)))
        );
        SearchResponse<ArticleDocument> response = SearchResponse.of(r -> r
                .hits(hitsMeta)
                .took(1)
                .timedOut(false)
                .shards(sh -> sh.total(1).successful(1).failed(0))
        );
        Mockito.lenient().when(esClient.search(
                ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                eq(ArticleDocument.class)
        )).thenReturn(response);
    }

    /** 构造一页快照（含单篇文章 a1） */
    private PageVo<List<ArticleIndexMessage>> snapshotPage(String articleId, int currentPage, int totalPages) {
        ArticleIndexMessage message = new ArticleIndexMessage();
        message.setOperation(MQOperation.CREATE);
        message.setArticleId(articleId);
        message.setTitle("T-" + articleId);
        message.setStatus("published");
        return new PageVo<>(currentPage, 100, (long) totalPages, totalPages, List.of(message));
    }

    // ============ 快照拉取失败时不得清理僵尸文档（防误删整个索引） ============

    @Test
    @DisplayName("首页快照拉取失败时不得删除任何 ES 文档")
    void shouldNotDeleteAnyDocumentWhenFirstSnapshotPageFails() throws Exception {
        // Arrange: article-service 不可用 → Feign fallback 返回 null（未 stub，Mockito 默认返回 null）
        // ES 里有存量文档，若对账器用空集当"权威集合"就会把它们全删光
        mockEsSearchWithIds("a1", "a2", "a3");

        // Act
        indexReconciler.reconcile();

        // Assert
        verify(articleSearchRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("翻页中途快照拉取失败时不得删除任何 ES 文档")
    void shouldNotDeleteAnyDocumentWhenSnapshotPagingFailsMidway() throws Exception {
        // Arrange: 第 1 页成功（拿到 a1），第 2 页失败 → 权威集合只有半截
        doReturn(Result.ok(snapshotPage("a1", 1, 2)))
                .when(articleIndexClient).getIndexSnapshot(1, 100);
        mockEsSearchWithIds("a1", "a2", "a3");

        // Act
        indexReconciler.reconcile();

        // Assert: a2/a3 只是还没翻到，不是僵尸，一篇都不许删
        verify(articleSearchRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("快照拉取失败时本轮不得标记为完成")
    void shouldNotReportCompletedWhenSnapshotFetchFails() throws Exception {
        // Arrange: 同上，首页失败
        mockEsSearchWithIds("a1");

        // Act
        indexReconciler.reconcile();

        // Assert: 失败必须显式可见（completed=false），否则日志"对账完成: 写入 0 篇"会掩盖事故
        IndexReconciler.SyncStats stats = indexReconciler.getLastSyncStats();
        assertFalse(stats.isCompleted());
        assertEquals(1, stats.getHttpErrors());
    }

    // ============ 回归保护：完整对账仍须清理僵尸文档 ============

    @Test
    @DisplayName("快照完整拉取时仍删除 ES 中多余的僵尸文档")
    void shouldStillDeleteOrphanDocumentsOnCompleteReconcile() throws Exception {
        // Arrange: 单页拉完（totalPages=1），权威集合 {a1}；ES 里多出 zombie-9
        doReturn(Result.ok(snapshotPage("a1", 1, 1)))
                .when(articleIndexClient).getIndexSnapshot(1, 100);
        mockEsSearchWithIds("a1", "zombie-9");

        // Act
        indexReconciler.reconcile();

        // Assert: 僵尸删掉、权威文档保留
        verify(articleSearchRepository).deleteById("zombie-9");
        verify(articleSearchRepository, never()).deleteById("a1");
    }

    // ====== 回归保护：语料 > 页大小（100）必须翻到最后一页，且不得把已发布文章当僵尸删 ======

    private static ArticleIndexMessage message(String id) {
        ArticleIndexMessage message = new ArticleIndexMessage();
        message.setOperation(MQOperation.CREATE);
        message.setArticleId(id);
        message.setTitle("T-" + id);
        message.setStatus("published");
        return message;
    }

    /**
     * 忠实模拟 article-service 快照端点的**真实分页语义**（Task 5/6 在 H2 + 生产拦截器上实测）：
     * <ul>
     *   <li>{@code pageNum} 直达 MP 的 {@code new Page<>(pageNum, size)}，而 MP 是 1-based
     *       —— 请求页 0 与 1 都返回<b>第 1 页</b>的数据（{@code IPage.offset()}: current&lt;=1 → 0）；</li>
     *   <li>拦截器 {@code overflow=true} 在 {@code current > pages} 时把 current 拨回 1
     *       —— 越界请求仍返回<b>第 1 页</b>的数据，永远不会给空页。</li>
     * </ul>
     * 回填的 {@code PageVo.currentPage} 是<b>请求值</b>（controller 原样回填），{@code totalPages} 是真实页数。
     * 每次请求的页号记入 {@code requestedPages}，供断言"每页恰好访问一次"。
     * 越界请求直接抛 {@link AssertionError}：真实端点会把越界弹回首页（无空页），
     * 没有上界守卫的实现会死循环 —— 这里把它转成<b>可断言的失败</b>，而不是让测试挂起。
     */
    private void mockEndpointPaging(List<ArticleIndexMessage> all, List<Integer> requestedPages) {
        int totalPages = (all.size() + 99) / 100;
        when(articleIndexClient.getIndexSnapshot(anyInt(), eq(100))).thenAnswer(inv -> {
            int requested = inv.getArgument(0);
            requestedPages.add(requested);
            if (requested > totalPages) {
                throw new AssertionError("对账越过了最后一页（请求页 " + requested
                        + " > totalPages " + totalPages + "）—— 页数上界守卫失效");
            }
            int effective = requested <= 1 ? 1 : requested;
            int from = (effective - 1) * 100;
            int to = Math.min(from + 100, all.size());
            List<ArticleIndexMessage> rows = from >= all.size() ? List.of() : all.subList(from, to);
            return Result.ok(new PageVo<>(requested, 100, (long) all.size(), totalPages, rows));
        });
    }

    @Test
    @DisplayName("语料超过一页：每页恰好访问一次、翻到最后一页、已发布文章一篇都不许当僵尸删")
    void shouldVisitEveryPageOnceAndNeverOrphanExistingArticles() throws Exception {
        // Arrange: 101 篇已发布（> 页大小 100 → 真实两页）；ES 里是这 101 篇 + 1 篇真僵尸
        List<ArticleIndexMessage> all = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            all.add(message(String.format("P%03d", i)));
        }
        List<Integer> requestedPages = new ArrayList<>();
        mockEndpointPaging(all, requestedPages);

        List<String> esIds = new ArrayList<>(all.stream().map(ArticleIndexMessage::getArticleId).toList());
        esIds.add("zombie-9");
        mockEsSearchWithIds(esIds.toArray(String[]::new));

        // Act
        indexReconciler.reconcile();

        // Assert (a): 从第 1 页开始、每页恰好一次、翻到最后一页（2）—— 0-based 起始会是 [0, 1]
        assertEquals(List.of(1, 2), requestedPages, "应逐页访问到最后一页各一次");
        // Assert (b): 第 2 页的文章必须进权威集合 —— 0-based 起始时它会被当成僵尸删掉
        verify(articleSearchRepository).deleteById("zombie-9");
        verify(articleSearchRepository, never()).deleteById("P101");
        verify(articleSearchRepository, never()).deleteById("P001");
        // 101 篇都要写入，且本轮必须是"完成"状态
        assertEquals(101, indexReconciler.getLastSyncStats().getUpserted());
        assertTrue(indexReconciler.getLastSyncStats().isCompleted());
    }
}
