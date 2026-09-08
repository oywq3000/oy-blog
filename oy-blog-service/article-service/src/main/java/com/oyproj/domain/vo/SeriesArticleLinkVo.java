package com.oyproj.domain.vo;

import lombok.Data;

/**
 * 文章详情中的专栏链接 VO（无专栏时 seriesList 为 null，前端判空隐藏）
 */
@Data
public class SeriesArticleLinkVo {
    private String seriesId;
    private String name;
    private String coverUrl;

    /**
     * 该文章在专栏内的排序
     */
    private Integer sortOrder;

    /**
     * 该专栏已发布文章总数
     */
    private Long totalCount;
}
