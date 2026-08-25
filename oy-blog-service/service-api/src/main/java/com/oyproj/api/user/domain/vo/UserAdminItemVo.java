package com.oyproj.api.user.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAdminItemVo {
    private String id;
    private String username;
    private String email;
    private Integer status;
    private String avatarUrl;
    /** 是否拥有 ADMIN 角色 */
    private Boolean admin;
    private LocalDateTime createdAt;
}
