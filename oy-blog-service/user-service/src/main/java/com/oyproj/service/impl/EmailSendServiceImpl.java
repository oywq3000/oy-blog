package com.oyproj.service.impl;

import com.oyproj.service.EmailSendService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * 邮件发送实现：JavaMailSender + Thymeleaf 模板渲染
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendServiceImpl implements EmailSendService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    /** 发件人地址：必须与 SMTP 登录账号（spring.mail.username）一致，否则 QQ/163 返回 501 */
    @Value("${spring.mail.username:}")
    private String emailFrom;

    @Override
    public void sendVerifyCode(String to, String code) {
        sendTemplateMail(to, "【OY Blog】注册验证码", "mail/verify-code",
                Map.of("code", code, "expireMinutes", 5));
    }

    @Override
    public void sendVerifyLink(String to, String verifyUrl, String username) {
        sendTemplateMail(to, "【OY Blog】邮箱验证", "mail/verify-email",
                Map.of("verifyUrl", verifyUrl, "username", username, "expireHours", 24));
    }

    /**
     * 发送模板邮件。模板名不带 templates/ 前缀（TemplateEngine 默认已配置 classpath:/templates/）。
     */
    private void sendTemplateMail(String to, String subject, String template, Map<String, Object> model) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            Context context = new Context();
            model.forEach(context::setVariable);
            String html = templateEngine.process(template, context);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            log.error("邮件发送失败, to: {}", to, e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
