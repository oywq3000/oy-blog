package com.oyproj.config;

import com.oyproj.common.mq.config.KafkaTopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用<b>真实的 application.yml</b> 验证监听容器的并发度接线。
 *
 * <p>为什么要有这条：spec §2.3 把"<b>同篇保序 + 跨篇并行</b>"写成迁移的真实升级，
 * 但这件事<b>全靠一个 yml 键</b>（{@code spring.kafka.listener.concurrency}）——
 * 不配它，单个容器就是一个线程跑全部 3 个分区，与 RabbitMQ 时代的全局串行完全等价
 * （升级点落空，且不报错、不告警）。本仓库已经吃过一次"配置键写错/放错位置被静默忽略"
 * （{@code enable-idempotence} 顶层键，提交 {@code 7989bc9}），所以承诺要用测试钉住。</p>
 *
 * <p>安全性论证就一句：同一条 key（= articleId）恒定落进同一个分区，而一个分区在同一时刻
 * 只被一个线程消费 —— 并发只发生在"不同分区"之间，也就是"不同文章"之间。</p>
 */
@DisplayName("application.yml 的索引监听并发度接线")
class ArticleIndexListenerConcurrencyWiringTest {

    /** 按 Boot 的方式把真实 application.yml 绑成 KafkaProperties（不设任何环境覆盖） */
    private static KafkaProperties propertiesFromRealYaml() throws Exception {
        return propertiesFromRealYaml(new Properties());
    }

    /**
     * 按 Boot 的方式把真实 application.yml 绑成 KafkaProperties。
     *
     * <p><b>为什么必须显式传占位符解析器</b>：裸 {@code Binder} 的默认是
     * {@code PlaceholdersResolver.NONE}——<b>不解析占位符</b>（Boot 源码 {@code Binder:192}）。
     * yml 里的 concurrency 现在是 {@code ${KAFKA_LISTENER_CONCURRENCY:3}}，
     * 不装解析器时绑到的就是字符串字面量 {@code "${KAFKA_LISTENER_CONCURRENCY:3}"}，
     * 转 Integer 直接 {@code BindException}（本改动实测踩到，2026-09-13）。
     * 生产里 Boot 是拿 {@code PropertySourcesPlaceholdersResolver(environment)} 绑的，
     * 这里补上同一层，测试才和生产走同一条路。</p>
     *
     * @param testEnv 模拟环境变量（如 {@code KAFKA_LISTENER_CONCURRENCY}）。
     *                用受控的 {@link Properties} 而不是 {@code System.getenv()}：测试是封闭的，
     *                本机恰好导出了同名变量也不会改变断言。
     */
    private static KafkaProperties propertiesFromRealYaml(Properties testEnv) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources),
                new PropertySourcesPlaceholdersResolver(
                        Collections.singletonList(new PropertiesPropertySource("test-env", testEnv))));
        return binder.bind("spring.kafka", Bindable.of(KafkaProperties.class)).get();
    }

    @Test
    @DisplayName("spring.kafka.listener.concurrency = 3（跨篇并行真的接线了，不只是文档）")
    void concurrency_reachesListenerProperties() throws Exception {
        Integer concurrency = propertiesFromRealYaml().getListener().getConcurrency();

        assertNotNull(concurrency,
                "必须显式配 spring.kafka.listener.concurrency —— 默认值 1 等于把 RabbitMQ 时代的全局串行原样搬过来");
        assertEquals(3, concurrency,
                "并发度应为 3（= article.index 的分区数），否则跨篇并行这个升级点就落空了");
    }

    @Test
    @DisplayName("环境变量 KAFKA_LISTENER_CONCURRENCY 能覆盖默认值（部署期调低不必重新打包）")
    void concurrency_envOverride_takesEffect() throws Exception {
        Properties testEnv = new Properties();
        testEnv.setProperty("KAFKA_LISTENER_CONCURRENCY", "1");

        Integer concurrency = propertiesFromRealYaml(testEnv).getListener().getConcurrency();

        assertEquals(1, concurrency,
                "KAFKA_LISTENER_CONCURRENCY 没接上：部署期发现内存吃紧时，调低并发就成了重新打包 + 重新部署");
    }

    @Test
    @DisplayName("并发度与分区数的关系：并发上限就是分区数（同篇仍保序）")
    void concurrency_doesNotExceedTopicPartitions() {
        int partitions = new KafkaTopicConfig().articleIndexTopic().numPartitions();
        Integer concurrency = assertDoesNotThrow(
                () -> propertiesFromRealYaml().getListener().getConcurrency());

        assertEquals(3, partitions,
                "article.index 的分区数变了？并发度与该值绑定（并发上限 = 分区数），请一并复核");
        assertNotNull(concurrency, "concurrency 未配置（见上一条用例）");
        assertTrue(concurrency <= partitions,
                "并发线程数不应超过分区数：多出来的线程只会空闲，且会让人误以为还能更并行");
    }
}
