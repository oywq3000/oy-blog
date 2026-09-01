package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.Article;

import java.util.List;

/**
 * @description 文章数据访问接口
 */
public interface ArticleDao extends IService<Article> {

    /**
     * 根据slug查询文章
     * @param slug SEO别名
     * @return 文章实体
     */
    Article getBySlug(String slug);

    /**
     * 查询作者的文章列表
     *
     * @param authorId 作者ID
     * @return 文章列表
     */
    List<Article> listByAuthor(String authorId);

    /**
     * 查询发布的文章
     *
     * @return 发布文章列表
     */
    List<Article> listPublished();

    /**
     * 分页查询已发布未删除的文章
     *
     * @param page 页码（0-based）
     * @param size 每页大小
     * @return 文章列表
     */
    List<Article> listPublished(int page, int size);

    /**
     * 统计已发布未删除的文章数量
     *
     * @return 文章数量
     */
    Long countPublished();

    /**
     * 统计用户的文章数量
     *
     * @param authorId 作者ID
     * @return 文章数量
     */
    Long countByAuthorId(String authorId);

    /**
     * 统计指定作者已发布未删除的文章数量
     *
     * @param authorId 作者ID
     * @return 文章数量
     */
    Long countPublishedByAuthor(String authorId);

    /**
     * 按作者分页查询已发布未删除的文章（置顶优先 + publishAt/createdAt/id 降序）
     *
     * @param authorId 作者ID
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 文章列表
     */
    List<Article> listPublishedByAuthor(String authorId, int pageNum, int pageSize);

    /**
     * 按作者和状态分页查询文章
     *
     * @param authorId 作者ID
     * @param status   文章状态 (published/draft/ai_reviewing/pending_review/rejected，或 all=三个审核中状态合并)
     * @param page     MP 分页对象（传入 pageNum/pageSize，查询后填充 total/pages）
     * @return 文章列表
     */
    List<Article> listByAuthorAndStatus(String authorId, String status, Page<Article> page);

    /**
     * 按作者和状态统计文章数量
     *
     * @param authorId 作者ID
     * @param status 文章状态 (published/draft)
     * @return 文章数量
     */
    Long countByAuthorAndStatus(String authorId, String status);

    /**
     * 按发布时间分页查询已发布未删除的文章（置顶优先 + publishAt/createdAt/id 降序）
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 文章列表
     */
    List<Article> listPublishedByTime(int pageNum, int pageSize);

    /**
     * 按热度分页查询已发布未删除的文章
     *
     * @param pageNum   页码（1-based）
     * @param pageSize  每页大小
     * @param wViews    浏览量权重
     * @param wLikes    点赞数权重
     * @param wComments 评论数权重
     * @param wFavorites 收藏数权重
     * @return 文章列表
     */
    List<Article> listPublishedByHot(int pageNum, int pageSize,
                                     long wViews, long wLikes, long wComments, long wFavorites);
}

