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
 * AI 对话会话
 *
 * id 由前端客户端生成（conv_ 前缀），后端首条消息时 upsert。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_conversation")
public class AgentConversation {

    /**
     * 会话ID（前端生成 conv_*）
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 归属用户ID或游客ID（来自网关 x-user-id）
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
