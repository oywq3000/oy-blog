package com.oyproj.filter;

import com.oyproj.properties.AuthProperties;
import com.oyproj.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {
    private final AuthProperties authProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // 跳过认证路径
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }
        
        // 从请求头获取令牌
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || !token.startsWith("Bearer ")) {
            // 没有令牌，直接通过，由下游服务处理
            return chain.filter(exchange);
        }
        
        // 提取令牌
        token = token.substring(7);
        
        // 验证令牌
        if (!JwtUtil.validateToken(token)) {
            // 令牌无效，直接通过，由下游服务处理
            return chain.filter(exchange);
        }
        
        // 从令牌中获取用户ID
        String userId = JwtUtil.getUserIdFromToken(token);
        
        // 将用户信息添加到请求头中
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId)
                .build();
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100; // 优先级高于默认过滤器
    }
    private boolean isWhitelisted(String path) {
        return authProperties.getWhitelist().stream().anyMatch(path::startsWith);
    }
}