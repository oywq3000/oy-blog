package com.oyproj.dto.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.ArticleRevision;
import com.oyproj.dto.ArticleRevisionDao;
import com.oyproj.mapper.ArticleRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 文章修订数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleRevisionDaoImpl extends ServiceImpl<ArticleRevisionMapper, ArticleRevision> implements ArticleRevisionDao {

    /**
     * 根据文章ID查询最新修订
     *
     * @param articleId 文章ID
     * @return 最新修订实体
     */
    @Override
    public ArticleRevision latest(String articleId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<ArticleRevision>()
                .eq(ArticleRevision::getArticleId, articleId)
                .orderByDesc(ArticleRevision::getSavedAt)
                .last("LIMIT 1"));
    }
}

