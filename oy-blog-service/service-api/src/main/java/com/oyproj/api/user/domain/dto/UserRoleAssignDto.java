package com.oyproj.api.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRoleAssignDto {
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    /** true=授予 ADMIN，false=收回 */
    @NotNull(message = "admin 标记不能为空")
    private Boolean admin;
}
