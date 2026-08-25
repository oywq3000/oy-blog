package com.oyproj.config;

import com.oyproj.common.base.Result;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.security.domain.SecurityUser;
import com.oyproj.common.security.filter.AuthFilter;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * admin-service 安全配置：/public/** 仅要求已认证（公告游客可读、通知仅读自己），其余一律要求 ADMIN。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final CommonCache commonCache;

    @Bean
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .anonymous(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/public/**").authenticated()
                        .anyRequest().access((authentication, context) ->
                                authentication != null
                                        && authentication.get() != null
                                        && authentication.get().getPrincipal() instanceof SecurityUser su
                                        && su.getUser() != null
                                        && su.getUser().getBlogRole() == BlogRole.ADMIN
                                        ? new AuthorizationDecision(true)
                                        : new AuthorizationDecision(false)))
                .exceptionHandling(exception -> {
                    exception.authenticationEntryPoint((request, response, authException) -> {
                        UnAuthorizedException e = new UnAuthorizedException();
                        response.setStatus(e.getErrCode());
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(JsonUtil.toJson(Result.error(e.getErrCode(), e.getMessage())));
                    }).accessDeniedHandler((request, response, accessDeniedException) -> {
                        ForbiddenException e = new ForbiddenException();
                        response.setStatus(e.getErrCode());
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(JsonUtil.toJson(Result.error(e.getErrCode(), e.getMessage())));
                    });
                });
        http.addFilterBefore(new AuthFilter(commonCache), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
