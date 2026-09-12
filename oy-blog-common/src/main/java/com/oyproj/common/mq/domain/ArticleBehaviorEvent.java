package com.oyproj.common.mq.domain;

import com.oyproj.common.mq.constants.ArticleBehaviorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户行为事件（热榜数据源）。
 *
 * <p>{@code occurredAt} 是<b>事件发生时间</b>而非发送时间：消息可能因重试/重启而延迟到达，
 * 按事件自带时间戳归窗口，结果才稳定。</p>
 *
 * <p>{@code weight} 由生产端按权重表赋值（取消操作为负数），随事件一起持久化，
 * 因此日后调整权重配置不会改变已写出的历史事件语义。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleBehaviorEvent {

    /** 事件唯一 id（幂等键，当前不做去重，保留供未来使用） */
    private String eventId;

    /** 行为类型 */
    private ArticleBehaviorType eventType;

    /** 文章 id */
    private String articleId;

    /** 用户 id；游客浏览为 null */
    private String userId;

    /** 事件发生时间，ISO-8601 带时区偏移，如 2026-09-12T20:15:30.123+08:00 */
    private String occurredAt;

    /** 热度权重（可为负） */
    private int weight;
}
