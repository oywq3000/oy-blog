package com.oyproj.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.common.SearchFitter;
import com.oyproj.domain.dto.SearchQueryDTO;
import com.oyproj.domain.entity.ArticleDocument;
import com.oyproj.service.SearchBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    public Result<PageVo<List<ArticleDocument>>> searchArticles(SearchQueryDTO queryDTO) {
        // 构建复杂查询
        try{
            // 检查是否有任何搜索条件
            boolean hasSearchCondition = false;
            BoolQuery.Builder boolQueryBuilder  = new BoolQuery.Builder();

            // 根据 filter 模式决定如何解释 keyword
            SearchFitter filter = queryDTO.getFilter() != null ? queryDTO.getFilter() : SearchFitter.ALL;
            String keyword = queryDTO.getKeyword();
            boolean hasKeyword = keyword != null && !keyword.isEmpty();

            if (hasKeyword) {
                switch (filter) {
                    case TAG:
                        // 标签精确匹配
                        boolQueryBuilder.must(
                                TermQuery.of(t -> t.field("tags").value(keyword))._toQuery()
                        );
                        hasSearchCondition = true;
                        break;
                    case AUTHOR:
                        // 作者精确匹配
                        boolQueryBuilder.must(
                                TermQuery.of(t -> t.field("author").value(keyword))._toQuery()
                        );
                        hasSearchCondition = true;
                        break;
                    case ARTICLE:
                        // 关键词搜索（标题、内容）
                        boolQueryBuilder.should(
                                MatchQuery.of(m->m.field("title").query(keyword).boost(2.0f))._toQuery()
                        );
                        boolQueryBuilder.should(
                                MatchQuery.of(m -> m.field("content").query(keyword))._toQuery()
                        );
                        hasSearchCondition = true;
                        break;
                    default: // ALL / ARTICLE
                        //all
                        boolQueryBuilder.should(
                                MatchQuery.of(m->m.field("title").query(keyword).boost(2.0f))._toQuery()
                        );
                        boolQueryBuilder.should(
                                MatchQuery.of(m -> m.field("content").query(keyword))._toQuery()
                        );
                        boolQueryBuilder.should(
                                TermQuery.of(t -> t.field("tags").value(keyword))._toQuery()
                        );
                        boolQueryBuilder.should(
                                TermQuery.of(t -> t.field("author").value(keyword))._toQuery()
                        );
                        hasSearchCondition = true;
                        break;
                }
            }

            // 状态过滤
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
                boolQueryBuilder.must(
                        TermQuery.of(t -> t.field("status").value(queryDTO.getStatus()))._toQuery()
                );
                hasSearchCondition = true;
            }
            // 发布时间范围过滤（createdAt 即发布时间；草稿不索引，索引内仅已发布文章）
            String fromStr = toEsDateString(queryDTO.getDateFrom(), false);
            String toStr = toEsDateString(queryDTO.getDateTo(), true);
            if (fromStr != null || toStr != null) {
                boolQueryBuilder.must(
                    RangeQuery.of(r -> r.date(dr -> {
                        dr.field("createdAt");
                        if (fromStr != null) dr.gte(fromStr);
                        if (toStr != null) dr.lte(toStr);
                        return dr;
                    }))._toQuery()
                );
                hasSearchCondition = true;
            }
            // 如果没有搜索条件，添加 match_all 查询
            if (!hasSearchCondition) {
                boolQueryBuilder.must(
                        MatchAllQuery.of(m -> m)._toQuery()
                );
            }

            // 分页设置
            int pageNum = queryDTO.getPage() != null ? queryDTO.getPage(): 0;
            int pageSize = queryDTO.getSize() != null ? queryDTO.getSize() : 10;
            int from = pageNum * pageSize;
            // 执行搜索
            SortOptions sortOptions = buildSortOptions(queryDTO);
            SearchResponse<ArticleDocument> response = client.search(s -> {
                s.index(INDEX)
                 .query(boolQueryBuilder.build()._toQuery())
                 .from(from)
                 .size(pageSize);
                if (sortOptions != null) {
                    s.sort(sortOptions);
                }
                return s;
            }, ArticleDocument.class);
            List<ArticleDocument> collect = response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .collect(Collectors.toList());
            // 获取总记录数 - 从response.hits().total()获取
            Long total = response.hits().total() != null ? response.hits().total().value() : 0L;
            Integer totalPages = (int) Math.ceil((double) total / pageSize);
            // 构建分页结果
            PageVo<List<ArticleDocument>> pageVo = new PageVo<>(pageNum, pageSize, total, totalPages, collect);
            return Result.ok(pageVo);
        }catch (Exception e) {
            log.error("搜索文章失败: {}", e.getMessage(), e);
            PageVo<List<ArticleDocument>> pageVo = new PageVo<>(0,0 , 0L, 0, null);
            return Result.ok(pageVo);

        }
    }

    /**
     * 构建排序选项。relevance（默认）返回 null，ES 按 _score 排序。
     */
    private SortOptions buildSortOptions(SearchQueryDTO q) {
        String sortBy = q.getSortBy();
        if (sortBy == null || sortBy.isBlank() || "relevance".equalsIgnoreCase(sortBy)) {
            return null;
        }
        String field = switch (sortBy) {
            case "createdAt" -> "createdAt";
            case "likeCount" -> "likeCount";
            case "viewCount" -> "viewCount";
            default -> null;
        };
        if (field == null) return null;
        SortOrder order = "asc".equalsIgnoreCase(q.getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;
        return SortOptions.of(so -> so.field(f -> f.field(field).order(order)));
    }

    private static final DateTimeFormatter ES_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    /**
     * 将用户传入的日期字符串转为 ES date_hour_minute_second_millis 格式。
     * @param value    用户输入
     * @param endOfDay true 表示 dateTo，补齐到当天 23:59:59.999
     * @return ES 兼容的日期字符串，解析失败返回 null
     */
    private String toEsDateString(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            String v = value.trim().replace(' ', 'T');
            LocalDateTime dt;
            if (v.length() == 10) { // yyyy-MM-dd
                LocalDate d = LocalDate.parse(v);
                dt = endOfDay ? d.atTime(LocalTime.MAX) : d.atStartOfDay();
            } else {
                dt = LocalDateTime.parse(v);
            }
            return ES_DATE_FORMATTER.format(dt);
        } catch (Exception e) {
            log.warn("日期参数解析失败，已忽略该边界: {} -> {}", value, e.getMessage());
            return null;
        }
    }

    public void indexArticle(ArticleDocument article) {
        try {
            articleSearchRepository.save(article);
            log.info("文章索引成功，ID: {}", article.getId());
        } catch (Exception e) {
            log.error("文章索引失败，ID: {}, 错误: {}", article.getId(), e.getMessage());
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

