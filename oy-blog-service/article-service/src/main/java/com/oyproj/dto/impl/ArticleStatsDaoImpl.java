package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.mapper.ArticleStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 *  文章统计数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleStatsDaoImpl extends ServiceImpl<ArticleStatsMapper, ArticleStats> implements ArticleStatsDao {

    /**
     * 增加文章阅读量
     *
     * @param articleId 文章ID
     * @param delta 增加量
     */
    @Override
    public void incViews(String articleId, long delta) {
        baseMapper.update(null, new LambdaUpdateWrapper<ArticleStats>()
                .eq(ArticleStats::getArticleId, articleId)
                .setSql("views = views + " + Math.max(delta, 0)));
    }

    /**
     * 根据文章ID列表批量查询统计信息
     *
     * @param articleIds 文章ID列表
     * @return 文章统计列表
     */
    @Override
    public List<ArticleStats> listByArticleIds(List<String> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return baseMapper.selectList(new LambdaQueryWrapper<ArticleStats>()
                .in(ArticleStats::getArticleId, articleIds));
    }

    /**
     * 更新点赞数
     *
     * @param articleId 文章ID
     * @param delta 增量（正数点赞，负数取消）
     */
    @Override
    public void incLikes(String articleId, long delta) {
        baseMapper.update(null, new LambdaUpdateWrapper<ArticleStats>()
                .eq(ArticleStats::getArticleId, articleId)
                .setSql("likes = GREATEST(likes + " + delta + ", 0)"));
    }

    /**
     * 更新收藏数
     *
     * @param articleId 文章ID
     * @param delta 增量（正数收藏，负数取消）
     */
    @Override
    public void incFavorites(String articleId, long delta) {
        baseMapper.update(null, new LambdaUpdateWrapper<ArticleStats>()
                .eq(ArticleStats::getArticleId, articleId)
                .setSql("favorites = GREATEST(favorites + " + delta + ", 0)"));
    }
}

