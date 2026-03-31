package com.oyproj.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送邮箱验证邮件
     * @param email 收件邮箱
     * @param verifyUrl 验证链接
     */
    void sendVerificationEmail(String userId, String email, String verifyUrl);
}