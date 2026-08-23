package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.Article;
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
}
