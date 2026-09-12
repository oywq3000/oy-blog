package com.oyproj.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 热榜配置。
 */
@Component
@Data
@ConfigurationProperties(prefix = "oy-blog.article.hot-rank")
public class HotRankProperties {

    /** Redis 榜保留的最大条数；请求页超出此窗口时回退 MySQL */
    private int windowSize = 200;

    /** 日桶保留天数（比窗口多 1 天，保证合并时 7 天数据都在） */
    private int retentionDays = 8;
}
