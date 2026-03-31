package com.oyproj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oyproj.domain.entity.ArticleAttachment;
import org.apache.ibatis.annotations.Mapper;

/**
 *  文章附件映射器
 */
@Mapper
public interface ArticleAttachmentMapper extends BaseMapper<ArticleAttachment> {}

