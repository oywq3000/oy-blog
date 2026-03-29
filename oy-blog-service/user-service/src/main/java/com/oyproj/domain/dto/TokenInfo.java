package com.oyproj.domain.dto;

import lombok.Data;

@Data
public class TokenInfo {
    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 令牌类型
     */
    private String tokenType;

    /**
     * 过期时间（秒）
     */
    private long expiresIn;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;
}
