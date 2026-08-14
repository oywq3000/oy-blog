package com.oyproj.utils;

import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.security.domain.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具
 *
 * 网关注入 X-User-Id 头后，oy-blog-common 的 AuthFilter 会把
 * SecurityUser 放进 SecurityContext（游客为 guestId）。
 */
public class CurrentUserUtil {

    private CurrentUserUtil() {
    }

    /**
     * 获取当前用户 id（真实 userId 或游客 guestId）
     */
    public static String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.getUser().getId();
        }
        throw new UnAuthorizedException("未登录");
    }
}
