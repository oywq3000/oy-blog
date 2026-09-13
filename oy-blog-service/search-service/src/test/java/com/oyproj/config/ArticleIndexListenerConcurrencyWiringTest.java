package com.oyproj.config;

import com.oyproj.common.mq.config.KafkaTopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

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

    /** 按 Boot 的方式把真实 application.yml 绑成 KafkaProperties */
    private static KafkaProperties propertiesFromRealYaml() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
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
