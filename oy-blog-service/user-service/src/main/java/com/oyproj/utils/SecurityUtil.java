package com.oyproj.utils;

import com.oyproj.domain.dto.TokenInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public class SecurityUtil {

    /**
     * 获取当前登录用户ID
     * @return 用户ID字符串
     */
    public static String getLoginIdAsString() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        // 假设用户详情实现类中包含ID字段
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername(); // 或根据实际情况获取ID
        }
        return principal.toString();
    }

    /**
     * 检查用户是否已登录
     * @return 是否已登录
     */
    public static boolean isLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * 登出当前用户
     */
    public static void logout() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 获取令牌信息
     * @return 令牌信息对象
     */
    public static TokenInfo getTokenInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserDetails)) {
            return null;
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String userId = userDetails.getUsername();
        String username = userDetails.getUsername(); // 假设用户名就是用户ID，实际项目中可能需要从用户详情中获取
        // 生成访问令牌和刷新令牌
        String accessToken = JwtUtil.generateAccessToken(userDetails);
        String refreshToken = JwtUtil.generateRefreshToken(userDetails);
        // 构建TokenInfo对象
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setAccessToken(accessToken);
        tokenInfo.setTokenType("Bearer");
        tokenInfo.setExpiresIn(JwtUtil.getAccessTokenExpireTime());
        tokenInfo.setRefreshToken(refreshToken);
        tokenInfo.setUserId(userId);
        tokenInfo.setUsername(username);
        return tokenInfo;
    }

    /**
     * 登录用户
     * @param id 用户ID
     */
    public static void login(String id) {

        Authentication authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        id, null, java.util.Collections.emptyList()
                );

        // 将认证对象设置到安全上下文中
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}