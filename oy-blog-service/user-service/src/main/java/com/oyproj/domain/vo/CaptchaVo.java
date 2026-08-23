package com.oyproj.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图形验证码返回
 */
@Data
@AllArgsConstructor
public class CaptchaVo {

    /**
     * 验证码 ID：发送邮箱验证码时与答案一起提交
     */
    @Schema(description = "图形验证码 ID，发送邮箱验证码时与答案一起提交")
    private String captchaId;

    /**
     * 验证码图片（data:image/png;base64,...）
     */
    @Schema(description = "验证码图片 data URI")
    private String captchaImg;
}
