package com.oyproj.common.mq.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章 AI 审核消息（发布/编辑提交后驱动后台审核）
 * 重试计数不放消息体，放消息头 x-attempt（见 ModerationRetrySender）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleModerationMessage {
    /** 文章ID（消费端从 DB 读最新状态，消息体只做触发） */
    private String articleId;
}
