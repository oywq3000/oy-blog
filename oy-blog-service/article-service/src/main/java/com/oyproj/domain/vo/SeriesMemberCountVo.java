package com.oyproj.domain.vo;

import lombok.Data;

/**
 * 专栏有效成员数统计VO
 */
@Data
public class SeriesMemberCountVo {
    private String seriesId;
    private Long articleCount;
}
