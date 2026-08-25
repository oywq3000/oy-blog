package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.CommentAdminBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论审核后台控制器（仅供 admin-service 通过 Feign 调用，直接 HTTP 访问需 ADMIN 角色）
 */
@Tag(name = "评论审核后台控制器", description = "评论待审列表、审核、删除与置顶")
@RestController
@RequestMapping("/article/comment/admin")
@RequiredArgsConstructor
public class CommentAdminController {

    private final CommentAdminBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:comment:read")
    @Operation(summary = "评论分页列表")
    public Result<PageVo<List<CommentAdminItemVo>>> adminPage(@RequestBody CommentAdminPageDto dto) {
        return biz.adminPage(dto);
    }

    @PostMapping("/audit")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "审核评论（通过/拒绝）")
    public Result<Boolean> audit(@RequestBody CommentAuditDto dto) {
        return biz.audit(dto);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "删除评论")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return biz.delete(id);
    }

    @PostMapping("/{id}/pin")
    @RequirePermission("admin:comment:write")
    @Operation(summary = "置顶/取消置顶评论")
    public Result<Boolean> pin(@PathVariable("id") String id, @RequestParam("pinned") Integer pinned) {
        return biz.pin(id, pinned);
    }
}
