-- MQ 消息重试日志表
-- 当 RabbitMQ 发送失败时，消息持久化到此表，由 RetryMqScheduler 定时重试
CREATE TABLE IF NOT EXISTS mq_retry_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_type VARCHAR(50) NOT NULL COMMENT '消息类型: ARTICLE_INDEX / ARTICLE_DELETE',
    message_body TEXT NOT NULL COMMENT '消息体 JSON',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING / SUCCESS / FAILED',
    error_msg VARCHAR(500) DEFAULT NULL COMMENT '最后一次错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 消息重试日志';
