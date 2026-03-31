package com.oyproj.domain.dto;

import com.oyproj.constant.MessageTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 *  消息发送传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendDto {

    /**
     * 消息类型
     */
    private MessageTypeEnum messageType;

    /**
     * 接收者（邮箱地址/OpenID/用户ID等）
     */
    private String to;

    /**
     * 标题
     */
    private String subject;

    /**
     * 纯文本内容
     */
    private String content;

    /**
     * 模板路径（可选）
     */
    private String template;

    /**
     * 模板参数（可选）
     */
    private Map<String, Object> params;

    /**
     * 业务ID（关联ID，如文章ID，可选）
     */
    private String bizId;

    /**
     * 业务类型（如：verify_code, new_comment等）
     */
    private String bizType;
}
