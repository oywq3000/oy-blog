package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
     * 查询已发布且未删除的文章
     *
     * @return 发布文章列表
     */
    @Override
    public List<Article> listPublished() {
        return baseMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .isNull(Article::getDeletedAt)
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getPublishAt)
                .orderByDesc(Article::getCreatedAt)
                .orderByDesc(Article::getId));
    }

    /**
     * 分页查询已发布未删除的文章
     */
    @Override
    public List<Article> listPublished(int page, int size) {
        return baseMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, "published")
                        .isNull(Article::getDeletedAt)
                        .orderByDesc(Article::getCreatedAt))
                .getRecords();
    }

    /**
     * 统计已发布未删除的文章数量
     */
    @Override
    public Long countPublished() {
        return baseMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .isNull(Article::getDeletedAt));
    }

    /**
     * 按作者和状态分页查询文章；status 为 all 时合并查询三个审核中状态
     */
    @Override
    public List<Article> listByAuthorAndStatus(String authorId, String status, Page<Article> page) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .isNull(Article::getDeletedAt)
                .orderByDesc(Article::getUpdatedAt);
        if ("all".equals(status)) {
            // all = 三个审核中状态合并查询（AI 审核中 / 待人工审核 / 已驳回）
            wrapper.in(Article::getStatus, "ai_reviewing", "pending_review", "rejected");
        } else {
            wrapper.eq(Article::getStatus, status);
        }
        return baseMapper.selectPage(page, wrapper).getRecords();
    }

    /**
     * 按作者和状态统计文章数量
     */
    @Override
    public Long countByAuthorAndStatus(String authorId, String status) {
        return baseMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, status)
                .isNull(Article::getDeletedAt));
    }

    /**
     * 按发布时间分页查询已发布未删除的文章（置顶优先 + publishAt/createdAt/id 降序）
     */
    @Override
    public List<Article> listPublishedByTime(int pageNum, int pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, "published")
                        .isNull(Article::getDeletedAt)
                        .orderByDesc(Article::getIsTop)
                        .orderByDesc(Article::getPublishAt)
                        .orderByDesc(Article::getCreatedAt)
                        .orderByDesc(Article::getId))
                .getRecords();
    }

    /**
     * 按热度分页查询已发布未删除的文章（加权评分降序 + id 降序兜底）
     */
    @Override
    public List<Article> listPublishedByHot(int pageNum, int pageSize,
                                            long wViews, long wLikes, long wComments, long wFavorites) {
        int offset = (pageNum - 1) * pageSize;
        return baseMapper.selectHotPage(offset, pageSize, wViews, wLikes, wComments, wFavorites);
    }
}

