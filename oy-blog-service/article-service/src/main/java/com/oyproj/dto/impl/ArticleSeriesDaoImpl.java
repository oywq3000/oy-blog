package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.ArticleSeries;
import com.oyproj.dto.ArticleSeriesDao;
import com.oyproj.mapper.ArticleSeriesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * @author LX
 * @date 2025/12/03
 * @description 文章系列数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleSeriesDaoImpl extends ServiceImpl<ArticleSeriesMapper, ArticleSeries> implements ArticleSeriesDao {

    /**
     * 根据唯一编码查询系列
     *
     * @param code 唯一编码
     * @return 文章系列
     */
    @Override
    public ArticleSeries getByCode(String code) {
        return baseMapper.selectOne(new LambdaQueryWrapper<ArticleSeries>().eq(ArticleSeries::getCode, code));
    }
}

