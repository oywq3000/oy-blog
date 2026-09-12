package com.oyproj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 审核链路的编程式事务边界。
 * 为什么不用 @Transactional 注解：一方面消费端链路里存在同类自调用（AOP 代理不拦截、注解形同虚设，
 * 本项目已踩过这个坑），另一方面两段事务的边界必须肉眼可见——AI 调用要严格落在两次事务之间。
 */
@Configuration
public class ModerationTxConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
