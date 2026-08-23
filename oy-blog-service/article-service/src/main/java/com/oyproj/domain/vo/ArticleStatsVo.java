package com.oyproj.domain.vo;

import lombok.Data;

/**
 * 全局文章数据统计
 */
@Data
public class ArticleStatsVo {
    private Long articleCount;
    private Long viewCount;
    private Long likeCount;
    private Long tagCount;
}
