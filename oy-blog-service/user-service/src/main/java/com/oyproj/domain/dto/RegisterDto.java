package com.oyproj.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterDto {
    /**
     * 用户名
     */
    @NotBlank(message = "{register.username.notBlank}")
    @Schema(description = "用户名", example = "admin")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "{register.password.notBlank}")
    @Schema(description = "密码", example = "123456")
    private String password;

    /**
     * 确认密码
     */
    @NotBlank(message = "{register.confirmPassword.notBlank}")
    @Schema(description = "确认密码", example = "123456")
    private String confirmPassword;

    /**
     * 邮箱
     */
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$", message = "{register.email.invalid}")
    @NotBlank(message = "{register.email.notBlank}")
    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    /**
     * 注册IP地址
     */
    @NotBlank(message = "{register.ipAddress.notBlank}")
    @Schema(description = "注册IP地址", example = "127.0.0.1")
    private String ipAddress;
}
