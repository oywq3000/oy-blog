package com.oyproj.service;

/**
 * 审核延迟重试发送器。
 * 消息发往独立的 retry exchange → retry 队列（无消费者）→ 逐条 TTL 到期死信回主 exchange
 * 重新投递主队列（发往主 exchange 会让主队列立即消费，延迟失效——必须走 retry exchange）。
 * 重试语义：nextAttempt = 下一次消费的计数值（∈{1,2,3}）；TTL = retryTtlMs[nextAttempt-1]。
 */
public interface ModerationRetrySender {

    /** 发延迟重试：nextAttempt 为下一次消费时的计数值（消费端 attempt+1 算出） */
    void sendRetry(String articleId, int nextAttempt);
}
