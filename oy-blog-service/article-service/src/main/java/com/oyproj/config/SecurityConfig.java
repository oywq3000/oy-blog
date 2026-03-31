package com.oyproj.config;

import com.oyproj.common.base.Result;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.utils.JsonUtil;
import com.oyproj.filter.AuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        //取消掉一下过时的Filter
         http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                //requestCache用于重定向，前后端分离后无需重定向，requestCache也用不上
                .requestCache(cache->{
                    cache.requestCache(new NullRequestCache());
                })
                //无需给用户一个匿名身份
                .anonymous(AbstractHttpConfigurer::disable);

        /**
         * http.authorizeHttpRequests()-开启HTTP请求授权配置，
         * authorize-> authorize.anyRequest() - 选择所有 HTTP 请求
         * .authenticated() - 要求所有请求都必须经过认证
         */

         http.authorizeHttpRequests(authorize-> authorize
                 .requestMatchers("/auth/**").permitAll()
                 .requestMatchers("/public/**").permitAll()
                 .anyRequest().authenticated())
                 .exceptionHandling(exception->{
                        exception.authenticationEntryPoint((request, response, authException) ->{
                            UnAuthorizedException unAuthorizedException = new UnAuthorizedException();
                            response.setStatus(unAuthorizedException.getErrCode());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(JsonUtil.toJson(Result.error(unAuthorizedException.getErrCode(),unAuthorizedException.getMessage())));
                        }).accessDeniedHandler(((request, response, accessDeniedException) -> {
                            ForbiddenException forbiddenException = new ForbiddenException();
                            response.setStatus(forbiddenException.getErrCode());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(JsonUtil.toJson(Result.error(forbiddenException.getErrCode(),forbiddenException.getMessage())));
                        }));
                 });
         http.addFilterBefore(new AuthFilter(), UsernamePasswordAuthenticationFilter.class);
         return http.build();
    }



    //encoder 工具
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}