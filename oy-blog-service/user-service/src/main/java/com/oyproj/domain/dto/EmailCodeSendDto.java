package com.oyproj.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送邮箱验证码请求
 */
@Data
public class EmailCodeSendDto {

    /**
     * 邮箱
     */
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$", message = "{register.email.invalid}")
    @NotBlank(message = "{register.email.notBlank}")
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    /**
     * 验证码用途：null=注册验证码；"reset"=重置密码验证码
     */
    @Schema(description = "用途：null=注册，reset=重置密码", example = "reset")
    private String purpose;
}
