package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.Article;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleDaoImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleDao {

    /**
     * 根据slug查询文章
     *
     * @param slug SEO别名
     * @return 文章实体
     */
    @Override
    public Article getBySlug(String slug) {
        return baseMapper.selectOne(new LambdaQueryWrapper<Article>().eq(Article::getSlug, slug));
    }

    /**
     * 统计用户的文章数量
     *
     * @param authorId 作者ID
     * @return 文章数量
     */
    @Override
    public Long countByAuthorId(String authorId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, "published"));
    }

    /**
     * 查询作者的文章列表
     *
     * @param authorId 作者ID
     * @return 文章列表
     */
    @Override
    public List<Article> listByAuthor(String authorId) {
        return baseMapper.selectList(new LambdaQueryWrapper<Article>().eq(Article::getAuthorId, authorId));
    }

    /**
     * 查询发布的文章
     *
     * @return 发布文章列表
     */
    @Override
    public List<Article> listPublished() {
        return baseMapper.selectList(new LambdaQueryWrapper<Article>().eq(Article::getStatus, "published"));
    }
}

