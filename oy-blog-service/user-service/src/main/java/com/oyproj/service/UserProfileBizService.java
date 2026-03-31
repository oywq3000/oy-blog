package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.vo.UserVo;

import java.util.Map;

/**
 * 用户个人信息业务服务接口
 */
public interface UserProfileBizService {
    /**
     * 获取当前登录用户信息
     *
     * @return 当前登录用户信息
     */
    Result<UserVo> getProfile();

    /**
     * 根据用户ID获取用户名
     *
     * @param userId 用户ID
     * @return 用户名
     */
    Result<Map<String, String>> getUsernameById(String userId);
}
