package com.oyproj.service;

/**
 * 文章审核消息生产者。
 * 发送失败只记日志不抛异常：文章已落库 ai_reviewing，ModerationStuckScanner 兜底扫描会接管。
 */
public interface ArticleModerationProducer {

    /** 发布/编辑提交后触发一次后台审核 */
    void sendModerationMessage(String articleId);
}
