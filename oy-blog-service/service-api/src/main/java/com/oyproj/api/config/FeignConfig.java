package com.oyproj.api.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {
    
    @Bean
    public RequestInterceptor serviceCallInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 为所有 Feign 调用添加服务间调用标识
                template.header("X-Service-Call", "true");
                // 传播 traceId 实现全链路追踪
                String traceId = org.slf4j.MDC.get("traceId");
                if (traceId != null && !traceId.isEmpty()) {
                    template.header("X-Trace-Id", traceId);
                }
            }
        };
    }
}