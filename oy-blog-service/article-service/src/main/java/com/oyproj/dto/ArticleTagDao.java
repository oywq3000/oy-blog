package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleTag;

import java.util.List;
import java.util.Map;


/**
 * 文章-标签关联数据访问接口
 */
public interface ArticleTagDao extends IService<ArticleTag> {

    /**
     * 批量查询文章的标签名列表（无关联的文章在返回 Map 中无 key）
     *
     * @param articleIds 文章ID列表
     * @return articleId -> 标签名列表（保持 article_tag 行顺序）
     */
    Map<String, List<String>> listTagNamesByArticleIds(List<String> articleIds);

    /**
     * 批量查询文章的标签ID（去重）
     *
     * @param articleIds 文章ID列表
     * @return 去重后的 tag_id 列表
     */
    List<String> listTagIdsByArticleIds(List<String> articleIds);

    /**
     * 批量查询标签的关联行
     *
     * @param tagIds 标签ID列表
     * @return tag_id IN 的 article_tag 关联行
     */
    List<ArticleTag> listByTagIds(List<String> tagIds);

    /**
     * 批量查询文章的标签ID映射
     *
     * @param articleIds 文章ID列表
     * @return articleId -> tag_id 列表
     */
    Map<String, List<String>> listTagIdMapByArticleIds(List<String> articleIds);
}
