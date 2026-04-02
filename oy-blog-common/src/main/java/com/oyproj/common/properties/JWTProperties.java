package com.oyproj.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "oyproj.common.jwt")
public class JWTProperties {
     public String securityKey;
     public long accessTokenExpireTime;
     public long refreshTokenExpireTime;
}
