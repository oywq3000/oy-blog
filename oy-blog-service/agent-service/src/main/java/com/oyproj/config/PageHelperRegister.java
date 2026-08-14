package com.oyproj.config;

import com.github.pagehelper.PageInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/**
 * 在 ApplicationReadyEvent 时注册 PageInterceptor 到所有 SqlSessionFactory。
 *
 * 使用事件监听而非 @Autowired/@Bean 避免与 MybatisPlusAutoConfiguration 形成循环依赖。
 * 此时容器已完全就绪，所有 SqlSessionFactory 均已创建。
 * （与 article-service 保持一致）
 */
@Component
public class PageHelperRegister {

    private final ApplicationContext ctx;

    public PageHelperRegister(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        PageInterceptor interceptor = new PageInterceptor();
        Properties props = new Properties();
        props.setProperty("helperDialect", "mysql");
        props.setProperty("reasonable", "true");
        interceptor.setProperties(props);

        Map<String, SqlSessionFactory> factories = ctx.getBeansOfType(SqlSessionFactory.class);
        for (SqlSessionFactory factory : factories.values()) {
            try {
                if (!factory.getConfiguration().getInterceptors().contains(interceptor)) {
                    factory.getConfiguration().addInterceptor(interceptor);
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
