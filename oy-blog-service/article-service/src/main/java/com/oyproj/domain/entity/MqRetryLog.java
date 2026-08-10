package com.oyproj.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MQ 消息重试日志
 * 当 RabbitMQ 发送失败时，消息持久化到此表，由定时任务重试发送。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mq_retry_log")
public class MqRetryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息类型：ARTICLE_INDEX / ARTICLE_DELETE */
    private String messageType;

    /** 消息体 JSON */
    private String messageBody;

    /** 已重试次数 */
    private Integer retryCount;

    /** 状态：PENDING / SUCCESS / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
