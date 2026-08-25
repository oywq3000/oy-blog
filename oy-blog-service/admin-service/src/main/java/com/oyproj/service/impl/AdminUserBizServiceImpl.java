package com.oyproj.service.impl;

import com.oyproj.api.user.client.AdminUserClient;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminUserBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserBizServiceImpl extends AdminBizBase implements AdminUserBizService {

    private final AdminUserClient client;

    @Override
    public Result<PageVo<List<UserAdminItemVo>>> page(UserAdminPageDto dto) {
        return client.adminUserPage(dto);
    }

    @Override
    public Result<Boolean> ban(String id) {
        return client.banUser(id);
    }

    @Override
    public Result<Boolean> unban(String id) {
        return client.unbanUser(id);
    }

    @Override
    public Result<Boolean> assignRole(UserRoleAssignDto dto) {
        return client.assignRole(dto);
    }
}
