package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.EmailCodeSendDto;
import com.oyproj.service.EmailVerifyBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 邮箱验证接口：注册验证码发送 + 验证链接（request/confirm/status）
 */
@Tag(name = "EmailVerifyController", description = "邮箱验证码发送与邮箱验证")
@RestController
@RequiredArgsConstructor
@RequestMapping("/email/verification")
public class EmailVerifyController {
    private final EmailVerifyBizService emailVerifyBiz;

    /**
     * 向未注册邮箱发送 6 位数字验证码（5 分钟有效）
     */
    @PostMapping("/send-code")
    @Operation(summary = "发送注册验证码", description = "向指定邮箱发送 6 位数字验证码（5 分钟有效）")
    public Result<Object> sendCode(@RequestBody @Valid EmailCodeSendDto dto) {
        return emailVerifyBiz.sendCode(dto);
    }

    /**
     * 当前登录用户请求发送邮箱验证链接邮件（24 小时有效）
     */
    @PostMapping("/request")
    @Operation(summary = "请求邮箱验证邮件", description = "向当前登录用户发送验证链接邮件（24 小时有效）")
    public Result<Object> request() {
        return emailVerifyBiz.request();
    }

    /**
     * 通过邮件中的链接 token 完成邮箱验证
     */
    @PostMapping("/confirm")
    @Operation(summary = "确认邮箱验证", description = "通过邮件中的链接 token 完成邮箱验证")
    public Result<Object> confirm(@RequestParam("token") String token) {
        return emailVerifyBiz.confirm(token);
    }

    /**
     * 查询当前用户的邮箱验证状态
     */
    @GetMapping("/status")
    @Operation(summary = "查询邮箱验证状态", description = "查询当前登录用户的邮箱验证状态")
    public Result<Boolean> status() {
        return emailVerifyBiz.status();
    }
}
