package com.oyproj.filter;

import com.oyproj.common.logging.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 网关全局日志过滤器 — 记录所有经过网关的请求和响应（元数据级别）
 *
 * <p>WebFlux 环境下不缓存请求/响应体，仅记录 method、URI、headers、status、duration</p>
 */
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingFilter.class);

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    private static final java.util.Set<String> SENSITIVE_HEADERS = new java.util.HashSet<>(
            java.util.Arrays.asList("authorization", "cookie", "set-cookie", "x-user-data")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";

        // 排除路径不记录
        if (LoggingUtils.isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        // traceId 生成/传播
        String traceId = request.getHeaders().getFirst(TRACE_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = LoggingUtils.generateTraceId();
        }
        MDC.put(MDC_TRACE_KEY, traceId);

        // 在响应头中设置 traceId
        exchange.getResponse().getHeaders().set(TRACE_HEADER, traceId);

        long startTime = System.currentTimeMillis();
        String queryString = request.getURI().getRawQuery();
        Map<String, String> headers = sanitizeHeaders(request);
        String clientIp = getClientIp(request);

        // 记录请求
        log.info("[GATEWAY REQUEST] {} {} | query={} | headers={} | client={}",
                method, path,
                queryString != null ? queryString : "NONE",
                headers, clientIp);

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    long duration = System.currentTimeMillis() - startTime;
                    ServerHttpResponse response = exchange.getResponse();
                    Integer statusCode = response.getStatusCode() != null
                            ? response.getStatusCode().value() : null;

                    log.info("[GATEWAY RESPONSE] {} {} | status={} | duration={}ms",
                            method, path, statusCode, duration);
                    MDC.clear();
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[GATEWAY RESPONSE] {} {} | status=ERROR | duration={}ms | error={}",
                            method, path, duration, error.getMessage(), error);
                    MDC.clear();
                });
    }

    @Override
    public int getOrder() {
        // 在 AuthenticationFilter（-100）之后执行
        return -99;
    }

    // ==================== 内部方法 ====================

    private Map<String, String> sanitizeHeaders(ServerHttpRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.getHeaders().forEach((name, values) -> {
            String value = values.isEmpty() ? null : String.join(", ", values);
            if (value != null && SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, value.length() > 10 ? value.substring(0, 3) + "***" : "***");
            } else {
                headers.put(name, value);
            }
        });
        return headers;
    }

    private String getClientIp(ServerHttpRequest request) {
        // 尝试从代理头获取真实 IP
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getHostString)
                .orElse("unknown");
    }
}
