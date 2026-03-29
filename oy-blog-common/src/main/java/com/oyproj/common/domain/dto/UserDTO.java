package com.oyproj.common.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Service服务中User类的精简类，负责在不同服务中传递用户信息
 */
@Data
public class UserDTO {
    private String id;
    private String username;
    private String email;
    private Integer status;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private Integer emailVerified;
}