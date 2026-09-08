package com.oyproj.domain.vo;

import lombok.Data;

/**
 * 专栏前台列表 VO（含已发布成员数角标）
 */
@Data
public class SeriesReadVo {
    private String id;
    private String name;
    private String description;
    private String coverUrl;

    /**
     * 已发布成员数（无有效成员为 0）
     */
    private Long articleCount;
}
