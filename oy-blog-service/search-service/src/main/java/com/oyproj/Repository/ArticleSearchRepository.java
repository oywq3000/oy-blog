package com.oyproj.Repository;

import com.oyproj.domain.entity.ArticleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章搜索Repository
 */
@Repository
public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleDocument, String> {
    
    /**
     * 根据标题或内容搜索
     */
    List<ArticleDocument> findByTitleContainingOrContentContaining(String title, String content);
    
    /**
     * 根据作者搜索
     */
    List<ArticleDocument> findByAuthorContaining(String author);
    
    /**
     * 根据标签搜索
     */
    List<ArticleDocument> findByTagsContaining(String tag);
}