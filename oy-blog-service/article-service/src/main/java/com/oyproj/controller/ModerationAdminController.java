package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.ModerationAdminBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章审核后台控制器（仅供 admin-service 通过 Feign 调用，直接 HTTP 访问需 ADMIN 角色）
 */
@Tag(name = "文章审核后台控制器", description = "文章待审队列与人工审核")
@RestController
@RequestMapping("/article/moderation/admin")
@RequiredArgsConstructor
public class ModerationAdminController {

    private final ModerationAdminBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:moderation:read")
    @Operation(summary = "文章待审队列（新文章+待审编辑合并）")
    public Result<PageVo<List<ArticleModerationItemVo>>> adminPage(@RequestBody ArticleModerationPageDto dto) {
        return biz.adminPage(dto);
    }

    @PostMapping("/audit")
    @RequirePermission("admin:moderation:write")
    @Operation(summary = "人工审核文章（通过/驳回）")
    public Result<Boolean> audit(@RequestBody ArticleModerationAuditDto dto) {
        return biz.audit(dto);
    }
}
