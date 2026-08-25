package com.oyproj.api.user.client;

import com.oyproj.api.config.AdminFeignConfig;
import com.oyproj.api.user.client.fallback.AdminUserClientFallbackFactory;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户服务管理接口 Feign 客户端（admin-service 使用）
 */
@FeignClient(value = "user-service", configuration = AdminFeignConfig.class,
        fallbackFactory = AdminUserClientFallbackFactory.class)
public interface AdminUserClient {

    @PostMapping("/admin/users/page")
    Result<PageVo<List<UserAdminItemVo>>> adminUserPage(@RequestBody UserAdminPageDto dto);

    @PostMapping("/admin/users/{id}/ban")
    Result<Boolean> banUser(@PathVariable("id") String id);

    @PostMapping("/admin/users/{id}/unban")
    Result<Boolean> unbanUser(@PathVariable("id") String id);

    @PostMapping("/admin/users/role")
    Result<Boolean> assignRole(@RequestBody UserRoleAssignDto dto);
}
