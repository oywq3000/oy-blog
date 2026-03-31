package com.oyproj.dto;

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
     * 统计用户的文章数量
     *
     * @param authorId 作者ID
     * @return 文章数量
     */
    Long countByAuthorId(String authorId);
}

