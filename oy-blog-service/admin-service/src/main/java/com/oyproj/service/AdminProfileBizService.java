package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;

/**
 * 管理员个人信息业务
 */
public interface AdminProfileBizService {

    /** 获取当前登录管理员信息 */
    Result<UserDTO> currentUser();
}
