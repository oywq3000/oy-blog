package com.oyproj.utils;

import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.domain.dto.SecurityUser;
import com.oyproj.domain.dto.TokenInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

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
        // 生成访问令牌和刷新令牌
        String accessToken = JwtUtil.generateAccessToken(userId);
        String refreshToken = JwtUtil.generateRefreshToken(userId);
        // 构建TokenInfo对象
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setAccessToken(accessToken);
        tokenInfo.setTokenType("Bearer");
        tokenInfo.setExpiresIn(JwtUtil.getAccessTokenExpireTime());
        tokenInfo.setRefreshToken(refreshToken);
        tokenInfo.setRefreshTokenExpiresIn(JwtUtil.getRefreshTokenExpireTime());
        tokenInfo.setUserId(userId);
        return tokenInfo;
    }

    /**
     * 登录用户
     * @param userDTO 用户传播信息
     */
    public static void login(UserDTO userDTO, List<GrantedAuthority> authorities) {

        //SecurityUser 对象只保留最少的用户数据
        SecurityUser securityUser = new SecurityUser(userDTO.getId(),userDTO.getStatus(),authorities);
        Authentication authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        securityUser, null,securityUser.getAuthorities()
                );

        // 将认证对象设置到安全上下文中
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}