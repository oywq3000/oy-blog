package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.controller.ArticleIndexController;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.mapper.ArticleTagMapper;
import com.oyproj.mapper.TagMapper;
import com.oyproj.service.ArticleIndexControllerProvider;
import com.oyproj.service.ArticleIndexSeedService;
import com.oyproj.service.ArticleIndexSeedService.SeedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * 装配测试：证明 <b>Spring 容器</b>（而不是 Mockito 手工 new）能把
 * {@link ArticleIndexController} 与 {@link ArticleIndexSeedServiceImpl} 一起建起来。
 *
 * <p>为什么必须有这个测试：播种服务依赖的 {@code ArticleIndexControllerProvider} 唯一的实现者
 * 就是 {@code ArticleIndexController}，而 controller 又依赖播种服务 —— 这是<b>构造器注入的环</b>。
 * Boot 3 默认 {@code spring.main.allow-circular-references=false}，环一旦存在
 * <b>article-service 直接起不来</b>（{@code BeanCurrentlyInCreationException}），
 * 而纯 Mockito 单测（{@code @InjectMocks} / 手工 new）在结构上永远看不到这类问题
 * ——它们不建 Spring 上下文。本测试就是那个监视图。</p>
 *
 * <p>只装配相关 bean：依赖项（DAO / UserClient / KafkaTemplate）给 mock，
 * 不引入自动配置 → 不碰 Nacos / MySQL / Redis / broker。</p>
 */
@DisplayName("ArticleIndexSeedService 装配（Spring 容器真实建 bean）")
class ArticleIndexSeedContextWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ArticleIndexController.class,
                    ArticleIndexSeedServiceImpl.class,
                    I18nUtils.class,
                    MockBeans.class);

    @Test
    @DisplayName("容器能同时建起 controller 与播种服务（无构造器成环），且播种能经容器调到 controller")
    void contextStarts_andSeedServiceReachesControllerThroughContainer() {
        runner.run(context -> {
            Throwable failure = context.getStartupFailure();
            if (failure != null) {
                fail("Spring 上下文装配失败（构造器注入成环时会正是这个样子）: " + failure);
            }

            // provider 必须由容器解析出 controller 本身（ObjectProvider 懒解析的结果）
            ArticleIndexControllerProvider provider =
                    context.getBean(ArticleIndexControllerProvider.class);
            assertInstanceOf(ArticleIndexController.class, provider);

            // 不只是"建起来了"：真的透过容器跑一次播种。
            // 空库（DAO mock 默认返回空集合/0）→ 0 条，不会碰 KafkaTemplate。
            ArticleIndexSeedService seedService = context.getBean(ArticleIndexSeedService.class);
            SeedResult result = seedService.seedIndexTopic();
            assertEquals(0, result.total());
            assertEquals(0, result.succeeded());
            assertTrue(result.failedArticleIds().isEmpty());
        });
    }

    /** 只为装配服务的依赖替身；不需要任何真实基础设施。 */
    @TestConfiguration
    static class MockBeans {

        @Bean
        ArticleDao articleDao() {
            return mock(ArticleDao.class);
        }

        @Bean
        ArticleContentDao articleContentDao() {
            return mock(ArticleContentDao.class);
        }

        @Bean
        ArticleStatsDao articleStatsDao() {
            return mock(ArticleStatsDao.class);
        }

        @Bean
        UserClient userClient() {
            return mock(UserClient.class);
        }

        @Bean
        ArticleTagMapper articleTagMapper() {
            return mock(ArticleTagMapper.class);
        }

        @Bean
        TagMapper tagMapper() {
            return mock(TagMapper.class);
        }

        @SuppressWarnings("unchecked")
        @Bean
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        /** Result.ok(...) 需要 messageSource；空表 → NoSuchMessageException → 回落到 ResultCode 默认文案。 */
        @Bean
        MessageSource messageSource() {
            return new StaticMessageSource();
        }
    }
}
