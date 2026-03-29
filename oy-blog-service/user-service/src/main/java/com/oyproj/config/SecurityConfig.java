package com.oyproj.config;

import com.oyproj.filter.AuthFilter;
import jakarta.servlet.Filter;
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
                .anonymous(AbstractHttpConfigurer::disable).build();
         http.authorizeHttpRequests(authorize-> authorize.anyRequest().authenticated());
         http.addFilterBefore(new AuthFilter(), UsernamePasswordAuthenticationFilter.class);
         return http.build();
    }



    //encoder 工具
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}