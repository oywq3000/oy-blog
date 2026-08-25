package com.oyproj.api.user.client.fallback;

import com.oyproj.api.user.client.AdminUserClient;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.utils.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AdminUserClientFallbackFactory implements FallbackFactory<AdminUserClient> {

    @Override
    public AdminUserClient create(Throwable cause) {
        return new AdminUserClient() {
            @Override
            public Result<PageVo<List<UserAdminItemVo>>> adminUserPage(UserAdminPageDto dto) {
                log.warn("用户服务管理接口调用失败(分页用户)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> banUser(String id) {
                log.warn("用户服务管理接口调用失败(禁用用户)，用户ID: {}, 错误: {}", id, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> unbanUser(String id) {
                log.warn("用户服务管理接口调用失败(启用用户)，用户ID: {}, 错误: {}", id, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> assignRole(UserRoleAssignDto dto) {
                log.warn("用户服务管理接口调用失败(分配角色)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }
        };
    }
}
