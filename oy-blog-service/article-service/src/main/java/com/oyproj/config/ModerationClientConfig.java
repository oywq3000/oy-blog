package com.oyproj.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 审核 HTTP 客户端配置：article-service 首次出站 HTTP（同步 JSON 调 BlogAgent）。
 * 用 Spring 6.1 自带的 RestClient（spring-boot-starter-web 已含），无需新增依赖。
 *
 * 注意：不要再加 @EnableConfigurationProperties(ModerationProperties.class)——ModerationProperties
 * 类上已有 @Component（与 HotWeightProperties 同模式），再加 enable 会双注册同名类型 bean，
 * 构造注入按类型匹配时抛"required a single bean, but 2 were found"。
 */
@Configuration
@RequiredArgsConstructor
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
