package com.oyproj.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Python Agent 调用客户端（SSE 流式 + 停止）
 *
 * Java↔Python 协议（见 scripts/agent_stub.py）：
 *   POST /chat/stream {conversationId, userId, message, history, deepThinking, model}
 *     -> text/event-stream，事件 token{content} / thinking{content} / done{messageId} / error{code,message}
 *   POST /chat/stop {conversationId}
 * 无鉴权，仅内网直连。接入真实 Python 服务只需替换 agent.python.base-url。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonSseClient {

    private final WebClient pythonWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Python 流式事件监听器
     */
    public interface StreamListener {

        void onToken(String content);

        void onThinking(String content);

        /**
         * 流正常结束（done 事件或连接关闭）；pythonMessageId 可能为 null（Java 侧自行生成消息 id）
         */
        void onDone(String pythonMessageId);

        /**
         * Python 主动报错或连接失败
         */
        void onError(int code, String message);
    }

    /**
     * 调用 Python /chat/stream，逐帧解析 SSE 回调监听器
     *
     * @return 订阅句柄，调用方 dispose 即可中断上游流
     */
    public Disposable streamChat(Map<String, Object> payload, StreamListener listener) {
        StreamParser parser = new StreamParser(listener);
        // text/event-stream 无匹配的 StringDecoder，用 DataBuffer 裸字节流解码，
        // CharsetDecoder 保持状态以正确处理跨 chunk 被截断的多字节字符
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return pythonWebClient.post()
                .uri("/chat/stream")
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .subscribe(
                        buffer -> {
                            try {
                                CharBuffer out = decoder.decode(buffer.toByteBuffer());
                                if (out.hasRemaining()) {
                                    parser.feed(out.toString());
                                }
                            } catch (Exception e) {
                                // 解码失败（REPLACE 策略下几乎不会发生）：跳过该 chunk
                                log.debug("decode chunk failed: {}", e.getMessage());
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        },
                        err -> {
                            log.warn("python chat stream failed: {}", err.getMessage());
                            listener.onError(503, "AI 服务暂不可用，请稍后重试");
                        },
                        parser::streamEnded
                );
    }

    /**
     * 通知 Python 停止生成（fire-and-forget）
     */
    public void stopChat(String conversationId) {
        pythonWebClient.post()
                .uri("/chat/stop")
                .bodyValue(Map.of("conversationId", conversationId))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                        resp -> log.info("python stop ack: {}", resp),
                        err -> log.warn("python stop failed: {}", err.getMessage())
                );
    }

    /**
     * SSE 帧解析器（每个流一个实例；回调在同一订阅的串行执行中，无需加锁）
     */
    private class StreamParser {

        private final StreamListener listener;
        private final StringBuilder buffer = new StringBuilder();
        private boolean finished = false;

        StreamParser(StreamListener listener) {
            this.listener = listener;
        }

        void feed(String chunk) {
            if (finished) {
                return;
            }
            buffer.append(chunk);
            int idx;
            while ((idx = buffer.indexOf("\n\n")) >= 0) {
                String frame = buffer.substring(0, idx);
                buffer.delete(0, idx + 2);
                handleFrame(frame);
                if (finished) {
                    return;
                }
            }
        }

        /**
         * 连接正常关闭但未收到 done/error（Python 提前结束）：补一个 done 兜底
         */
        void streamEnded() {
            if (!finished) {
                finished = true;
                listener.onDone(null);
            }
        }

        private void handleFrame(String frame) {
            String event = "";
            String data = null;
            for (String line : frame.split("\n")) {
                String l = line.trim();
                if (l.startsWith("event:")) {
                    event = l.substring(6).trim();
                } else if (l.startsWith("data:")) {
                    data = l.substring(5).trim();
                }
            }
            if (data == null) {
                return;
            }
            try {
                Map<String, Object> m = objectMapper.readValue(data,
                        new TypeReference<Map<String, Object>>() {
                        });
                switch (event) {
                    case "token" -> listener.onToken((String) m.get("content"));
                    case "thinking" -> listener.onThinking((String) m.get("content"));
                    case "done" -> {
                        finished = true;
                        listener.onDone((String) m.get("messageId"));
                    }
                    case "error" -> {
                        finished = true;
                        Object code = m.get("code");
                        listener.onError(code instanceof Number n ? n.intValue() : 500,
                                String.valueOf(m.getOrDefault("message", "Python 服务错误")));
                    }
                    default -> log.debug("ignore unknown sse event: {}", event);
                }
            } catch (Exception e) {
                log.warn("unparseable sse frame: {}", frame);
            }
        }
    }
}
