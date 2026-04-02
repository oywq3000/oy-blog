package com.oyproj.common.config;

import com.oyproj.common.properties.JWTProperties;
import com.oyproj.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * JWT配置类
 */
@Configuration
@RequiredArgsConstructor
public class JWTConfig {
    
    private final JWTProperties jwtProperties;
    
    @PostConstruct
    public void init() {
        // 初始化JWT配置
        JwtUtil.setConfig(
            jwtProperties.getSecurityKey(),
            jwtProperties.getAccessTokenExpireTime(),
            jwtProperties.getRefreshTokenExpireTime()
        );
    }
}