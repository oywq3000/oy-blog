package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话视图对象（对齐前端 types/agent.ts 的 Conversation）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVo {

    private String id;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 消息数（批量统计）
     */
    private Long messageCount;
}
