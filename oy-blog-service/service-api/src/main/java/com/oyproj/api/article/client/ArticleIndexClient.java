package com.oyproj.api.article.client;

import com.oyproj.api.article.client.fallback.ArticleIndexClientFallbackFactory;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 文章索引数据 Feign 客户端
 * 供 search-service 对账重建索引用
 */
@FeignClient(value = "article-service", contextId = "article-index-client", fallbackFactory = ArticleIndexClientFallbackFactory.class)
public interface ArticleIndexClient {

    /**
     * 分页获取已发布文章的索引快照数据
     *
     * @param pageNum 页码（0-based）
     * @param pageSize 每页大小
     * @return 文章索引消息列表（含 content + stats）
     */
    @GetMapping("/internal/index/snapshot")
    Result<PageVo<List<ArticleIndexMessage>>> getIndexSnapshot(
            @RequestParam("pageNum") int pageNum,
            @RequestParam("pageSize") int pageSize);
}
