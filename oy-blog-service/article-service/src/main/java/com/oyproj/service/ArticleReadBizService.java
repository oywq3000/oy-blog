package com.oyproj.service;


import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.vo.ArticleChapterVo;
import com.oyproj.domain.vo.ArticleContentVo;
import com.oyproj.domain.vo.ArticleInfoVo;
import com.oyproj.domain.vo.TagStatVo;

import java.util.List;

/**
 * 文章阅读查询业务服务接口
 */
public interface ArticleReadBizService {

    /**
     * 根据slug查询文章
     *
     * @param slug SEO别名
     * @return 文章
     */
    Result<ArticleInfoVo> getBySlug(String slug);
    
    /**
     * 查询文章内容
     *
     * @param articleId 文章ID
     * @return 文章内容
     */
    Result<ArticleContentVo> getContent(String articleId);

    /**
     * 查询文章章节目录
     *
     * @param articleId 文章ID
     * @return 章节列表
     */
    Result<List<ArticleChapterVo>> listChapters(String articleId);

    /**
     * 按发布时间分页查询已发布文章列表（置顶优先 + publishAt 降序）
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 分页的文章列表
     */
    Result<PageVo<List<ArticleInfoVo>>> listPublished(int pageNum, int pageSize);

    /**
     * 按作者分页查询该用户已发布文章列表（置顶优先 + publishAt 降序）
     *
     * @param authorId 作者ID
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 分页的文章列表
     */
    Result<PageVo<List<ArticleInfoVo>>> listPublishedByAuthor(String authorId, int pageNum, int pageSize);

    /**
     * 按热度分页查询已发布文章列表（加权评分降序）
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 分页的文章列表
     */
    Result<PageVo<List<ArticleInfoVo>>> listPublishedByHot(int pageNum, int pageSize);

    /**
     * 查询用户浏览历史
     *
     * @return 文章列表
     */
    Result<List<ArticleInfoVo>> listHistory();

    /**
     * 查询热门标签
     *
     * @return 标签列表
     */
    Result<List<TagStatVo>> listPopularTags();

    /**
     * 根据文章Id查询文章基础信息
     *
     * @param articleId 文章ID
     * @return 文章信息
     */
    Result<ArticleInfoVo> getById(String articleId);

    /**
     * 查询当前用户的文章列表（按状态分页）
     *
     * @param status 文章状态 (published/draft，或 all=三个审核中状态合并)
     * @return 分页的文章列表
     */
    Result<PageVo<List<ArticleInfoVo>>> listMine(String status);
}

