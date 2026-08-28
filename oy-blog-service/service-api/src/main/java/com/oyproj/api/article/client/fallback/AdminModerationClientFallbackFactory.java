package com.oyproj.api.article.client.fallback;

import com.oyproj.api.article.client.AdminModerationClient;
import com.oyproj.api.article.domain.dto.ArticleModerationAuditDto;
import com.oyproj.api.article.domain.dto.ArticleModerationPageDto;
import com.oyproj.api.article.domain.vo.ArticleModerationItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.utils.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AdminModerationClientFallbackFactory implements FallbackFactory<AdminModerationClient> {

    @Override
    public AdminModerationClient create(Throwable cause) {
        return new AdminModerationClient() {
            @Override
            public Result<PageVo<List<ArticleModerationItemVo>>> adminModerationPage(ArticleModerationPageDto dto) {
                log.warn("文章服务审核接口调用失败(待审队列)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> auditArticleModeration(ArticleModerationAuditDto dto) {
                log.warn("文章服务审核接口调用失败(审核操作)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }
        };
    }
}
