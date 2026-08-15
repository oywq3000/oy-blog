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
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;

import org.springframework.http.HttpHeaders;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {
    private final AuthProperties authProperties;
    private final CommonCache commonCache;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        log.info("转发:{}",path);
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        //白名单路径：可选认证（类似 permitAll）—— 无论 token 是否有效都可访问
        if(isWhitelisted(path)){
            AuthenticationResult authResult = authenticateUser(token);
            if(authResult.isAuthenticated()){
                //token 有效 → 仍按认证用户处理，注入真实用户ID
                log.debug("认证用户访问白名单路径: {}, 用户ID: {}", path, authResult.getUserId());
                return handleAuthenticatedUser(exchange, chain, authResult);
            }
            //无 token 或 token 无效/过期 → 按游客放行，绝不拒绝
            log.debug("游客访问白名单路径: {}", path);
            return handleGuestUser(exchange, chain);
        }
        //非白名单路径：严格认证，行为保持不变
        AuthenticationResult authResult = authenticateUser(token);
        if(authResult.isAuthenticated()){
            //用户认证成功
            log.debug("认证用户访问: {}, 用户ID: {}", path, authResult.getUserId());
            return handleAuthenticatedUser(exchange, chain, authResult);
        }else if(!StringUtil.isNullOrEmpty(token)){
            //存在token，且认证失败 → 直接拒绝
            log.warn("Token认证失败，拒绝访问: {}", path);
            return Mono.error(new UnAuthorizedException("Token无效或已过期，请重新登录"));
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
        return authProperties.getWhitelist().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private AuthenticationResult authenticateUser(String token) {

        if (token == null) {
            return AuthenticationResult.unauthenticated();
        }
        try {
            token = token.substring(7);
            Claims claims = JwtUtil.parseToken(token);
            // 拒绝 refresh token 作为 access token 使用
            String tokenType = JwtUtil.getTokenType(claims);
            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                log.warn("非法使用 refresh token 作为访问令牌");
                return AuthenticationResult.unauthenticated();
            }
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