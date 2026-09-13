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
     * <p><b>页号是 1-based，必须从 1 开始。</b>article-service 侧把它直达 MyBatis-Plus 的
     * {@code new Page<>(pageNum, size)}，而 MP 的 {@code Page} 是 1-based
     * （{@code IPage.offset()}: {@code current <= 1 → 0}）——传 0 与传 1 命中<b>同一页</b>，
     * 从 0 开始翻会重复第 0、1 页并再也翻不到后面的页。唯一调用方
     * {@code IndexReconciler} 已按 1-based 翻页（见 {@code MybatisPlusConfig} 的同类说明）。</p>
     *
     * @param pageNum 页码，从 1 开始（1-based：MP 的 Page 是 1-based）
     * @param pageSize 每页大小
     * @return 文章索引消息列表（含 content + stats）
     */
    @GetMapping("/internal/index/snapshot")
    Result<PageVo<List<ArticleIndexMessage>>> getIndexSnapshot(
            @RequestParam("pageNum") int pageNum,
            @RequestParam("pageSize") int pageSize);
}
