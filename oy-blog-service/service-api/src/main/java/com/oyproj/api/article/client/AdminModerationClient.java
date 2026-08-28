package com.oyproj.api.article.client;

import com.oyproj.api.article.client.fallback.AdminModerationClientFallbackFactory;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.api.config.AdminFeignConfig;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 文章审核管理接口 Feign 客户端（admin-service 使用）
 */
@FeignClient(value = "article-service", contextId = "admin-moderation-client",
        configuration = AdminFeignConfig.class,
        fallbackFactory = AdminModerationClientFallbackFactory.class)
public interface AdminModerationClient {

    @PostMapping("/article/moderation/admin/page")
    Result<PageVo<List<ArticleModerationItemVo>>> adminModerationPage(@RequestBody ArticleModerationPageDto dto);

    @PostMapping("/article/moderation/admin/audit")
    Result<Boolean> auditArticleModeration(@RequestBody ArticleModerationAuditDto dto);
}
