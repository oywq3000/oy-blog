package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.EmailCodeSendDto;
import com.oyproj.domain.vo.CaptchaVo;

/**
 * 邮箱验证业务：注册验证码 + 验证链接
 */
public interface EmailVerifyBizService {

    /**
     * 向未注册邮箱发送 6 位数字验证码（5 分钟有效）
     */
    Result<Object> sendCode(EmailCodeSendDto dto);

    /**
     * 当前登录用户请求发送邮箱验证链接邮件（24 小时有效）
     */
    Result<Object> request();

    /**
     * 通过验证链接 token 完成邮箱验证
     */
    Result<Object> confirm(String token);

    /**
     * 查询当前用户的邮箱验证状态
     */
    Result<Boolean> status();

    /**
     * 获取图形验证码（发送邮箱验证码前的人机验证，2 分钟有效、一次性使用）
     */
    Result<CaptchaVo> captcha();
}
