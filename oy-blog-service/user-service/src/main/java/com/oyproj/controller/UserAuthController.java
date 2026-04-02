package com.oyproj.controller;


import com.oyproj.common.base.OpLog;
import com.oyproj.common.base.Result;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.domain.dto.LoginDto;
import com.oyproj.domain.dto.RegisterDto;
import com.oyproj.domain.dto.TokenInfo;
import com.oyproj.domain.dto.UpdatePasswordDto;
import com.oyproj.service.UserAuthBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证接口
 */
@Tag(name = "UserAuthController", description = "用户登录、注册、信息查询、注销等操作")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class UserAuthController {
    private final UserAuthBizService authBiz;
    /**
     * 用户登录
     * @return
     */
    @PostMapping("/login")
    @OpLog(action = "login", func = "auth.login")
    @Operation(summary = "用户登录", description = "用户通过用户名和密码登录系统")
    public Result<TokenInfo> doLogin(@RequestBody LoginDto req) {
        return authBiz.login(req);
    }

    /**
     * 用户注册
     * @return
     */
    @PostMapping("/register")
    @OpLog(action = "register", func = "user.auth.register")
    @Operation(summary = "用户注册", description = "用户通过用户名、密码、邮箱注册系统")
    public Result<Object> doRegister(@RequestBody RegisterDto req) {
        return authBiz.register(req);
    }

    /**
     * 用户注销登录
     *
     * @return 注销结果
     */
    @PostMapping("/logout")
    @OpLog(action = "logout", func = "user.auth.logout")
    @Operation(summary = "用户注销登录", description = "用户注销登录系统")
    public Result<Object> logout() {
        return authBiz.logout();
    }

    /**
     * 更新用户密码
     *
     * @param req 更新密码请求参数
     * @return 更新结果
     */
    @PostMapping("/password/update")
    @OpLog(action = "update_password", func = "user.auth.updatePassword")
    @Operation(summary = "更新用户密码", description = "用户通过旧密码更新为新密码")
    public Result<Object> updatePassword(@RequestBody UpdatePasswordDto req) {
        return authBiz.updatePassword(req);
    }



    @GetMapping
    public Result<String> test(){
        return authBiz.test();
    }


}
