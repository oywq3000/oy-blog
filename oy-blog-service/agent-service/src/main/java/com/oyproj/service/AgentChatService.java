package com.oyproj.service;

import com.oyproj.domain.dto.ChatStreamRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 聊天流式业务接口
 */
public interface AgentChatService {

    /**
     * 发送消息并返回 SSE 流
     * <p>
     * 流程：会话 upsert -> 落库用户消息 -> 载入历史 -> 转发 Python 并逐帧透传
     */
    SseEmitter streamChat(ChatStreamRequest req, String userId);

    /**
     * 停止指定会话的生成（取消订阅 + 通知 Python）
     */
    void stop(String conversationId);
}
