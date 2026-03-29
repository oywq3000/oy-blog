package com.oyproj.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新密码请求参数
 */
@Data
public class UpdatePasswordDto {
    /**
     * 旧密码
     */
    @NotNull(message = "{password.old.notBlank}")
    @Schema(description = "旧密码",example = "123456")
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "{password.new.notBlank}")
    @Schema(description = "新密码", example = "654321")
    private String newPassword;

    /**
     * 确认新密码
     */
    @NotBlank(message = "{password.confirm.notBlank}")
    @Schema(description = "确认新密码", example = "654321")
    private String confirmPassword;

}
