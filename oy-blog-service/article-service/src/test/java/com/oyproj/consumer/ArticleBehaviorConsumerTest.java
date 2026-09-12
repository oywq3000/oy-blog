package com.oyproj.consumer;

import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.common.mq.domain.ArticleBehaviorEvent;
import com.oyproj.service.HotRankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 消费端的「吞掉异常 + 照样 ack」契约。
 *
 * <p>这条契约是热榜设计的地基：热榜是装饰功能，一条毒消息不值得卡住整个分区
 * （Kafka 是 offset 提交制，不 ack 就是队头阻塞）。它与 ES 索引链那条
 * 「重试 + 死信」刻意相反，所以必须被测试钉住，否则日后有人为了「不丢事件」
 * 把 catch 去掉或把 ack 挪出 finally，分区阻塞会以线上事故的形式被发现。</p>
 */
@DisplayName("ArticleBehaviorConsumer 跳过与 ack 契约")
class ArticleBehaviorConsumerTest {

    private final HotRankService hotRankService = mock(HotRankService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final ArticleBehaviorConsumer consumer = new ArticleBehaviorConsumer(hotRankService);

    private static ArticleBehaviorEvent event() {
        return ArticleBehaviorEvent.builder()
                .eventId("e-1")
                .eventType(ArticleBehaviorType.VIEW)
                .articleId("A1")
                .userId("U1")
                .occurredAt("2026-09-12T20:15:30+08:00")
                .weight(1)
                .build();
    }

    @Test
    @DisplayName("处理成功：不改动 ack 以外的行为")
    void onEvent_success_acks() {
        consumer.onEvent(event(), ack);

        verify(hotRankService).recordEvent(any(ArticleBehaviorEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("处理抛异常：仍然 ack，且异常不外泄（不阻塞分区）")
    void onEvent_serviceThrows_stillAcksAndSwallows() {
        doThrow(new RuntimeException("redis down")).when(hotRankService).recordEvent(any());

        assertDoesNotThrow(() -> consumer.onEvent(event(), ack));

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("连 ack 之后的路径也不受影响：ack 失败不会被这里吞成静默")
    void onEvent_afterServiceFailure_ackIsStillTheLastThingThatHappens() {
        doThrow(new RuntimeException("redis down")).when(hotRankService).recordEvent(any());

        consumer.onEvent(event(), ack);

        // 顺序：先记事件、再 ack。若有人在 ack 之前 return/throw，位点就永远不会前进
        var inOrder = inOrder(hotRankService, ack);
        inOrder.verify(hotRankService).recordEvent(any(ArticleBehaviorEvent.class));
        inOrder.verify(ack).acknowledge();
    }
}
