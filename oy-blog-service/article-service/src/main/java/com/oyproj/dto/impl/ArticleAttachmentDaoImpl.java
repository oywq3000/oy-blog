package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.ArticleAttachment;
import com.oyproj.dto.ArticleAttachmentDao;
import com.oyproj.mapper.ArticleAttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章附件数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ArticleAttachmentDaoImpl extends ServiceImpl<ArticleAttachmentMapper, ArticleAttachment> implements ArticleAttachmentDao {

    /**
     * 根据文章ID查询附件列表
     *
     * @param articleId 文章ID
     * @return 附件列表
     */
    @Override
    public List<ArticleAttachment> listByArticle(String articleId) {
        return baseMapper.selectList(new LambdaQueryWrapper<ArticleAttachment>().eq(ArticleAttachment::getArticleId, articleId));
    }
}

