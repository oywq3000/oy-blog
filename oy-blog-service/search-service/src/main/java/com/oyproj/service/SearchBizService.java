package com.oyproj.service;


import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.SearchQueryDTO;
import com.oyproj.domain.entity.ArticleDocument;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 *  搜索业务服务接口（Elasticsearch）
 */
public interface SearchBizService {


    Result<List<ArticleDocument>> searchArticles(SearchQueryDTO queryDTO);
    void indexArticle(ArticleDocument article);
    void deleteArticleIndex(Long articleId);
    void bulkIndexArticles(List<ArticleDocument> articles);
}

