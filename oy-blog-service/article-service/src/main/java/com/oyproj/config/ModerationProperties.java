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
}
