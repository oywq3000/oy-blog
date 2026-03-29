package com.oyproj.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {
    
    // 密钥，实际项目中应从配置文件读取
    private static final SecretKey SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    
    // 访问令牌过期时间：2小时
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 2 * 60 * 60 * 1000;
    
    // 刷新令牌过期时间：7天
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000;
    
    /**
     * 生成访问令牌
     * @param userDetails 用户详情
     * @return 访问令牌
     */
    public static String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userDetails.getUsername());
        claims.put("iat", new Date());
        claims.put("exp", new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE_TIME));
        
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SECRET_KEY)
                .compact();
    }
    
    /**
     * 生成刷新令牌
     * @param userDetails 用户详情
     * @return 刷新令牌
     */
    public static String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userDetails.getUsername());
        claims.put("iat", new Date());
        claims.put("exp", new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE_TIME));
        
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SECRET_KEY)
                .compact();
    }
    
    /**
     * 解析令牌
     * @param token 令牌
     * @return 声明
     */
    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    /**
     * 获取访问令牌过期时间
     * @return 过期时间（秒）
     */
    public static long getAccessTokenExpireTime() {
        return ACCESS_TOKEN_EXPIRE_TIME / 1000;
    }
}