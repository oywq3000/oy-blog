package com.oyproj.filter;


import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.JwtUtil;
import com.oyproj.domain.AuthenticationResult;
import com.oyproj.properties.AuthProperties;
import com.oyproj.utils.GuestUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;

import org.springframework.http.HttpHeaders;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
        log.info("转发:{}",path);

        //优先认证用户
        AuthenticationResult authResult = authenticateUser(request);
        if(authResult.isAuthenticated()){
            //用户认证成功
            log.debug("认证用户访问: {}, 用户ID: {}", path, authResult.getUserId());
            return handleAuthenticatedUser(exchange, chain, authResult);
        }
        //游客访问白名单路径
        if(isWhitelisted(path)){
            log.debug("游客访问白名单路径: {}", path);
            return handleGuestUser(exchange, chain);
        }
        //非白名单路径需要认证
        log.warn("未认证访问受保护路径: {}", path);
        return Mono.error(new UnAuthorizedException("需要认证"));
    }


    @Override
    public int getOrder() {
        return -100; // 优先级高于默认过滤器
    }
    private boolean isWhitelisted(String path) {
        return authProperties.getWhitelist().stream().anyMatch(path::startsWith);
    }

    private AuthenticationResult authenticateUser(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null) {
            return AuthenticationResult.unauthenticated();
        }
        try {
            token = token.substring(7);
            Claims claims = JwtUtil.parseToken(token);
            String userId = claims.getSubject();
            // 验证User是否在缓存中
            if (commonCache.hasKey(CachePrefix.USER_ID.getPrefix() + userId)) {
                return AuthenticationResult.authenticated(userId);
            }
            log.debug("Token已失效: {}", token);
            return AuthenticationResult.unauthenticated();

        } catch (Exception e) {
            log.debug("Token解析失败: {}", e.getMessage());
            return AuthenticationResult.unauthenticated();
        }
    }

    /**
     * 处理认证用户请求
     */
    private Mono<Void> handleAuthenticatedUser(ServerWebExchange exchange,
                                               GatewayFilterChain chain,
                                               AuthenticationResult authResult) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(HeaderConstant.USER_ID.getValue(), authResult.getUserId())
                .header(HeaderConstant.USER_TYPE.getValue(), BlogRole.READER.name())
                .build();
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * 处理游客请求
     */
    private Mono<Void> handleGuestUser(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String guestId = GuestUtil.getGuestIdFromCookie(request);
        if(guestId==null){
            guestId = GuestUtil.generateUniqueGuestId();
            GuestUtil.setGuestCookie(response,guestId);
        }
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(HeaderConstant.USER_ID.getValue(), guestId)
                .header(HeaderConstant.USER_TYPE.getValue(), BlogRole.GUEST.name())
                .build();
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
}