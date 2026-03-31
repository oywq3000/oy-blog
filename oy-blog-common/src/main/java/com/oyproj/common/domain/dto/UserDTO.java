package com.oyproj.common.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Service服务中User类的精简类，负责在不同服务中传递用户信息
 */
@Data
public class UserDTO implements Serializable {
    private String id;
    private String username;
    private Integer status;
    private String avatarUrl;
}