package com.oyproj.service.impl;

import com.oyproj.constant.MessageTypeEnum;
import com.oyproj.domain.dto.MessageSendDto;
import com.oyproj.service.EmailService;
import com.oyproj.service.MessageService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 *  邮件服务实现
 */
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    @NotNull private final MessageService messageService;
    @Value("${app.host:http://localhost:8080}")
    private String appHost;

    /**
     * 发送邮箱验证邮件
     * @param email 收件邮箱
     * @param verifyUrl 验证链接
     */
    @Override
    public void sendVerificationEmail(String userId, String email, String verifyUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put("username", userId);
        model.put("verifyUrl", verifyUrl);
        model.put("expireHours", 24);

        MessageSendDto sendDto = MessageSendDto.builder()
                .messageType(MessageTypeEnum.EMAIL)
                .to(email)
                .subject("邮箱验证")
                .template("templates/mail/verify-email.html")
                .params(model)
                .bizId(userId)
                .bizType("verify_code")
                .build();

        messageService.sendMessage(sendDto);
    }
}