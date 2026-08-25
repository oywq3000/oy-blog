package com.oyproj.dao;

import com.oyproj.domain.vo.DailyTrendVo;
import com.oyproj.domain.vo.TopArticleVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 统计看板只读 DAO（直连同库统计表聚合，写操作仍走各服务）
 */
@Mapper
public interface DashboardDao {

    Long countPublishedArticles();

    Long sumViews();

    Long sumLikes();

    Long sumComments();

    Long countUsers();

    /** 近 30 天每日访问量 */
    List<DailyTrendVo> listDailyViews();

    /** 热门文章 TOP10 */
    List<TopArticleVo> listTopArticles();
}
