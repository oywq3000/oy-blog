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
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
        // text/event-stream 无匹配的 StringDecoder，用 DataBuffer 裸字节流解码。
        // 注意：不能用 CharsetDecoder.decode(ByteBuffer) 便捷方法——它等价于
        // decode(in, out, true) 且调用后 reset 状态，网络把多字节字符切成两块时
        // 每块都会把半个字符当作 malformed 替换成 U+FFFD（正是本次修复的乱码根因）。
        // 正确姿势：三参 decode(in, out, endOfInput=false) 保持状态，并把解码完后
        // 仍留在输入里未消费的半个字符字节拼到下一块，流末尾另做一次 flush。
        Utf8StreamDecoder decoder = new Utf8StreamDecoder();
        return pythonWebClient.post()
                .uri("/chat/stream")
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .subscribe(
                        buffer -> {
                            try {
                                ByteBuffer in = buffer.toByteBuffer();
                                String out = decoder.decode(in);
                                if (!out.isEmpty()) {
                                    parser.feed(out);
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
                        () -> {
                            // 所有 chunk 之后：把可能残留在解码器里的尾部半个字符 flush 出来
                            parser.feed(decoder.finish());
                            parser.streamEnded();
                        }
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

    /**
     * 有状态的 UTF-8 流式解码器：正确处理被网络切成两半的多字节字符。
     *
     * 坑：CharsetDecoder 便捷方法 {@code decode(ByteBuffer)} 等价于
     * {@code decode(in, out, true)} 且每次调用后 reset —— 一个 3 字节汉字被
     * TCP/WebClient 切成两半时，前一半会被当作 malformed 替换成 U+FFFD（乱码）。
     * 本类采用三参 {@code decode(in, out, false)} 保持解码状态，解码后把输入里
     * 未消费的尾部字节（半个字符）缓存起来拼进下一块；流结束再用
     * {@code endOfInput=true} flush 收尾。
     *
     * 与 WebClient SSE 定位一致：仅当 Python 上游用标准 JSON（ensure_ascii=False）
     * 输出纯 UTF-8 时有效；非 UTF-8 字节仍按 REPLACE 策略替换成 U+FFFD。
     */
    static class Utf8StreamDecoder {

        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        private final CharBuffer out = CharBuffer.allocate(4096);
        /** 上一块解码后未消费的半个字符字节（可能为 0 长度） */
        private byte[] leftover = new byte[0];

        /** 解码一块原始字节，返回这次能安全输出的完整字符（不含半截多字节字符） */
        String decode(ByteBuffer in) {
            // 合并上次的半个字符 + 本次块
            byte[] merged;
            int carryLen = leftover.length;
            int thisLen = in.remaining();
            merged = new byte[carryLen + thisLen];
            if (carryLen > 0) {
                System.arraycopy(leftover, 0, merged, 0, carryLen);
            }
            in.get(merged, carryLen, thisLen);
            ByteBuffer buf = ByteBuffer.wrap(merged);

            StringBuilder sb = new StringBuilder(Math.max(16, merged.length));
            CoderResult result;
            do {
                out.clear();
                result = decoder.decode(buf, out, false);
                out.flip();
                sb.append(out);
            } while (result == CoderResult.OVERFLOW);

            // 把可能留在输入里未消费的尾部（半个字符）缓存到下一块
            if (buf.hasRemaining()) {
                leftover = Arrays.copyOfRange(merged, merged.length - buf.remaining(), merged.length);
            } else {
                leftover = new byte[0];
            }
            return sb.toString();
        }

        /** 流结束 flush：把残留在输入里的尾部半个字符一并解出（endOfInput=true） */
        String finish() {
            if (leftover.length == 0) {
                return "";
            }
            ByteBuffer tail = ByteBuffer.wrap(leftover);
            out.clear();
            decoder.decode(tail, out, true);
            out.flip();
            // 正常流结束不会有未消费尾部；若真有（上游字节确实被切断），
            // endOfInput=true 下 REPLACE 策略兜底成 U+FFFD，属数据自身损坏。
            return out.toString();
        }
    }
}
