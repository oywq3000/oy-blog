package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.vo.SeriesMemberCountVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章映射器
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 按热度分页查询已发布未删除的文章
     *
     * <p>热度分 = views*权重 + likes*权重 + comments*权重 + favorites*权重（LEFT JOIN 缺失统计行按 0 分参与排序），
     * 评分降序后再按 id 降序兜底，保证同分跨页排序稳定。</p>
     *
     * @param offset 偏移量（0-based）
     * @param size   每页大小
     * @return 文章列表
     */
    @Select("SELECT a.* FROM article a " +
            "LEFT JOIN article_stats s ON a.id = s.article_id " +
            "WHERE a.status = 'published' AND a.deleted_at IS NULL " +
            "ORDER BY (COALESCE(s.views,0) * #{wViews} + COALESCE(s.likes,0) * #{wLikes} + " +
            "           COALESCE(s.comments,0) * #{wComments} + COALESCE(s.favorites,0) * #{wFavorites}) DESC, " +
            "         a.id DESC " +
            "LIMIT #{offset}, #{size}")
    List<Article> selectHotPage(@Param("offset") int offset, @Param("size") int size,
                                @Param("wViews") long wViews, @Param("wLikes") long wLikes,
                                @Param("wComments") long wComments, @Param("wFavorites") long wFavorites);

    /**
     * 分页查询专栏的已发布未删除成员文章
     *
     * <p>只收录 published 且未软删除的文章；按 sort_order 升序、publish_at 降序展示。</p>
     *
     * @param seriesId 专栏 id
     * @param offset   偏移量（0-based）
     * @param size     每页大小
     * @return 专栏成员文章列表
     */
    @Select("SELECT a.* FROM article_series_item si JOIN article a ON a.id = si.article_id " +
            "AND a.status = 'published' AND a.deleted_at IS NULL " +
            "WHERE si.series_id = #{seriesId} " +
            "ORDER BY si.sort_order ASC, a.publish_at DESC LIMIT #{offset}, #{size}")
    List<Article> selectSeriesMemberPage(@Param("seriesId") String seriesId,
                                         @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计专栏的已发布未删除成员文章数
     *
     * @param seriesId 专栏 id
     * @return 有效成员数
     */
    @Select("SELECT COUNT(*) FROM article_series_item si JOIN article a ON a.id = si.article_id " +
            "AND a.status = 'published' AND a.deleted_at IS NULL WHERE si.series_id = #{seriesId}")
    long selectSeriesMemberCount(@Param("seriesId") String seriesId);

    /**
     * 按专栏分组统计已发布未删除成员文章数（专栏列表角标用）
     *
     * @return 每栏有效成员数（无有效成员的专栏不出现）
     */
    @Select("SELECT si.series_id, COUNT(*) AS article_count FROM article_series_item si " +
            "JOIN article a ON a.id = si.article_id AND a.status = 'published' AND a.deleted_at IS NULL " +
            "GROUP BY si.series_id")
    List<SeriesMemberCountVo> selectPublishedCountGroupBySeries();
}
