package com.oyproj.service;

import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 用户管理 BFF 业务：透传 Feign 调用 user-service
 */
public interface AdminUserBizService {

    Result<PageVo<List<UserAdminItemVo>>> page(UserAdminPageDto dto);

    Result<Boolean> ban(String id);

    Result<Boolean> unban(String id);

    Result<Boolean> assignRole(UserRoleAssignDto dto);
}
