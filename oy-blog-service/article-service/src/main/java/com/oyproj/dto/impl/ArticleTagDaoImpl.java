package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.ArticleTag;
import com.oyproj.domain.entity.Tag;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.mapper.ArticleTagMapper;
import com.oyproj.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @description 文章-标签关联数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleTagDaoImpl extends ServiceImpl<ArticleTagMapper, ArticleTag> implements ArticleTagDao {

    private final TagMapper tagMapper;

    /**
     * 批量查询文章的标签名列表：article_tag 按文章批量查 → tag 按ID批量查名称 → 内存组装
     * （模式同 ArticleIndexController.getIndexSnapshot 的标签批量加载）
     *
     * @param articleIds 文章ID列表
     * @return articleId -> 标签名列表
     */
    @Override
    public Map<String, List<String>> listTagNamesByArticleIds(List<String> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> tagMap = new HashMap<>();
        List<ArticleTag> relations = baseMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIds));
        if (relations.isEmpty()) {
            return tagMap;
        }
        List<String> tagIds = relations.stream().map(ArticleTag::getTagId).distinct().toList();
        Map<String, String> tagIdToName = new HashMap<>();
        tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds))
                .forEach(t -> tagIdToName.put(t.getId(), t.getName()));
        for (ArticleTag relation : relations) {
            String name = tagIdToName.get(relation.getTagId());
            if (name != null) {
                tagMap.computeIfAbsent(relation.getArticleId(), k -> new ArrayList<>()).add(name);
            }
        }
        return tagMap;
    }
}
