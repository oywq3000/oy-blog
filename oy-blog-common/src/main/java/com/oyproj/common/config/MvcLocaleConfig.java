package com.oyproj.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * MVC 服务的语言（Locale）解析配置
 *
 * 按请求头 Accept-Language 决定报错文案语言：
 * - 无 Accept-Language 头 → 回退中文（zh_CN）
 * - 头为 en / en-US → 英文（en_US）
 * - 其他语言（如 fr）→ 中文
 *
 * 注意：
 * 1. 必须限定 SERVLET —— 网关（WebFlux）classpath 没有 spring-webmvc，
 *    加载本类会 NoClassDefFoundError
 * 2. bean 方法名必须叫 localeResolver —— Spring Boot 按 bean 名退让（@ConditionalOnMissingBean(name="localeResolver")）
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MvcLocaleConfig {

    @Bean
    public org.springframework.web.servlet.LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.US, Locale.SIMPLIFIED_CHINESE));
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }
}
