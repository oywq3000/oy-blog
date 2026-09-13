package com.oyproj.config;

import org.apache.kafka.clients.producer.ProducerConfig;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用<b>真实的 application.yml</b> 验证文章索引生产者的属性接线（它建立的 {@code KafkaTemplate}
 * 同时服务索引链路与热榜链路）。
 *
 * <p>为什么要有这条：`spring.kafka.producer.properties` 下那几个带点号的键
 * （{@code enable.idempotence} / {@code max.block.ms}）**只要写成外层等价的短横线形式
 * （{@code spring.kafka.producer.enable-idempotence}）就会被静默忽略**——
 * Boot 3.4.11 的 {@code KafkaProperties.Producer} 根本没有这两者的绑定字段，
 * 绑定器默认忽略未知键：不报错、不告警，注释里承诺的保证在运行时并不存在
 * （提交 {@code 7989bc9} 修的就是这个）。全是字符串，写错一个 key 编译器一无所知。</p>
 *
 * <p>所以这里按 Boot 的方式把 yml 读进来绑成 {@link KafkaProperties}，再调
 * {@link KafkaProperties#buildProducerProperties()}——那是 {@code KafkaTemplate} 真正用来
 * 构造 {@code ProducerConfig} 的那份 Map。键在里面 = 契约真的生效。这是消费端
 * {@code BehaviorEventDeserializerWiringTest} 的对称用例（那边验 delegate，这边验 producer），
 * 也是"将来有人把它挪回顶层"的回归网。</p>
 */
@DisplayName("application.yml 的索引生产者属性接线")
class ArticleIndexProducerWiringTest {

    /** 按 Boot 的方式把真实 application.yml 绑成 KafkaProperties */
    private static Map<String, Object> producerPropertiesFromRealYaml() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        KafkaProperties properties = binder.bind("spring.kafka", Bindable.of(KafkaProperties.class)).get();
        return properties.buildProducerProperties();
    }

    /**
     * 取值用 {@code String.valueOf}：{@code KafkaProperties.Producer.properties} 是
     * {@code Map<String, String>}，yml 里的 {@code true}/{@code 100} 绑过来就已是字符串
     * （Kafka 的 {@code ConfigDef} 对 STRING 型原始值按对应类型解析，运行时行为不变）。
     * 断言的关键在<b>键在不在</b>——外层写法会让整个键消失。
     */
    private static void assertProperty(Map<String, Object> props, String key, String expected) {
        assertTrue(props.containsKey(key),
                key + " 必须出现在 buildProducerProperties() 里——写成外层短横线形式"
                        + "（spring.kafka.producer." + key.replace('.', '-') + "）会被 Boot 静默忽略，"
                        + "成为注释里承诺了、运行时并不存在的保证");
        assertEquals(expected, String.valueOf(props.get(key)));
    }

    @Test
    @DisplayName("enable.idempotence 真的进到了生产者属性（第 0 层：确认落盘 + 不重复）")
    void idempotence_reachesProducerProperties() throws Exception {
        assertProperty(producerPropertiesFromRealYaml(),
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    }

    @Test
    @DisplayName("max.block.ms 真的进到了生产者属性（send() 在用户请求线程上，异常路径必须快速失败）")
    void maxBlockMs_reachesProducerProperties() throws Exception {
        assertProperty(producerPropertiesFromRealYaml(),
                ProducerConfig.MAX_BLOCK_MS_CONFIG, "100");
    }

    @Test
    @DisplayName("acks=all 与外层键一起生效（第 0 层的另一半）")
    void acksAll_reachesProducerProperties() throws Exception {
        assertProperty(producerPropertiesFromRealYaml(), ProducerConfig.ACKS_CONFIG, "all");
    }
}
