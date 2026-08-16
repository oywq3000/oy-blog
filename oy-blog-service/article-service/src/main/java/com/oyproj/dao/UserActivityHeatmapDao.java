package com.oyproj.dao;

import com.oyproj.domain.vo.HeatmapDayVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface UserActivityHeatmapDao {
    //最近12个月用户每日活跃事件数（发文章/评论/回复/点赞/收藏），窗口下界由Java侧传入
    List<HeatmapDayVo> listActivityDays(@Param("userId") String userId,
                                        @Param("startDate") LocalDate startDate);
}
