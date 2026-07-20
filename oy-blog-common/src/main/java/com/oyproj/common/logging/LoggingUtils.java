package com.oyproj.common.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志工具类 — 提供请求/响应体的脱敏、截断、排除判断等通用能力
 */
public final class LoggingUtils {

    private LoggingUtils() {
    }

    /** 请求/响应体最大日志长度 */
    private static final int DEFAULT_MAX_BODY_LENGTH = 2048;

    /** 需要脱敏的字段名（小写匹配） */
    private static final List<String> SENSITIVE_FIELDS = Arrays.asList(
            "password", "passwd", "secret", "token", "authorization",
            "access_token", "refresh_token", "api_key", "apikey", "credential"
    );

    /** 敏感字段值替换规则：匹配 \"field\":\"...\" 或 \"field\":\"...\"  */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(\"(?:" + String.join("|", SENSITIVE_FIELDS) + ")\"\\s*[:=]\\s*\")[^\",\\s}]+",
            Pattern.CASE_INSENSITIVE
    );

    /** 不需要记录 body 的 Content-Type */
    private static final List<String> BINARY_CONTENT_TYPES = Arrays.asList(
            "multipart/form-data",
            "application/octet-stream",
            "application/zip",
            "application/gzip",
            "image/",
            "video/",
            "audio/"
    );

    /** 不记录详细访问日志的路径前缀 */
    private static final List<String> EXCLUDED_PATH_PREFIXES = Arrays.asList(
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/v2/api-docs",
            "/swagger-resources",
            "/webjars",
            "/favicon.ico",
            "/health"
    );

    // ==================== 脱敏 ====================

    /**
     * 对 JSON 字符串中的敏感字段值进行脱敏
     */
    public static String maskSensitive(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        return SENSITIVE_PATTERN.matcher(body).replaceAll("$1****");
    }

    // ==================== 截断 ====================

    /**
     * 截断过长字符串，超出长度后追加 "... (truncated N chars)"
     */
    public static String truncate(String body) {
        return truncate(body, DEFAULT_MAX_BODY_LENGTH);
    }

    public static String truncate(String body, int maxLength) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        if (body.length() <= maxLength) {
            return body;
        }
        int truncated = body.length() - maxLength;
        return body.substring(0, maxLength) + "... (truncated " + truncated + " chars)";
    }

    // ==================== Content-Type 判断 ====================

    /**
     * 是否应该跳过 body 记录（二进制 / multipart）
     */
    public static boolean shouldSkipBody(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        for (String binary : BINARY_CONTENT_TYPES) {
            if (lower.contains(binary)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断某个 Content-Type 是否需要缓存请求体
     */
    public static boolean isCacheableContentType(String contentType) {
        return !shouldSkipBody(contentType);
    }

    // ==================== 路径排除 ====================

    /**
     * 判断请求路径是否应被排除（不记录详细日志）
     */
    public static boolean isExcludedPath(String uri) {
        if (uri == null) {
            return true;
        }
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成 traceId
     */
    public static String generateTraceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
