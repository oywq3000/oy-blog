package com.oyproj.controller;

import com.oyproj.domain.dto.ChatStreamRequest;
import com.oyproj.service.AgentChatService;
import com.oyproj.utils.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 聊天流式接口（SSE）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class AgentChatController {

    private final AgentChatService chatService;

    /**
     * 发送消息，流式返回 token/thinking/done/error 事件
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatStreamRequest req) {
        return chatService.streamChat(req, CurrentUserUtil.getUserId());
    }
}
