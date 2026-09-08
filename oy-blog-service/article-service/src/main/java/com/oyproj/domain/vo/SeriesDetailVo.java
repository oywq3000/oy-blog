package com.oyproj.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 专栏详情 VO（前台，成员文章按 sort_order 分页）
 */
@Data
public class SeriesDetailVo {
    private String id;
    private String name;
    private String description;
    private String coverUrl;

    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private Integer totalPages;

    /**
     * 本页成员文章（已发布，按 sort_order 升序）
     */
    private List<ArticleInfoVo> articles;
}
