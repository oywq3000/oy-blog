package com.oyproj.Repository;

import com.oyproj.domain.entity.ArticleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章搜索 Repository
 * 查询全部通过 SearchBizServiceImpl 使用 ElasticsearchClient 原生 API，此处仅提供 CRUD 基础能力。
 */
@Repository
public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleDocument, String> {
}