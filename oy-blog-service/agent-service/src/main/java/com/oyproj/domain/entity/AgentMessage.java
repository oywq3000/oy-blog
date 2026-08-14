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
 * AI 对话消息
 *
 * id 由服务端生成（ASSIGN_UUID），done 事件回传前端。
 * user_id 冗余存储，便于 feedback 接口做消息级 owner 校验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_message")
public class AgentMessage {

    /**
     * 消息ID（服务端生成）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 所属会话ID
     */
    private String conversationId;

    /**
     * 归属用户ID（冗余）
     */
    private String userId;

    /**
     * 角色：user / assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 思考过程（深度思考时）
     */
    private String thinking;

    /**
     * 思考耗时（秒）
     */
    private Integer thinkingTime;

    /**
     * 用户反馈：like / dislike
     */
    private String feedback;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
