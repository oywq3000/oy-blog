package com.oyproj.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.SearchQueryDTO;
import com.oyproj.domain.entity.ArticleDocument;
import com.oyproj.service.SearchBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 搜索业务服务实现（Elasticsearch）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchBizServiceImpl implements SearchBizService {

    private static final String INDEX = "articles";
    private final ArticleSearchRepository articleSearchRepository;

    @NotNull private final ElasticsearchClient client;

    public Result<List<ArticleDocument>> searchArticles(SearchQueryDTO queryDTO) {
        // 构建复杂查询
        try{
            BoolQuery.Builder boolQueryBuilder  = new BoolQuery.Builder();

            // 关键词搜索（标题、内容）
            if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isEmpty()) {
                boolQueryBuilder.should(
                        MatchQuery.of(m->m.field("title").query(queryDTO.getKeyword()).boost(2.0f))._toQuery()
                );
                boolQueryBuilder.should(
                        MatchQuery.of(m -> m.field("content").query(queryDTO.getKeyword()))._toQuery()
                );
            }

            // 作者搜索
            if (queryDTO.getAuthor() != null && !queryDTO.getAuthor().isEmpty()) {
                boolQueryBuilder.must(
                        TermQuery.of(t -> t.field("author").value(queryDTO.getAuthor()))._toQuery()
                );
            }

            // 标签搜索
            if (queryDTO.getTag() != null && !queryDTO.getTag().isEmpty()) {
                boolQueryBuilder.must(
                        TermQuery.of(t -> t.field("tags").value(queryDTO.getTag()))._toQuery()
                );
            }

            // 分类搜索
            if (queryDTO.getCategory() != null && !queryDTO.getCategory().isEmpty()) {
                boolQueryBuilder.must(
                        TermQuery.of(t -> t.field("category").value(queryDTO.getCategory()))._toQuery()
                );
            }

            // 状态过滤
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
                boolQueryBuilder.must(
                        TermQuery.of(t -> t.field("status").value(queryDTO.getStatus()))._toQuery()
                );
            }

            // 分页设置
            int pageNum = queryDTO.getPage() != null ? queryDTO.getPage(): 0;
            int pageSize = queryDTO.getSize() != null ? queryDTO.getSize() : 10;
            int from = pageNum * pageSize;

            // 执行搜索
            SearchResponse<ArticleDocument> response = client.search(s -> s
                            .index(INDEX)
                            .query(boolQueryBuilder.build()._toQuery())
                            .from(from)
                            .size(pageSize),
                    ArticleDocument.class
            );
            return Result.ok(response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .collect(Collectors.toList()));
        }catch (Exception e) {
            log.error("搜索文章失败: {}", e.getMessage(), e);
            return Result.ok(List.of());
        }
    }
    public void indexArticle(ArticleDocument article) {
        try {
            articleSearchRepository.save(article);
            log.info("文章索引成功，ID: {}", article.getArticleId());
        } catch (Exception e) {
            log.error("文章索引失败，ID: {}, 错误: {}", article.getArticleId(), e.getMessage());
            throw new RuntimeException("文章索引失败");
        }
    }

    public void deleteArticleIndex(Long articleId) {

    }

    public void bulkIndexArticles(List<ArticleDocument> articles) {
        try {
            articleSearchRepository.saveAll(articles);
            log.info("批量索引成功，数量: {}", articles.size());
        } catch (Exception e) {
            log.error("批量索引失败，错误: {}", e.getMessage());
            throw new RuntimeException("批量索引失败");
        }
    }


}

