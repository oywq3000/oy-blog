package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.api.article.domain.vo.ArticleAdminItemVo;
import com.oyproj.api.article.domain.vo.SeriesAdminVo;
import com.oyproj.api.article.domain.vo.TagAdminVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.ArticleAdminBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章管理后台控制器（仅供 admin-service 通过 Feign 调用，直接 HTTP 访问需 ADMIN 角色）
 */
@Tag(name = "文章管理后台控制器", description = "管理视角文章列表、标签与系列维护")
@RestController
@RequestMapping("/article/admin")
@RequiredArgsConstructor
public class ArticleAdminController {

    private final ArticleAdminBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:article:read")
    @Operation(summary = "管理视角文章分页列表")
    public Result<PageVo<List<ArticleAdminItemVo>>> adminPage(@RequestBody ArticleAdminPageDto dto) {
        return biz.adminPage(dto);
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
