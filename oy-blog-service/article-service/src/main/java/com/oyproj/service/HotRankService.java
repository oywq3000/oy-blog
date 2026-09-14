package com.oyproj.service;

import com.oyproj.common.mq.domain.ArticleBehaviorEvent;

import java.util.List;

/**
 * 热榜服务：事件累加、窗口合并、榜单读取。
 */
public interface HotRankService {

    /** 榜单周期参数：近 7 天（周榜） */
    String PERIOD_WEEK = "7d";
    /** 榜单周期参数：近 30 天（月榜） */
    String PERIOD_MONTH = "30d";
    /** 榜单周期参数：近 90 天（季榜） */
    String PERIOD_QUARTER = "90d";

    /** 把一条行为事件累加进它<b>发生当日</b>的日桶 */
    void recordEvent(ArticleBehaviorEvent event);

    /** 合并日桶，重算出近 7 天榜与趋势榜 */
    void recompute();

    /** 按热度降序取榜单中的文章 id；Redis 异常时返回空列表 */
    List<String> topArticleIds(String rankKey, int limit);
}
