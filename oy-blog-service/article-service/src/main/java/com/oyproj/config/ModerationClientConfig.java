package com.oyproj.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 审核 HTTP 客户端配置：article-service 首次出站 HTTP（同步 JSON 调 BlogAgent）。
 * 用 Spring 6.1 自带的 RestClient（spring-boot-starter-web 已含），无需新增依赖。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationClientConfig {

    private final ModerationProperties moderationProperties;

    @Bean
    public RestClient moderationRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(moderationProperties.getTimeoutMs());
        factory.setReadTimeout(moderationProperties.getTimeoutMs());
        return RestClient.builder()
                .baseUrl(moderationProperties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
