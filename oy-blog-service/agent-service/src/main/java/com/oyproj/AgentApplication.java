package com.oyproj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * AI 助手服务（BFF）
 *
 * 架构定位：前端 -> 网关(/agent/**) -> agent-service -> Python Agent（内网直连）
 * 职责：会话/消息落库、统一 Result 信封、鉴权对接（读取网关注入的 x-user-id）、SSE 流式转发
 *
 */
@SpringBootApplication
@EnableTransactionManagement
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
