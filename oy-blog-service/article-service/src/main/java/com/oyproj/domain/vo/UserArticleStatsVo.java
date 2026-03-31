package com.oyproj.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 *  用户文章统计VO
 */
@Data
@Builder
public class UserArticleStatsVo {
    private Long articleCount;
    private Long viewCount;
    private Long likeCount;
}
