package com.oyproj.controller;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminModerationBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端文章审核控制器（BFF 入口，管理前端只调这里）
 */
@Tag(name = "管理端文章审核控制器", description = "文章待审队列与人工审核")
@RestController
@RequestMapping("/admin/moderation")
@RequiredArgsConstructor
public class AdminModerationController {

    private final AdminModerationBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:moderation:read")
    @Operation(summary = "文章待审队列")
    public Result<PageVo<List<ArticleModerationItemVo>>> page(@RequestBody ArticleModerationPageDto dto) {
        return biz.page(dto);
    }

    @PostMapping("/audit")
    @RequirePermission("admin:moderation:write")
    @Operation(summary = "人工审核文章（通过/驳回）")
    public Result<Boolean> audit(@RequestBody @Valid ArticleModerationAuditDto dto) {
        return biz.audit(dto);
    }
}
