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
import com.oyproj.domain.entity.ArticleDocument;
import com.oyproj.util.MarkdownSanitizer;
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
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 60 * 1000)
    public void reconcile() {
        log.info("开始 ES-MySQL 索引对账...");
        LocalDateTime start = LocalDateTime.now();
        SyncStats stats = new SyncStats();
        stats.setStartTime(start);
        int upserted = 0;
        Set<String> authoritativeIds = new HashSet<>();

        try {
            // 1. 分页拉取 article-service 全量已发布文章
            int page = 0;
            while (true) {
                Result<PageVo<List<ArticleIndexMessage>>> result =
                        articleIndexClient.getIndexSnapshot(page, PAGE_SIZE);
                if (result == null || !result.getIsSuccess() || result.getData() == null) {
                    log.error("拉取文章索引快照失败, page: {}, 终止对账", page);
                    stats.setHttpErrors(stats.getHttpErrors() + 1);
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

                // 判断是否最后一页
                PageVo<?> pageVo = result.getData();
                if (pageVo.getCurrentPage() >= (pageVo.getTotalPages() != null ? pageVo.getTotalPages() - 1 : 0)) {
                    break;
                }
                page++;
            }

            stats.setUpserted(upserted);

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
            String scrollId = null;
            SearchResponse<ArticleDocument> response = esClient.search(s -> s
                    .index(INDEX)
                    .query(MatchAllQuery.of(m -> m)._toQuery())
                    .size(ES_SCROLL_SIZE)
                    .scroll(t -> t.time("2m")),
                    ArticleDocument.class);
            scrollId = response.scrollId();

            while (true) {
                List<String> batchIds = response.hits().hits().stream()
                        .map(Hit::id)
                        .collect(Collectors.toList());
                esIds.addAll(batchIds);
                if (batchIds.isEmpty() || batchIds.size() < ES_SCROLL_SIZE) {
                    break;
                }
                String sid = scrollId;
                response = esClient.scroll(s -> s.scrollId(sid).scroll(t -> t.time("2m")),
                        ArticleDocument.class);
                scrollId = response.scrollId();
            }

            // 清理 scroll
            if (scrollId != null) {
                try {
                    esClient.clearScroll(c -> c.scrollId(scrollId));
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
        ArticleDocument doc = new ArticleDocument();
        doc.setId(message.getArticleId());
        doc.setTitle(message.getTitle());
        doc.setContent(MarkdownSanitizer.sanitize(message.getContentMd()));
        doc.setSummary(message.getSummary());
        doc.setAuthor(message.getAuthor());
        doc.setAuthorId(message.getAuthorId());
        doc.setCreatedAt(message.getCreatedAt());
        doc.setUpdatedAt(message.getUpdatedAt());
        doc.setStatus(message.getStatus());
        doc.setTags(message.getTags());
        doc.setCategory(message.getCategory());
        doc.setViewCount(message.getViewCount() != null ? message.getViewCount() : 0L);
        doc.setLikeCount(message.getLikeCount() != null ? message.getLikeCount() : 0L);
        doc.setCommentCount(message.getCommentCount() != null ? message.getCommentCount() : 0L);
        return doc;
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
