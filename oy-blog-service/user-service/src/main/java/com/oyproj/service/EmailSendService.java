package com.oyproj.service;

/**
 * 邮件发送服务（验证码 / 验证链接）
 */
public interface EmailSendService {

    /**
     * 发送注册验证码邮件
     *
     * @param to   收件邮箱
     * @param code 6 位数字验证码
     */
    void sendVerifyCode(String to, String code);

    /**
     * 发送邮箱验证链接邮件
     *
     * @param to        收件邮箱
     * @param verifyUrl 验证链接
     * @param username  收件人用户名
     */
    void sendVerifyLink(String to, String verifyUrl, String username);
}
