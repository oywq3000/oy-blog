package com.oyproj.service;

import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 管理端文章审核 BFF 业务
 */
public interface AdminModerationBizService {
    Result<PageVo<List<ArticleModerationItemVo>>> page(ArticleModerationPageDto dto);
    Result<Boolean> audit(ArticleModerationAuditDto dto);
}
