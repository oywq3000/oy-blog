package com.oyproj.utils;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 邮箱验证相关随机值生成工具
 */
public final class VerifyCodeUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerifyCodeUtils() {
    }

    /**
     * 生成 6 位数字验证码
     */
    public static String genCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /**
     * 生成邮箱验证链接 token
     */
    public static String genToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
