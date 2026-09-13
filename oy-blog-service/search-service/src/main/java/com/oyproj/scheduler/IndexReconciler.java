package com.oyproj.scheduler;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.api.article.client.ArticleIndexClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.converter.ArticleDocumentConverter;
import com.oyproj.domain.entity.ArticleDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * ES-MySQL 索引对账调度器
 * 定时从 article-service 拉取全量已发布文章，批量写入 ES 并清理僵尸文档。
 * 这是 MQ 实时同步的兜底保障。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexReconciler {

    private static final String INDEX = "articles";
    private static final int PAGE_SIZE = 100;
    private static final int ES_SCROLL_SIZE = 5000;

    private final ArticleIndexClient articleIndexClient;
    private final ArticleSearchRepository articleSearchRepository;
    private final ElasticsearchClient esClient;

    private final AtomicReference<SyncStats> lastSyncStats = new AtomicReference<>(new SyncStats());

    /**
     * 每 30 分钟执行一次全量对账
     */
    @Scheduled(fixedDelayString = "${oy-blog.sync.reconcile-interval-ms:1800000}",
               initialDelayString = "${oy-blog.sync.reconcile-initial-delay-ms:60000}")
    public void reconcile() {
        log.info("开始 ES-MySQL 索引对账...");
        LocalDateTime start = LocalDateTime.now();
        SyncStats stats = new SyncStats();
        stats.setStartTime(start);
        int upserted = 0;
        boolean snapshotComplete = true;
        Set<String> authoritativeIds = new HashSet<>();

        try {
            // 1. 分页拉取 article-service 全量已发布文章
            // 页号 1-based：article-service 把它直达 MyBatis-Plus 的 new Page<>(pageNum, size)，
            // 而 MP 的 Page 是 1-based（IPage.offset(): current<=1 → 0）—— 传 0 与传 1 命中同一页：
            // 从 0 开始翻会重复第一页、此后页码整体错位一页，并在下方以总页数为上界时更早 break，
            // **漏掉末尾那一页**（细节见守卫处的注释）。并非"再也翻不到后面的页"。
            int page = 1;
            while (true) {
                Result<PageVo<List<ArticleIndexMessage>>> result =
                        articleIndexClient.getIndexSnapshot(page, PAGE_SIZE);
                if (result == null || !result.getIsSuccess() || result.getData() == null) {
                    log.error("拉取文章索引快照失败, page: {}, 中止本轮对账（跳过僵尸文档清理）", page);
                    stats.setHttpErrors(stats.getHttpErrors() + 1);
                    snapshotComplete = false;
                    break;
                }

                List<ArticleIndexMessage> messages = result.getData().getData();
                if (messages == null || messages.isEmpty()) {
                    break;
                }

                // 转换为 ES 文档并批量写入
                List<ArticleDocument> docs = messages.stream()
                        .map(this::convertToDocument)
                        .collect(Collectors.toList());
                articleSearchRepository.saveAll(docs);
                upserted += docs.size();

                // 记录 MySQL 侧文章ID
                docs.forEach(d -> authoritativeIds.add(d.getId()));
                log.info("对账进度: 已处理 {} 篇, 当前页 {} 篇", upserted, docs.size());

                // 判断是否最后一页。**必须用页数上界终止，不能等"空页"**：pageNum 是 1-based
                // （MP 的 Page 是 1-based），且拦截器 overflow=true 在 current > pages 时会把
                // current 拨回 1（**第一页**）—— 越界翻页永远拿得到非空页，等空页会死循环。
                // 用 0-based 起始 + `totalPages - 1` 守卫则更糟（修复前的实际写法）：第 0、1 页
                // 读成同一页 ⇒ 同样的页数上界下**少读一页**，末尾那一页的文章没进 authoritativeIds
                // → >100 篇（≥2 页）时最旧的那批被当成僵尸**误删**。注意它并非"只装下最新一页"，
                // 而是重复第一页 + 漏掉最后一页（总页数正好为 2 时，两者恰好重合）。
                PageVo<?> pageVo = result.getData();
                Integer totalPages = pageVo.getTotalPages();
                if (totalPages == null) {
                    // 页数未知 ⇒ 无法确认是否已翻完 ⇒ 按"快照不完整"处理，禁止清理僵尸文档。
                    // （authoritativeIds 可能是半截，拿它当权威集合会误删尚未翻到的文章。）
                    log.error("快照未给出总页数, page: {}, 中止本轮对账（跳过僵尸文档清理）", page);
                    snapshotComplete = false;
                    break;
                }
                if (pageVo.getCurrentPage() >= totalPages) {
                    break;
                }
                page++;
            }

            stats.setUpserted(upserted);

            // 快照不完整时严禁清理僵尸文档：authoritativeIds 只有半截（首页失败时为空集），
            // 拿它当"权威集合"会把尚未翻到的已发布文章——乃至整个索引——误判为僵尸删掉
            if (!snapshotComplete) {
                stats.setCompleted(false);
                stats.setErrorMessage("文章索引快照拉取不完整，已跳过僵尸文档清理");
                stats.setDurationMs(ChronoUnit.MILLIS.between(start, LocalDateTime.now()));
                lastSyncStats.set(stats);
                return;
            }

            // 2. 清理 ES 中多余的文档（MySQL 已删除或取消发布的）
            int orphansDeleted = deleteOrphanDocuments(authoritativeIds);
            stats.setOrphansDeleted(orphansDeleted);

            stats.setCompleted(true);
            stats.setDurationMs(ChronoUnit.MILLIS.between(start, LocalDateTime.now()));
            lastSyncStats.set(stats);
            log.info("ES-MySQL 对账完成: 写入 {} 篇, 清理 {} 篇僵尸文档, 耗时 {}ms",
                    upserted, orphansDeleted, stats.getDurationMs());

        } catch (Exception e) {
            log.error("ES-MySQL 对账异常", e);
            stats.setCompleted(false);
            stats.setErrorMessage(e.getMessage());
            lastSyncStats.set(stats);
        }
    }

    /**
     * 删除 ES 中存在但 MySQL 中不存在的文档
     */
    private int deleteOrphanDocuments(Set<String> authoritativeIds) {
        int deleted = 0;
        try {
            // 使用 scroll 查询 ES 中所有文档 ID
            List<String> esIds = new ArrayList<>();
            SearchResponse<ArticleDocument> response = esClient.search(s -> s
                    .index(INDEX)
                    .query(MatchAllQuery.of(m -> m)._toQuery())
                    .size(ES_SCROLL_SIZE)
                    .scroll(t -> t.time("2m")),
                    ArticleDocument.class);
            String scrollId = response.scrollId();

            // 处理第一批
            List<String> batchIds = response.hits().hits().stream()
                    .map(Hit::id)
                    .collect(Collectors.toList());
            esIds.addAll(batchIds);

            // 继续 scroll
            while (!batchIds.isEmpty() && batchIds.size() >= ES_SCROLL_SIZE) {
                String sid = scrollId;
                var scrollResponse = esClient.scroll(s -> s.scrollId(sid).scroll(t -> t.time("2m")),
                        ArticleDocument.class);
                scrollId = scrollResponse.scrollId();
                batchIds = scrollResponse.hits().hits().stream()
                        .map(Hit::id)
                        .collect(Collectors.toList());
                esIds.addAll(batchIds);
            }

            // 清理 scroll
            String finalScrollId = scrollId;
            if (finalScrollId != null) {
                try {
                    esClient.clearScroll(c -> c.scrollId(finalScrollId));
                } catch (Exception ignored) {
                }
            }

            // 批量删除不在 MySQL 中的文档
            List<String> toDelete = esIds.stream()
                    .filter(id -> !authoritativeIds.contains(id))
                    .collect(Collectors.toList());
            if (!toDelete.isEmpty()) {
                for (String id : toDelete) {
                    try {
                        articleSearchRepository.deleteById(id);
                        deleted++;
                    } catch (Exception e) {
                        log.warn("删除 ES 僵尸文档失败, id: {}", id, e);
                    }
                }
                log.info("清理 {} 篇 ES 僵尸文档", deleted);
            }
        } catch (Exception e) {
            log.error("清理僵尸文档异常", e);
        }
        return deleted;
    }

    private ArticleDocument convertToDocument(ArticleIndexMessage message) {
        return ArticleDocumentConverter.toDocument(message);
    }

    public SyncStats getLastSyncStats() {
        return lastSyncStats.get();
    }

    @lombok.Data
    public static class SyncStats {
        private LocalDateTime startTime;
        private int upserted;
        private int orphansDeleted;
        private int httpErrors;
        private long durationMs;
        private boolean completed;
        private String errorMessage;
    }
}
