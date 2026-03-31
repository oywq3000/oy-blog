package com.oyproj.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 标签统计VO
 */
@Data
@Builder
public class TagStatVo {
    private String id;
    private String name;
    private String code;
    private Long articleCount;
}
