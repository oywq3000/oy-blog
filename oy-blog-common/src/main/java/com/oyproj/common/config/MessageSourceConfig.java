package com.oyproj.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;

/**
 * 国际化配置类
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MessageSourceConfig implements WebMvcConfigurer {
    
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
        
        // 是否回退到系统区域设置
        messageSource.setFallbackToSystemLocale(true);
        
        return messageSource;
    }
}