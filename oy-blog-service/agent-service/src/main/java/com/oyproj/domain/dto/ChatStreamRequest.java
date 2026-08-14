package com.oyproj.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天流式请求（对齐前端 ChatInput 发送体）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamRequest {

    /**
     * 会话ID（前端生成 conv_*）
     */
    private String conversationId;

    /**
     * 用户消息内容
     */
    private String message;

    /**
     * 是否深度思考
     */
    private Boolean deepThinking;

    /**
     * 模型标识
     */
    private String model;
}
