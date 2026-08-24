package com.oyproj.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

/**
 * 国际化配置类
 * 注意：条件为 ANY（SERVLET + REACTIVE 都生效），网关（WebFlux）也需要加载 MessageSource
 * 但 LocaleResolver（spring-webmvc 的类）不能放这里 —— 网关 classpath 没有 spring-webmvc，
 * 放这里会导致网关启动 NoClassDefFoundError，见 MvcLocaleConfig
 */
@Configuration
@ConditionalOnWebApplication
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();

        // 设置消息文件的基础名（不包含扩展名）
        messageSource.setBasenames(
            "classpath:i18n/messages",
            "classpath:i18n/ValidationMessages"
        );

        // 设置默认编码
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

        // 设置缓存时间（秒）
        messageSource.setCacheSeconds(3600);

        // 如果找不到消息，是否使用消息代码作为默认消息
        messageSource.setUseCodeAsDefaultMessage(false);

        // 不回退系统区域设置：语言不匹配（如 fr）时确定回退到默认 bundle（中文），
        // 而不是依赖 JVM 所在服务器的语言环境
        messageSource.setFallbackToSystemLocale(false);

        return messageSource;
    }
}
