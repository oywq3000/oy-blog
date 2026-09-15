package com.oyproj.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "oy-blog.article.recommend")
public class RecommendProperties {
    /** 画像取 top N 标签参与打分 */
    private int profileTopTags = 30;
    /** 缓存成品列表长度（打满即截断） */
    private int resultCacheSize = 100;
    /** 读端结果缓存秒数 */
    private long resultCacheTtlSeconds = 600L;
    /** 游客画像/已读 key TTL（7 天） */
    private long guestTtlSeconds = 604800L;
}