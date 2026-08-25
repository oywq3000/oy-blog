package com.oyproj.service;

import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 用户管理后台业务
 */
public interface UserAdminBizService {

    /** 用户分页列表（关键字/状态筛选，标注是否管理员） */
    Result<PageVo<List<UserAdminItemVo>>> adminPage(UserAdminPageDto dto);

    /** 封禁用户（status=0 并踢下线） */
    Result<Boolean> ban(String id);

    /** 解封用户（status=1） */
    Result<Boolean> unban(String id);

    /** 授予/收回 ADMIN 角色并清除会话缓存 */
    Result<Boolean> assignRole(UserRoleAssignDto dto);
}
