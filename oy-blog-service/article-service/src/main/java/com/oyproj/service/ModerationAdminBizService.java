package com.oyproj.service;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 文章人工审核后台业务
 */
public interface ModerationAdminBizService {
    /** 待审队列：pending_review 新文章 + 已发布文章的待审编辑，两类合并分页 */
    Result<PageVo<List<ArticleModerationItemVo>>> adminPage(ArticleModerationPageDto dto);

    /** 人工审核：通过/驳回 */
    Result<Boolean> audit(ArticleModerationAuditDto dto);
}
