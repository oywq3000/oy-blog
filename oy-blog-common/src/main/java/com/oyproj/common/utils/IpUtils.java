package com.oyproj.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP工具类
 */
public class IpUtils {

    /**
     * 获取客户端真实 IP（优先级：X-Forwarded-For → X-Real-IP → RemoteAddr）
     *
     * @param request 当前请求
     * @return 客户端 IP，可能为 null（无法获取时）
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
