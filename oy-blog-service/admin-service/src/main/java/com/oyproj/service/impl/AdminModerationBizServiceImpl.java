package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminModerationClient;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminModerationBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端文章审核 BFF 实现：全部直接透传 Feign 结果
 */
@Service
@RequiredArgsConstructor
public class AdminModerationBizServiceImpl extends AdminBizBase implements AdminModerationBizService {

    private final AdminModerationClient client;

    @Override
    public Result<PageVo<List<ArticleModerationItemVo>>> page(ArticleModerationPageDto dto) {
        return client.adminModerationPage(dto);
    }

    @Override
    public Result<Boolean> audit(ArticleModerationAuditDto dto) {
        return client.auditArticleModeration(dto);
    }
}
