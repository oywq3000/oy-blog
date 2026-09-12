package com.oyproj.service;

/**
 * 审核消息处理流程：两段短事务包住状态机，AI 审核调用在事务外。
 * 入口是 ArticleModerationConsumer（MQ 消息驱动）；测试可直接调用本接口。
 */
public interface ArticleModerationWorkflow {

    /**
     * 处理一条审核消息。
     *
     * @param articleId 文章 ID（消息体唯一字段）
     * @param attempt   重试计数，来自消息头 x-attempt（首投 0）
     */
    void process(String articleId, int attempt);
}
