package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签统计VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagStatVo {
    private String id;
    private String name;
    private Long articleCount;
    /** 1=官方预置标签 0=用户自创标签 */
    private Integer isCommon;
}
