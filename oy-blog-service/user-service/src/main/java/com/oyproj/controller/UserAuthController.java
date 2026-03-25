package com.oyproj.controller;


import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证接口
 */
@Tag(name = "UserAuthController", description = "用户登录、注册、信息查询、注销等操作")
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/auth")
public class UserAuthController {
    private final UserAuthBizService authBiz;
}
