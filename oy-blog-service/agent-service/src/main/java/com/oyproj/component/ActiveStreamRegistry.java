package com.oyproj.component;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 进行中的流式对话登记表（内存态，单机部署够用；重启即失效，Python 流随 TCP 断开自然终止）
 */
@Component
public class ActiveStreamRegistry {

    /**
     * 活跃流：SSE 发射器 + WebClient 订阅句柄（订阅稍后创建，用 AtomicReference 回填）
     */
    public record ActiveStream(SseEmitter emitter, AtomicReference<Disposable> subscription) {
    }

    private final Map<String, ActiveStream> streams = new ConcurrentHashMap<>();

    /**
     * 登记活跃流；若该会话已有活跃流返回 false
     */
    public boolean register(String conversationId, ActiveStream stream) {
        return streams.putIfAbsent(conversationId, stream) == null;
    }

    public ActiveStream get(String conversationId) {
        return streams.get(conversationId);
    }

    public void remove(String conversationId) {
        streams.remove(conversationId);
    }
}
