package com.oyproj.filter;

import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.constant.CommonConstant;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.properties.AuthProperties;
import com.oyproj.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {
    private final AuthProperties authProperties;
    private final CommonCache commonCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        // 跳过认证路径
        if (isWhitelisted(path)) {
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(HeaderConstant.USER_ID.getValue(),CommonConstant.GUEST_ID.getValue())
                    .build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }
        // 从请求头获取令牌
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || !token.startsWith("Bearer ")) {
           return Mono.error(new UnAuthorizedException());
        }
        token = token.substring(7);
        Claims claims;
        try {
            // 从令牌中获取用户ID
             claims = JwtUtil.parseToken(token);
        } catch (Exception e) {
            // 无法解析令牌或获取用户信息，抛出未认证异常
            return Mono.error(new UnAuthorizedException());
        }
        //校验redis中是否有
        if(!commonCache.hasKey(claims.getSubject())){
            //Token过期
            return Mono.error(new BaseException(ResultCode.TOKEN_EXPIRE));
        }
        log.info("转发用户{}的请求",claims.getSubject());
        // 将用户信息添加到请求头中
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(HeaderConstant.USER_ID.getValue(), claims.getSubject())
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