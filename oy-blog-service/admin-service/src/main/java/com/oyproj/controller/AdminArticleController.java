package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminArticleBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端文章管理控制器（BFF 入口，管理前端只调这里）
 */
@Tag(name = "管理端文章控制器", description = "管理端文章列表、发布、标签与系列")
@RestController
@RequestMapping("/admin/article")
@RequiredArgsConstructor
public class AdminArticleController {

    private final AdminArticleBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:article:read")
    @Operation(summary = "管理视角文章分页列表")
    public Result<PageVo<List<ArticleAdminItemVo>>> page(@RequestBody ArticleAdminPageDto dto) {
        return biz.page(dto);
    }

    @PostMapping("/draft")
    @RequirePermission("admin:article:write")
    @Operation(summary = "保存草稿")
    public Result<String> draft(@RequestBody ArticleSaveDto dto) {
        return biz.draft(dto);
    }

    @PostMapping("/publish")
    @RequirePermission("admin:article:write")
    @Operation(summary = "发布文章")
    public Result<Map<String, String>> publish(@RequestBody ArticleSaveDto dto) {
        return biz.publish(dto);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除文章")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return biz.delete(id);
    }

    @PostMapping("/tag")
    @RequirePermission("admin:article:write")
    @Operation(summary = "新建或更新标签")
    public Result<String> saveTag(@RequestBody TagSaveDto dto) {
        return biz.saveTag(dto);
    }

    @DeleteMapping("/tag/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除标签")
    public Result<Boolean> deleteTag(@PathVariable("id") String id) {
        return biz.deleteTag(id);
    }

    @GetMapping("/tags")
    @RequirePermission("admin:article:read")
    @Operation(summary = "标签全量列表")
    public Result<List<TagAdminVo>> listTags() {
        return biz.listTags();
    }

    @PostMapping("/series")
    @RequirePermission("admin:article:write")
    @Operation(summary = "新建或更新系列")
    public Result<String> saveSeries(@RequestBody SeriesSaveDto dto) {
        return biz.saveSeries(dto);
    }

    @DeleteMapping("/series/{id}")
    @RequirePermission("admin:article:write")
    @Operation(summary = "删除系列")
    public Result<Boolean> deleteSeries(@PathVariable("id") String id) {
        return biz.deleteSeries(id);
    }

    @GetMapping("/series")
    @RequirePermission("admin:article:read")
    @Operation(summary = "系列全量列表")
    public Result<List<SeriesAdminVo>> listSeries() {
        return biz.listSeries();
    }
}
