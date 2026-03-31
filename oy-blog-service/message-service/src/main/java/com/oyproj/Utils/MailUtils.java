package com.oyproj.Utils;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.util.Map;

/**
 * @description 邮件发送工具类，支持 Thymeleaf 模板渲染
 */
@Component
@RequiredArgsConstructor
public class MailUtils {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    /**
     * 发送模板邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param template 模板路径（如：templates/mail/verify-email.html）
     * @param model 模板变量
     */
    public void sendTemplateMail(String to, String subject, String template, Map<String, Object> model) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            Context context = new Context();
            if (model != null) {
                model.forEach(context::setVariable);
            }
            String html = templateEngine.process(template, context);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}