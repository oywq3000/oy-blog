package com.oyproj.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户活跃度热力图单日数据VO
 */
@Data
@Builder
public class HeatmapDayVo {
    /**
     * 日期（YYYY-MM-DD）
     */
    private LocalDate date;

    /**
     * 当天活跃事件总数（发文章/评论/回复/点赞/收藏）
     */
    private Long count;
}
