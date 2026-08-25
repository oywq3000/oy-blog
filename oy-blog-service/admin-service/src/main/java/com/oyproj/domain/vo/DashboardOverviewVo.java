package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVo {
    private Long articleCount;
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long userCount;
}
