package com.oyproj.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文章热度加权公式配置
 *
 * <p>热度分 = views * wViews + likes * wLikes + comments * wComments + favorites * wFavorites，
 * 权重可在 application.yml 的 oy-blog.article.hot-weight 下调整，未配置时使用字段默认值。</p>
 */
@Component
@Data
@ConfigurationProperties(prefix = "oy-blog.article.hot-weight")
public class HotWeightProperties {
    /** 浏览量权重 */
    private long views = 1L;
    /** 点赞数权重 */
    private long likes = 2L;
    /** 评论数权重 */
    private long comments = 5L;
    /** 收藏数权重 */
    private long favorites = 3L;
}
