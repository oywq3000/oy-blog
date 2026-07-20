package com.oyproj.common.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 请求/响应日志过滤器 — 完全无侵入，自动拦截所有 Servlet 请求并记录详细输入输出
 *
 * <p>通过 {@link LoggingAutoConfiguration} 自动注册，仅在 Servlet Web 环境下生效。</p>
 */
public class LoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger("com.oyproj.common.logging.access");

    /** 需要脱敏的请求头 */
    private static final java.util.Set<String> SENSITIVE_HEADERS = new java.util.HashSet<>(
            java.util.Arrays.asList("authorization", "x-user-data", "cookie", "set-cookie")
    );

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    // ==================== 请求头格式化 ====================

    /**
     * 格式化请求头，敏感头脱敏
     */
    private Map<String, String> sanitizeHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return headers;
        }
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            if (value == null) {
                headers.put(name, null);
                continue;
            }
            if (SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, value.length() > 10 ? value.substring(0, 3) + "***" : "***");
            } else {
                headers.put(name, value);
            }
        }
        return headers;
    }

    // ==================== LOGGER NAME ====================
    // Static so LoggingUtils can reference; used by logback-spring.xml to route to ACCESS appender
    static final String ACCESS_LOGGER = "com.oyproj.common.logging.access";

    // ==================== FILTER ====================

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        String queryString = httpRequest.getQueryString();

        // 排除路径不记录
        if (LoggingUtils.isExcludedPath(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // 包装 request/response 以缓存内容
        ContentCachingRequestWrapper wrappedRequest = wrapRequest(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        // traceId 生成/传播
        String traceId = resolveTraceId(httpRequest);
        MDC.put(MDC_TRACE_KEY, traceId);
        httpResponse.setHeader(TRACE_HEADER, traceId);

        long startTime = System.currentTimeMillis();
        boolean hasException = false;

        try {
            // ===== 记录请求 =====
            String contentType = httpRequest.getContentType();
            boolean skipBody = LoggingUtils.shouldSkipBody(contentType);

            Map<String, String> headers = sanitizeHeaders(httpRequest);
            String clientIp = getClientIp(httpRequest);

            log.info("[REQUEST] {} {} | query={} | headers={} | body={} | client={}",
                    method,
                    uri,
                    queryString != null ? queryString : "NONE",
                    headers,
                    skipBody ? "[BINARY]" : "[NOT_READ_YET]",
                    clientIp);

            // 执行后续过滤器链
            chain.doFilter(wrappedRequest, wrappedResponse);

        } catch (Exception e) {
            hasException = true;
            long duration = System.currentTimeMillis() - startTime;
            log.error("[RESPONSE] {} {} | status=EXCEPTION | duration={}ms | error={}",
                    method, uri, duration, e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            if (!hasException) {
                int status = wrappedResponse.getStatus();

                // 读取请求体（在 chain 执行后才能从缓存中读取）
                String reqBody = readRequestBody(wrappedRequest, httpRequest.getContentType());
                // 读取响应体
                String respBody = readResponseBody(wrappedResponse);

                log.info("[RESPONSE] {} {} | status={} | duration={}ms | reqBody={} | respBody={}",
                        method, uri, status, duration, reqBody, respBody);
            }

            // 关键：将缓存的响应体写回真正的输出流
            try {
                wrappedResponse.copyBodyToResponse();
            } catch (IOException e) {
                log.error("Failed to copy response body back to client", e);
            }
            MDC.clear();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 包装 HttpServletRequest 为 ContentCachingRequestWrapper
     * 注意：GET 请求不需要包装 body
     */
    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper) {
            return (ContentCachingRequestWrapper) request;
        }
        return new ContentCachingRequestWrapper(request);
    }

    /**
     * 从请求缓存中读取 body
     */
    private String readRequestBody(ContentCachingRequestWrapper wrapper, String contentType) {
        if (LoggingUtils.shouldSkipBody(contentType)) {
            return "[BINARY]";
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return "[EMPTY]";
        }
        String body = new String(content, StandardCharsets.UTF_8);
        return LoggingUtils.truncate(LoggingUtils.maskSensitive(body));
    }

    /**
     * 从响应缓存中读取 body
     */
    private String readResponseBody(ContentCachingResponseWrapper wrapper) {
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return "[EMPTY]";
        }
        // 不强制截断响应体，但截断过长的（如文件内容）
        if (content.length > 4096) {
            return "[LARGE_BODY " + content.length + " bytes]";
        }
        String contentType = wrapper.getContentType();
        if (LoggingUtils.shouldSkipBody(contentType)) {
            return "[BINARY]";
        }
        String body = new String(content, StandardCharsets.UTF_8);
        return LoggingUtils.truncate(LoggingUtils.maskSensitive(body));
    }

    /**
     * 解析 traceId：优先从请求头获取，否则新生成
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        return LoggingUtils.generateTraceId();
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
