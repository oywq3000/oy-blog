package com.oyproj.controller;

import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminUserBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端用户管理控制器（BFF 入口）
 */
@Tag(name = "管理端用户控制器", description = "用户列表、封禁与角色分配")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserBizService biz;

    @PostMapping("/page")
    @RequirePermission("admin:user:read")
    @Operation(summary = "用户分页列表")
    public Result<PageVo<List<UserAdminItemVo>>> page(@RequestBody UserAdminPageDto dto) {
        return biz.page(dto);
    }

    @PostMapping("/{id}/ban")
    @RequirePermission("admin:user:write")
    @Operation(summary = "封禁用户")
    public Result<Boolean> ban(@PathVariable("id") String id) {
        return biz.ban(id);
    }

    @PostMapping("/{id}/unban")
    @RequirePermission("admin:user:write")
    @Operation(summary = "解封用户")
    public Result<Boolean> unban(@PathVariable("id") String id) {
        return biz.unban(id);
    }

    @PostMapping("/role")
    @RequirePermission("admin:user:write")
    @Operation(summary = "授予/收回 ADMIN 角色")
    public Result<Boolean> assignRole(@RequestBody UserRoleAssignDto dto) {
        return biz.assignRole(dto);
    }
}
