package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleChapter;


import java.util.List;

/**
 * 章节数据访问接口
 */
public interface ArticleChapterDao extends IService<ArticleChapter> {

    /**
     * 按文章查询所有章节
     *
     * @param articleId 文章ID
     * @return 章节列表
     */
    List<ArticleChapter> listByArticle(String articleId);

    /**
     * 根据锚点查询章节
     *
     * @param articleId 文章ID
     * @param anchor 锚点
     * @return 章节
     */
    ArticleChapter getByAnchor(String articleId, String anchor);
}

