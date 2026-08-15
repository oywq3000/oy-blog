package com.oyproj.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 忘记密码重置请求（未登录用户通过邮箱验证码重置）
 */
@Data
public class ResetPasswordDto {

    /**
     * 邮箱
     */
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$", message = "{register.email.invalid}")
    @NotBlank(message = "{register.email.notBlank}")
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    /**
     * 邮箱验证码
     */
    @NotBlank(message = "{register.emailCode.notBlank}")
    @Schema(description = "邮箱验证码", example = "123456")
    private String emailCode;

    /**
     * 新密码
     */
    @NotBlank(message = "{password.new.notBlank}")
    @Schema(description = "新密码", example = "NewPass123")
    private String newPassword;

    /**
     * 确认新密码
     */
    @NotBlank(message = "{password.confirm.notBlank}")
    @Schema(description = "确认新密码", example = "NewPass123")
    private String confirmPassword;
}
