package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.ArticleSeriesItem;
import com.oyproj.dto.ArticleSeriesItemDao;
import com.oyproj.mapper.ArticleSeriesItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章系列项数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleSeriesItemDaoImpl extends ServiceImpl<ArticleSeriesItemMapper, ArticleSeriesItem> implements ArticleSeriesItemDao {

    /**
     * 根据系列ID查询项列表
     *
     * @param seriesId 系列ID
     * @return 项列表
     */
    @Override
    public List<ArticleSeriesItem> listItems(Long seriesId) {
        return baseMapper.selectList(new LambdaQueryWrapper<ArticleSeriesItem>()
                .eq(ArticleSeriesItem::getSeriesId, seriesId)
                .orderByAsc(ArticleSeriesItem::getSortOrder));
    }
}

