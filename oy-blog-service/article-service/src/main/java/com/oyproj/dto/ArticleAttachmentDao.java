package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleAttachment;


import java.util.List;

/**
 * 文章附件数据访问接口
 */
public interface ArticleAttachmentDao extends IService<ArticleAttachment> {

    /**
     * 根据文章ID查询附件列表
     *
     * @param articleId 文章ID
     * @return 附件列表
     */
    List<ArticleAttachment> listByArticle(String articleId);
}

