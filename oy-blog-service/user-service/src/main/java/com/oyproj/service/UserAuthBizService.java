package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.LoginDto;
import com.oyproj.domain.dto.RegisterDto;
import com.oyproj.domain.dto.TokenInfo;
import com.oyproj.domain.dto.UpdatePasswordDto;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户认证业务服务接口
 */
public interface UserAuthBizService {
    /**
     * 用户登录
     *
     * @param req 登录请求参数
     * @return 登录结果
     */
    @Transactional
    Result<TokenInfo> login(LoginDto req);

    /**
     * 用户注册
     *
     * @param req 注册请求参数
     * @return 注册结果
     */
    @Transactional
    Result<Object> register(RegisterDto req);

    /**
     * 用户注销
     *
     * @return 注销结果
     */
    Result<Object> logout();

    /**
     * 更新用户密码
     *
     * @param req 更新密码请求参数
     * @return 更新结果
     */
    Result<Object> updatePassword(UpdatePasswordDto req);

    Result<String> test();

}
