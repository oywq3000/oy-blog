package com.oyproj.api.article.client.fallback;

import com.oyproj.api.article.client.AdminArticleClient;
import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.utils.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AdminArticleClientFallbackFactory implements FallbackFactory<AdminArticleClient> {

    @Override
    public AdminArticleClient create(Throwable cause) {
        return new AdminArticleClient() {
            @Override
            public Result<PageVo<List<ArticleAdminItemVo>>> adminArticlePage(ArticleAdminPageDto dto) {
                log.warn("文章服务管理接口调用失败(分页文章)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<String> saveDraft(ArticleSaveDto dto) {
                log.warn("文章服务管理接口调用失败(保存草稿)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Map<String, String>> publish(ArticleSaveDto dto) {
                log.warn("文章服务管理接口调用失败(发布文章)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> deleteArticle(String id) {
                log.warn("文章服务管理接口调用失败(删除文章)，文章ID: {}, 错误: {}", id, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<String> saveTag(TagSaveDto dto) {
                log.warn("文章服务管理接口调用失败(保存标签)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> deleteTag(String id) {
                log.warn("文章服务管理接口调用失败(删除标签)，标签ID: {}, 错误: {}", id, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<List<TagAdminVo>> listTags() {
                log.warn("文章服务管理接口调用失败(标签列表)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<String> saveSeries(SeriesSaveDto dto) {
                log.warn("文章服务管理接口调用失败(保存系列)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> deleteSeries(String id) {
                log.warn("文章服务管理接口调用失败(删除系列)，系列ID: {}, 错误: {}", id, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<List<SeriesAdminVo>> listSeries() {
                log.warn("文章服务管理接口调用失败(系列列表)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<PageVo<List<CommentAdminItemVo>>> adminCommentPage(CommentAdminPageDto dto) {
                log.warn("文章服务管理接口调用失败(分页评论)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> auditComment(CommentAuditDto dto) {
                log.warn("文章服务管理接口调用失败(评论审核)，错误: {}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> deleteComment(String id) {
                log.warn("文章服务管理接口调用失败(删除评论)，评论ID: {}, 错误: {}", id, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }

            @Override
            public Result<Boolean> pinComment(String id, Integer pinned) {
                log.warn("文章服务管理接口调用失败(置顶评论)，评论ID: {}, 错误: {}", id, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), I18nUtils.t("error.unavailable"));
            }
        };
    }
}
