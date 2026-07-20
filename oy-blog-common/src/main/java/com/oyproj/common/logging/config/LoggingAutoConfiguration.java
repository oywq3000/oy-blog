package com.oyproj.common.logging.config;

import com.oyproj.common.logging.LoggingFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 日志过滤器自动配置 — 仅在 Servlet Web 环境下生效
 *
 * <p>Gateway（WebFlux）不会加载此配置，避免类型冲突。</p>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, org.springframework.web.util.ContentCachingRequestWrapper.class})
public class LoggingAutoConfiguration {

    @Bean
    public FilterRegistrationBean<LoggingFilter> loggingFilterRegistration() {
        FilterRegistrationBean<LoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LoggingFilter());
        registration.addUrlPatterns("/*");
        // 优先级低于 Spring Security / AuthFilter，但高于业务 Filter
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.setName("loggingFilter");
        return registration;
    }
}
