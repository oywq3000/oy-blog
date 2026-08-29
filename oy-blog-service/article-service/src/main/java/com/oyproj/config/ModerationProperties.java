package com.oyproj.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章 AI 审核配置（application.yml 的 oy-blog.article.moderation 节点）
 */
@Component
@Data
@ConfigurationProperties(prefix = "oy-blog.article.moderation")
public class ModerationProperties {
    /** 审核总开关，false = 全放行（等于关闭审核门） */
    private boolean enabled = true;
    /** 豁免角色列表（BlogRole.name()，如 ADMIN），命中则跳过 AI 审核 */
    private List<String> exemptRoles = new ArrayList<>(List.of("ADMIN"));
    /** BlogAgent 审核端点地址 */
    private String baseUrl = "http://localhost:8001";
    /** 审核调用超时（毫秒），连接和读取共用 */
    private int timeoutMs = 30000;
    /** 审核重试延迟序列（毫秒）：第 1/2/3 次重试的延迟，取 retryTtlMs[attempt] */
    private List<Long> retryTtlMs = new ArrayList<>(List.of(10000L, 30000L, 90000L));
    /** 最大重试次数（失败达到该次数后转人工），attempt 从 0 起 */
    private int maxAttempt = 3;
    /** 兜底扫描：审核中超过该分钟数无结果 → 转人工 */
    private int stuckTimeoutMinutes = 15;
    /** 兜底扫描间隔（毫秒） */
    private long scanIntervalMs = 300000L;
}
