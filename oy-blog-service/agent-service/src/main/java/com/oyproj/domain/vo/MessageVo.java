package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息视图对象（对齐前端 types/agent.ts 的 Message）
 * streaming/error/errorMessage 是前端本地状态，后端不返回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVo {

    private String id;

    /**
     * user / assistant
     */
    private String role;

    private String content;

    /**
     * 思考过程
     */
    private String thinking;

    /**
     * 思考耗时（秒）
     */
    private Integer thinkingTime;

    private LocalDateTime createdAt;
}
