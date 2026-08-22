package com.oyproj.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.oyproj.Repository.ArticleSearchRepository;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.common.SearchFitter;
import com.oyproj.domain.dto.SearchQueryDTO;
import com.oyproj.domain.entity.ArticleDocument;
import com.oyproj.domain.vo.ArticleSearchVO;
import com.oyproj.service.SearchBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public Result<PageVo<List<ArticleSearchVO>>> searchArticles(SearchQueryDTO queryDTO) {
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
                        // 标签精确匹配（caseInsensitive 保证大小写不敏感）
                        boolQueryBuilder.must(termCI("tags", keyword));
                        hasSearchCondition = true;
                        break;
                    case AUTHOR:
                        // 作者精确匹配（caseInsensitive 保证大小写不敏感）
                        boolQueryBuilder.should(termCI("authorName", keyword));
                        boolQueryBuilder.should(termCI("authorId", keyword));
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
                        boolQueryBuilder.should(termCI("tags", keyword));
                        boolQueryBuilder.should(termCI("authorName", keyword));
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
            int pageNum = queryDTO.getPageNum() != null ? queryDTO.getPageNum(): 0;
            int pageSize = queryDTO.getPageSize() != null ? queryDTO.getPageSize() : 10;
            int from = pageNum * pageSize;
            // 是否有关键词搜索条件（只有关键词搜索才需要高亮）
            boolean hasKeywordQuery = hasKeyword && (filter == SearchFitter.ALL || filter == SearchFitter.ARTICLE);
            // 标签高亮：TAG 精确过滤，或 ALL 模式下命中标签 TermQuery
            boolean needTagHighlight = hasKeyword && (filter == SearchFitter.ALL || filter == SearchFitter.TAG);
            // 作者高亮：AUTHOR 精确过滤，或 ALL 模式下命中作者 TermQuery
            boolean needAuthorHighlight = hasKeyword && (filter == SearchFitter.ALL || filter == SearchFitter.AUTHOR);

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
                // 高亮配置：按搜索模式启用对应字段
                if (hasKeywordQuery || needTagHighlight || needAuthorHighlight) {
                    s.highlight(h -> {
                        if (hasKeywordQuery) {
                            // title 高亮
                            h.fields("title", hf -> hf
                                    .preTags("<em class=\"highlight\">")
                                    .postTags("</em>")
                                    .numberOfFragments(0)
                            );
                            // content 高亮：返回 200 字上下文片段
                            h.fields("content", hf -> hf
                                    .preTags("<em class=\"highlight\">")
                                    .postTags("</em>")
                                    .fragmentSize(200)
                                    .numberOfFragments(1)
                            );
                            // summary 高亮：返回 200 字上下文片段
                            h.fields("summary", hf -> hf
                                    .preTags("<em class=\"highlight\">")
                                    .postTags("</em>")
                                    .fragmentSize(200)
                                    .numberOfFragments(1)
                            );
                        }
                        if (needTagHighlight) {
                            // tags 是 keyword 数组：高亮返回命中元素的完整值
                            h.fields("tags", hf -> hf
                                    .preTags("<em class=\"highlight\">")
                                    .postTags("</em>")
                                    .numberOfFragments(0)
                            );
                        }
                        if (needAuthorHighlight) {
                            h.fields("authorName", hf -> hf
                                    .preTags("<em class=\"highlight\">")
                                    .postTags("</em>")
                                    .numberOfFragments(0)
                            );
                        }
                        return h;
                    });
                }
                return s;
            }, ArticleDocument.class);

            // 提取高亮并映射为 ArticleSearchVO
            List<ArticleSearchVO> collect = mapToSearchResults(response);
            // 获取总记录数 - 从response.hits().total()获取
            Long total = response.hits().total() != null ? response.hits().total().value() : 0L;
            Integer totalPages = (int) Math.ceil((double) total / pageSize);
            // 构建分页结果
            PageVo<List<ArticleSearchVO>> pageVo = new PageVo<>(pageNum, pageSize, total, totalPages, collect);
            return Result.ok(pageVo);
        }catch (Exception e) {
            log.error("搜索文章失败: {}", e.getMessage(), e);
            PageVo<List<ArticleSearchVO>> pageVo = new PageVo<>(0,0 , 0L, 0, null);
            return Result.ok(pageVo);

        }
    }

    /**
     * 构建大小写不敏感的精确匹配查询（tags/authorName/authorId 等 keyword 字段）。
     * keyword 字段默认大小写敏感，开启 caseInsensitive 保证搜索不区分大小写。
     */
    private static Query termCI(String field, String value) {
        return TermQuery.of(t -> t.field(field).value(value).caseInsensitive(true))._toQuery();
    }

    /**
     * 将 ES 搜索结果映射为 ArticleSearchVO 列表，提取高亮片段。
     * <p>
     * 高亮优先级：content → summary → null
     */
    private List<ArticleSearchVO> mapToSearchResults(SearchResponse<ArticleDocument> response) {
        List<ArticleSearchVO> results = new ArrayList<>();
        for (Hit<ArticleDocument> hit : response.hits().hits()) {
            ArticleSearchVO vo = new ArticleSearchVO();
            ArticleDocument source = hit.source();
            if (source != null) {
                // 复制所有字段
                copyDocumentFields(source, vo);
            }

            // 提取高亮片段
            Map<String, List<String>> highlights = hit.highlight();
            if (highlights != null && !highlights.isEmpty()) {
                // highlightTitle
                List<String> titleFrags = highlights.get("title");
                if (titleFrags != null && !titleFrags.isEmpty()) {
                    vo.setHighlightTitle(titleFrags.get(0));
                }
                // highlightSnippet: 优先 content，其次 summary
                List<String> contentFrags = highlights.get("content");
                List<String> summaryFrags = highlights.get("summary");
                if (contentFrags != null && !contentFrags.isEmpty()) {
                    vo.setHighlightSnippet(contentFrags.get(0));
                } else if (summaryFrags != null && !summaryFrags.isEmpty()) {
                    vo.setHighlightSnippet(summaryFrags.get(0));
                }
                // highlightTags: 命中的标签名（去 em 纯文本，前端用它强制展示并高亮）
                List<String> tagFrags = highlights.get("tags");
                if (tagFrags != null && !tagFrags.isEmpty()) {
                    vo.setHighlightTags(tagFrags.stream()
                            .map(this::stripHighlightMarkers)
                            .filter(name -> name != null && !name.isEmpty())
                            .toList());
                }
                // highlightAuthorName: 命中作者名的 HTML 片段（前端 v-html 渲染）
                List<String> authorFrags = highlights.get("authorName");
                if (authorFrags != null && !authorFrags.isEmpty()) {
                    vo.setHighlightAuthorName(authorFrags.get(0));
                }
            }
            results.add(vo);
        }
        return results;
    }

    /**
     * 去除高亮片段中的 em 标签，还原纯文本（如 &lt;em&gt;Java&lt;/em&gt; → Java）
     */
    private String stripHighlightMarkers(String fragment) {
        if (fragment == null) {
            return null;
        }
        return fragment.replaceAll("<em[^>]*>", "").replaceAll("</em>", "");
    }

    /**
     * 将 ArticleDocument 字段复制到 ArticleSearchVO。
     */
    private void copyDocumentFields(ArticleDocument src, ArticleSearchVO dst) {
        dst.setId(src.getId());
        dst.setSlug(src.getSlug());
        dst.setTitle(src.getTitle());
        dst.setContent(src.getContent());
        dst.setSummary(src.getSummary());
        dst.setAuthorName(src.getAuthorName());
        dst.setAuthorAvatar(src.getAuthorAvatar());
        dst.setAuthorId(src.getAuthorId());
        dst.setCreatedAt(src.getCreatedAt());
        dst.setUpdatedAt(src.getUpdatedAt());
        dst.setStatus(src.getStatus());
        dst.setViewCount(src.getViewCount());
        dst.setLikeCount(src.getLikeCount());
        dst.setCommentCount(src.getCommentCount());
        dst.setTags(src.getTags());
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

