package com.oyproj.common.mq.config;

import com.oyproj.common.mq.domain.ArticleModerationMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：{@code RabbitMQConfig#rabbitTemplate} 必须显式 {@code setMandatory(true)}，
 * 否则 returnsCallback 永不触发——不可路由的审核消息被 broker 静默丢弃。
 *
 * <p><b>为什么要显式设</b>（已逐条核对 bytecode，非推测）：</p>
 * <ol>
 *   <li>Spring AMQP 3.2.8 的 {@code RabbitTemplate.<init>} 把 {@code mandatoryExpression}
 *       初始化为 {@code Boolean.FALSE}（{@code iconst_0} → {@code ValueExpression}），
 *       只有 {@code setMandatory/setMandatoryExpression*} 能改。</li>
 *   <li>发送路径（{@code lambda$send$3}）实际算的是
 *       {@code mandatory = (returnsCallback != null || (correlationData != null && hasText(id)))
 *       && isMandatoryFor(message)}——第一个或项在审核发送（不传 correlationData）时为 false，
 *       若 {@code isMandatoryFor} 也 false，则 {@code channel.basicPublish} 收到 {@code mandatory=false}，
 *       broker 不回 {@code basic.return}，returns 回调形同虚设。</li>
 *   <li>application.yml 的 {@code spring.rabbitmq.template.mandatory: true} <b>到不了本 bean</b>：
 *       它由 {@code RabbitTemplateConfigurer} 施加，只作用于 Boot 的
 *       {@code RabbitAutoConfiguration#rabbitTemplate}，而该 bean 带
 *       {@code @ConditionalOnMissingBean(RabbitOperations.class)}，被本项目自定义的
 *       RabbitTemplate bean 顶掉（{@code RabbitTemplate implements RabbitOperations}）。</li>
 * </ol>
 *
 * <p>本测试在<b>两层</b>钉住：断言层（{@code isMandatoryFor}）与线上真实路径
 * （捕获 {@code channel.basicPublish} 的 mandatory 实参）。对照组用裸 {@code new RabbitTemplate}
 * 复现「不设 mandatory」的后果，确保断言确实有区分能力（不会因两边都为 true 而假绿）。</p>
 */
@DisplayName("RabbitMQConfig 产出的模板必须 mandatory=true（returns 回调才可达）")
class RabbitMQConfigMandatoryTest {

    private static final String EXCHANGE = "article.moderation.exchange";
    private static final String ROUTING_KEY = "article.moderation";

    private ConnectionFactory connectionFactory;
    private Channel channel;

    @BeforeEach
    void setUp() {
        Connection connection = mock(Connection.class);
        channel = mock(Channel.class);
        connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createChannel(anyBoolean())).thenReturn(channel);
        when(connection.isOpen()).thenReturn(true);
        when(channel.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("RabbitMQConfig 的模板：isMandatoryFor 与 basicPublish 实参均为 true")
    void configBeanIsMandatory() throws Exception {
        RabbitTemplate template = new RabbitMQConfig().rabbitTemplate(connectionFactory);

        assertThat(template.isMandatoryFor(emptyMessage()))
                .as("RabbitMQConfig 产出的模板必须能对消息判定 mandatory=true")
                .isTrue();

        assertThat(publishAndCaptureMandatory(template))
                .as("真实发送路径上 channel.basicPublish 的 mandatory 实参必须为 true，"
                        + "否则不可路由的消息不会触发 returnsCallback")
                .isTrue();
    }

    @Test
    @DisplayName("对照组：除 setMandatory 外配置相同的模板，mandatory 为 false（= 不显式设置时的默认后果）")
    void bareTemplateIsNotMandatory() throws Exception {
        // 与 RabbitMQConfig 产出的模板**只差一行 setMandatory(true)**：
        // 转换器照设，用来隔离变量——证明「转换器/其它配置都对」也换不来 mandatory，
        // 也证明本测试的断言确实有区分能力（否则可能两边都为 true 而假绿）。
        RabbitTemplate bare = new RabbitTemplate(connectionFactory);
        bare.setMessageConverter(new RabbitMQConfig().jsonMessageConverter());

        assertThat(bare.isMandatoryFor(emptyMessage()))
                .as("RabbitTemplate 的 mandatoryExpression 默认应为 FALSE——这正是必须显式 setMandatory(true) 的原因")
                .isFalse();

        assertThat(publishAndCaptureMandatory(bare))
                .as("对照组必须发布成 mandatory=false，证明本测试的断言有区分能力")
                .isFalse();
    }

    /** 走真实 convertAndSend → doSend → basicPublish，捕获 mandatory 实参 */
    private boolean publishAndCaptureMandatory(RabbitTemplate template) throws Exception {
        template.convertAndSend(EXCHANGE, ROUTING_KEY,
                ArticleModerationMessage.builder().articleId("art-1").build());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Boolean> mandatory =
                org.mockito.ArgumentCaptor.forClass(Boolean.class);
        verify(channel).basicPublish(eq(EXCHANGE), eq(ROUTING_KEY), mandatory.capture(), any(), any());
        return Boolean.TRUE.equals(mandatory.getValue());
    }

    private Message emptyMessage() {
        return new Message(new byte[0], new MessageProperties());
    }
}
