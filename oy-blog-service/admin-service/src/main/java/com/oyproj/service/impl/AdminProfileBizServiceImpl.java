package com.oyproj.service.impl;

import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.service.AdminProfileBizService;
import org.springframework.stereotype.Service;

@Service
public class AdminProfileBizServiceImpl extends AdminBizBase implements AdminProfileBizService {

    @Override
    public Result<UserDTO> currentUser() {
        return Result.ok(getCurrentUserDTO());
    }
}
