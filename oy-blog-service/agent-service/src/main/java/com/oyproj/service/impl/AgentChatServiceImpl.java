package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.component.ActiveStreamRegistry;
import com.oyproj.component.PythonSseClient;
import com.oyproj.domain.dto.ChatStreamRequest;
import com.oyproj.domain.entity.AgentConversation;
import com.oyproj.domain.entity.AgentMessage;
import com.oyproj.mapper.AgentConversationMapper;
import com.oyproj.mapper.AgentMessageMapper;
import com.oyproj.service.AgentChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final int HISTORY_SIZE = 20;

    private final AgentConversationMapper conversationMapper;  //会话表
    private final AgentMessageMapper messageMapper;  //消息表
    private final ActiveStreamRegistry registry;  //登记表：记录哪些会话正在生成中
    private final PythonSseClient pythonSseClient; //调用Python的客户端
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter streamChat(ChatStreamRequest req, String userId) {
        String conversationId = req.getConversationId();         //获取会话id
        String message = req.getMessage() == null ? "" : req.getMessage().trim();
        SseEmitter emitter = new SseEmitter(0L);  //创建SSE发射器：参数是超时毫秒数，0=不设超时限制
        // 参数校验
        if (conversationId == null || conversationId.isBlank() || message.isEmpty()) {
            sendEvent(emitter, "error", Map.of("code", 400, "message", "参数不完整"));//推一个error事件
            emitter.complete(); //主动关闭
            return emitter;
        }
        // 同会话已有流在生成
        if (registry.get(conversationId) != null) {
            sendEvent(emitter, "error", Map.of("code", 409, "message", "已有对话在生成中，请稍候"));
            emitter.complete();
            return emitter;
        }

        // 1. 会话 upsert（owner 校验：存在但不属于当前用户按不存在处理）
        AgentConversation conv = conversationMapper.selectById(conversationId);
        if (conv != null && !conv.getUserId().equals(userId)) {
            sendEvent(emitter, "error", Map.of("code", 404, "message", "会话不存在"));
            emitter.complete();
            return emitter;
        }
        LocalDateTime now = LocalDateTime.now();
        if (conv == null) {
            conv = AgentConversation.builder()
                    .id(conversationId)
                    .userId(userId)
                    .title(truncateTitle(message))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            conversationMapper.insert(conv);
        } else {
            // 首条消息自动命名
            if (DEFAULT_TITLE.equals(conv.getTitle())) {   //标题还是默认的“新对话”
                conv.setTitle(truncateTitle(message));     //用消息内容主动命名
            }
            conv.setUpdatedAt(now);                        //刷新最后活跃时间
            conversationMapper.updateById(conv);
        }

        // 2. 落库用户消息（id 由 ASSIGN_UUID 生成）
        messageMapper.insert(AgentMessage.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role("user")
                .content(message)
                .createdAt(now)
                .build());

        // 3. 最近 20 条历史（升序）
        List<AgentMessage> historyMsgs = messageMapper.selectList(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getConversationId, conversationId)
                        .orderByDesc(AgentMessage::getCreatedAt)
                        .last("LIMIT " + HISTORY_SIZE));
        Collections.reverse(historyMsgs);  // 反转成正序（最旧在前），因为 LLM 需要按时间顺序读
        List<Map<String, String>> history = historyMsgs.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent() == null ? "" : m.getContent()))
                .collect(Collectors.toList());  // 转成 Python 端要的格式：[{role, content}, ...]

        // 4. 登记活跃流
        ActiveStreamRegistry.ActiveStream active =
                new ActiveStreamRegistry.ActiveStream(emitter, new AtomicReference<>());
        if (!registry.register(conversationId, active)) {
            sendEvent(emitter, "error", Map.of("code", 409, "message", "已有对话在生成中，请稍候"));
            emitter.complete();
            return emitter;
        }

        // 5. 订阅 Python 流
        StringBuilder content = new StringBuilder();     //拼接AI正文
        StringBuilder thinking = new StringBuilder();    //拼接AI思考过程
        AtomicLong thinkingStart = new AtomicLong(0); //第一个思考token的时间戳，0=还没开始
        AtomicBoolean finished = new AtomicBoolean(false); //是否已经结束（防止重复收尾）

        // 流结束：落库 assistant 消息并发送 done（消息 id 由 Java 生成，忽略 Python 的 messageId）
        // 这是一个Runnable，被触发时执行收尾
        Runnable finish = () -> {
            //原子地 false→true；返回 false 说明别的
            if (!finished.compareAndSet(false, true)) {
                return;  //幂等只收尾一次
            }
            int thinkingTime = thinkingStart.get() > 0
                    ? (int) ((System.currentTimeMillis() - thinkingStart.get()) / 1000)
                    : 0;
            AgentMessage assistantMsg = AgentMessage.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .role("assistant")
                    .content(content.toString())
                    .thinking(thinking.length() > 0 ? thinking.toString() : null)
                    .thinkingTime(thinkingTime > 0 ? thinkingTime : null)
                    .createdAt(LocalDateTime.now())
                    .build();
            messageMapper.insert(assistantMsg);
            registry.remove(conversationId);
            sendEvent(emitter, "done", Map.of("messageId", assistantMsg.getId()));
            emitter.complete();
        };

        Disposable subscription = pythonSseClient.streamChat(buildPayload(req, userId, message, history),
                new PythonSseClient.StreamListener() {
                    @Override
                    public void onToken(String token) {
                        if (token == null) {
                            return;
                        }
                        content.append(token);
                        sendEvent(emitter, "token", Map.of("content", token));
                    }
                    @Override
                    public void onThinking(String t) {
                        if (t == null) {
                            return;
                        }
                        if (thinkingStart.get() == 0) {
                            thinkingStart.set(System.currentTimeMillis());
                        }
                        thinking.append(t);
                        sendEvent(emitter, "thinking", Map.of("content", t));
                    }
                    @Override
                    public void onDone(String pythonMessageId) {
                        finish.run();
                    }
                    @Override
                    public void onError(int code, String message) {
                        // 失败不落库 assistant 消息，用户消息已保存
                        if (finished.compareAndSet(false, true)) {
                            registry.remove(conversationId);
                            sendEvent(emitter, "error", Map.of("code", code, "message", message));
                            emitter.complete();
                        }
                    }
                });
        active.subscription().set(subscription);

        // 6. 客户端断开/超时清理：取消订阅 + 通知 Python 停止
        Runnable cleanup = () -> {
            registry.remove(conversationId);
            if (!finished.get()) {
                Disposable d = active.subscription().get();
                if (d != null) {
                    d.dispose();
                }
                pythonSseClient.stopChat(conversationId);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    @Override
    public void stop(String conversationId) {
        ActiveStreamRegistry.ActiveStream active = registry.get(conversationId);
        if (active != null) {
            Disposable d = active.subscription().get();
            if (d != null) {
                d.dispose();
            }
            registry.remove(conversationId);
            // 结束 SSE 响应，让仍在监听的客户端正常收尾（前端通常会先 abort 本地 fetch，此调用幂等）
            try {
                active.emitter().complete();
            } catch (Exception ignored) {
                // 客户端已断开，忽略
            }
        }
        pythonSseClient.stopChat(conversationId);
    }

    private Map<String, Object> buildPayload(ChatStreamRequest req, String userId, String message,
                                             List<Map<String, String>> history) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", req.getConversationId());
        payload.put("userId", userId);
        payload.put("message", message);
        payload.put("history", history);
        payload.put("deepThinking", Boolean.TRUE.equals(req.getDeepThinking()));
        payload.put("model", req.getModel() == null || req.getModel().isBlank() ? "default" : req.getModel());
        return payload;
    }

    private String truncateTitle(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 20 ? cleaned.substring(0, 20) + "…" : cleaned;
    }

    /**
     * 发送 SSE 事件；data 传 JSON 字符串，与前端解析器（event:/data: 前缀）匹配
     */
    private void sendEvent(SseEmitter emitter, String name, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.debug("sse send failed (client gone?): {}", e.getMessage());
        }
    }
}
