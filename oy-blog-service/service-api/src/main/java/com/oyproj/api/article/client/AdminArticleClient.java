package com.oyproj.api.article.client;

import com.oyproj.api.article.client.fallback.AdminArticleClientFallbackFactory;
import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.api.config.AdminFeignConfig;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章服务管理接口 Feign 客户端（admin-service 使用）
 */
@FeignClient(value = "article-service", configuration = AdminFeignConfig.class,
        fallbackFactory = AdminArticleClientFallbackFactory.class)
public interface AdminArticleClient {

    // ===== 文章管理 =====
    @PostMapping("/article/admin/page")
    Result<PageVo<List<ArticleAdminItemVo>>> adminArticlePage(@RequestBody ArticleAdminPageDto dto);

    @PostMapping("/article/draft")
    Result<String> saveDraft(@RequestBody ArticleSaveDto dto);

    @PostMapping("/article/publish")
    Result<Map<String, String>> publish(@RequestBody ArticleSaveDto dto);

    @DeleteMapping("/article/{id}")
    Result<Boolean> deleteArticle(@PathVariable("id") String id);

    // ===== 标签管理 =====
    @PostMapping("/article/admin/tag")
    Result<String> saveTag(@RequestBody TagSaveDto dto);

    @DeleteMapping("/article/admin/tag/{id}")
    Result<Boolean> deleteTag(@PathVariable("id") String id);

    @GetMapping("/article/admin/tags")
    Result<List<TagAdminVo>> listTags();

    // ===== 系列管理 =====
    @PostMapping("/article/admin/series")
    Result<String> saveSeries(@RequestBody SeriesSaveDto dto);

    @DeleteMapping("/article/admin/series/{id}")
    Result<Boolean> deleteSeries(@PathVariable("id") String id);

    @GetMapping("/article/admin/series")
    Result<List<SeriesAdminVo>> listSeries();

    // ===== 评论审核 =====
    @PostMapping("/article/comment/admin/page")
    Result<PageVo<List<CommentAdminItemVo>>> adminCommentPage(@RequestBody CommentAdminPageDto dto);

    @PostMapping("/article/comment/admin/audit")
    Result<Boolean> auditComment(@RequestBody CommentAuditDto dto);

    @DeleteMapping("/article/comment/admin/{id}")
    Result<Boolean> deleteComment(@PathVariable("id") String id);

    @PostMapping("/article/comment/admin/{id}/pin")
    Result<Boolean> pinComment(@PathVariable("id") String id, @RequestParam("pinned") Integer pinned);
}
