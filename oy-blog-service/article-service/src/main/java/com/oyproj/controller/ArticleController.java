package com.oyproj.controller;
import com.oyproj.api.article.domain.UserArticleStatDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.file.domain.vo.FileVo;
import com.oyproj.common.base.OpLog;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.vo.ArticleInfoVo;
import com.oyproj.domain.vo.HeatmapDayVo;
import com.oyproj.domain.vo.SeriesReadVo;
import com.oyproj.service.ArticleBizService;
import com.oyproj.service.ArticleCommonBizService;
import com.oyproj.service.ArticleReadBizService;
import com.oyproj.service.ArticleSeriesBizService;
import com.oyproj.service.ArticleStatsBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/**
 *  文章管理控制器
 */
@Tag(name = "文章管理控制器", description = "文章发布、草稿、删除等管理操作")
@RestController
@RequiredArgsConstructor
@RequestMapping("/article")
public class ArticleController {

    @NotNull private final ArticleBizService biz;
    @NotNull private final ArticleCommonBizService commonBiz;
    @NotNull private final ArticleStatsBizService statsBiz;
    @NotNull private final ArticleReadBizService readBiz;
    @NotNull private final ArticleSeriesBizService seriesBiz;

    /**
     * 保存草稿
     *
     * @param dto 文章信息
     * @return 文章ID
     */
    @PostMapping("/draft")
    @Operation(summary = "保存草稿", description = "保存文章为草稿")
    @OpLog(action = "save_draft", func = "article.draft")
    // @SaCheckPermission(value = PermissionConstants.ARTICLE_CREATE) 编辑草稿
    public Result<String> saveDraft(@RequestBody ArticleSaveDto dto) {
        return biz.saveDraft(dto);
    }

    /**
     * 发布文章
     *
     * @param dto 文章信息
     * @return 文章ID
     */
    @PostMapping("/publish")
    @Operation(summary = "发布文章", description = "发布文章")
    @OpLog(action = "publish_article", func = "article.publish")
    public Result<Map<String, String>> publish(@RequestBody ArticleSaveDto dto) {
        return biz.publish(dto);
    }

    /**
     * 上传文章封面
     *
     * @param file 封面文件
     * @return 文件信息
     */
    @PostMapping("/cover")
    @Operation(summary = "上传文章封面", description = "上传文章封面图片")
    public Result<FileVo> uploadCover(@RequestPart("file") MultipartFile file) {
        return commonBiz.uploadCover(file);
    }

    /**
     * 上传文章内容图片
     *
     * @param file 图片文件
     * @return 文件信息
     */
    @PostMapping("/image")
    @Operation(summary = "上传文章内容图片", description = "上传文章正文中的图片")
    public Result<FileVo> uploadContentImage(@RequestPart("file") MultipartFile file) {
        return commonBiz.uploadContentImage(file);
    }

    /**
     * 判断是否为当前用户的文章
     *
     * @param articleId 文章ID
     * @return 是否为当前用户的文章
     */
    @GetMapping("/{articleId}/check")
    @Operation(summary = "检查文章是否为当前用户", description = "根据ID检查文章是否为当前登录用户的文章")
    public Result<Boolean> checkOwnership(@PathVariable("articleId") String articleId) {
        return biz.checkOwnership(articleId);
    }

    /**
     * 删除文章
     *
     * @param id 文章ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除文章", description = "根据ID删除文章（软删除）")
    @OpLog(action = "delete_article", func = "article.delete")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return biz.delete(id);
    }

    /**
     * 获取当前用户文章统计
     *
     * @return 统计信息
     */
    @GetMapping("/stats/me")
    @Operation(summary = "获取当前用户文章统计", description = "获取当前登录用户的文章统计信息")
    public Result<UserArticleStatDto> getMyStats() {
        return biz.getMyStats();
    }

    /**
     * 获取当前用户活跃度热力图
     *
     * @return 最近12个月每日活跃数据（发文章/评论/回复/点赞/收藏），游客返回空列表
     */
    @GetMapping("/stats/heatmap/me")
    @Operation(summary = "获取当前用户活跃度热力图", description = "最近12个月发文章/评论/回复/点赞/收藏的每日活跃数据，游客返回空列表")
    public Result<List<HeatmapDayVo>> getMyHeatmap() {
        return statsBiz.getMyHeatmap();
    }

    /**
     * 获取指定用户活跃度热力图（公开，他人主页展示用）
     *
     * @param userId 用户ID
     * @return 最近12个月每日活跃数据
     */
    @GetMapping("/stats/heatmap/{userId}")
    @Operation(summary = "获取指定用户活跃度热力图", description = "最近12个月发文章/评论/回复/点赞/收藏的每日活跃数据")
    public Result<List<HeatmapDayVo>> getUserHeatmap(@PathVariable("userId") @NotNull String userId) {
        return statsBiz.getUserHeatmap(userId);
    }

    /**
     * 获取指定用户文章统计
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    @GetMapping("/stats/{userId}")
    @Operation(summary = "获取指定用户文章统计", description = "获取指定用户的文章统计信息")
    public Result<UserArticleStatDto> getUserStats(@PathVariable("userId") String userId) {
        return biz.getUserStats(userId);
    }

    /**
     * 查询当前用户的文章列表（按状态分页）
     *
     * @param status 文章状态：published（已发布）、draft（草稿），或 all（全部审核中：AI 审核中/待人工审核/已驳回），默认 published
     * @return 分页的文章列表（含 total / currentPage / totalPages）
     */
    @GetMapping("/creator/me")
    @Operation(summary = "查询当前用户的文章列表", description = "按状态分页查询当前登录用户的文章，支持 published、draft 及 all（三个审核中状态合并）")
    public Result<PageVo<List<ArticleInfoVo>>> listMine(
            @Parameter(description = "文章状态：published / draft / all（全部审核中）", example = "published")
            @RequestParam(defaultValue = "published") String status) {
        return readBiz.listMine(status);
    }

    // ================================================================
    //  创作端专栏 CRUD（spec §九，登录态；网关白名单外的 /article/** 均需认证）
    //  鉴权约定同 /creator/me：无需 @RequirePermission，越权校验在业务层按归属做
    // ================================================================

    /**
     * 查询我的专栏列表（含已发布文章计数）
     *
     * @return 当前登录用户创建的专栏列表
     */
    @GetMapping("/creator/series")
    @Operation(summary = "查询我的专栏列表", description = "当前登录用户创建的专栏（含已发布公开文章计数）")
    public Result<List<SeriesReadVo>> listMySeries(HttpServletRequest request) {
        return Result.ok(seriesBiz.listOwnSeries(request.getHeader(HeaderConstant.USER_ID.getValue())));
    }

    /**
     * 新建专栏
     *
     * @param dto 专栏信息（name 必填，description/coverUrl 可选）
     * @return 新专栏 ID
     */
    @PostMapping("/creator/series")
    @Operation(summary = "创建专栏", description = "以当前登录用户为创建者新建专栏")
    public Result<String> createSeries(@RequestBody @Valid SeriesSaveDto dto, HttpServletRequest request) {
        // Result.ok(String) 会被解析为 ok(errMsg)（data=null），返回 id 必须走二参 ok(data, errMsg)
        String id = seriesBiz.createSeries(dto, request.getHeader(HeaderConstant.USER_ID.getValue()));
        return Result.ok(id, I18nUtils.from(ResultCode.SUCCESS));
    }

    /**
     * 更新专栏（改名/描述/封面，仅创建者；ADMIN 例外）
     *
     * @param id  专栏 ID
     * @param dto 新值
     * @return 是否成功
     */
    @PutMapping("/creator/series/{id}")
    @Operation(summary = "更新专栏", description = "改名/描述/封面（仅专栏创建者；ADMIN 例外）")
    public Result<Boolean> updateSeries(@PathVariable("id") String id, @RequestBody @Valid SeriesSaveDto dto,
                                        HttpServletRequest request) {
        seriesBiz.updateSeries(id, dto, request.getHeader(HeaderConstant.USER_ID.getValue()), isAdminUser(request));
        return Result.ok(true);
    }

    /**
     * 删除专栏（级联清空成员，仅创建者；ADMIN 例外）
     *
     * @param id 专栏 ID
     * @return 是否成功
     */
    @DeleteMapping("/creator/series/{id}")
    @Operation(summary = "删除专栏", description = "删除专栏并清空其成员（仅专栏创建者；ADMIN 例外）")
    public Result<Boolean> deleteSeries(@PathVariable("id") String id, HttpServletRequest request) {
        seriesBiz.deleteOwnSeries(id, request.getHeader(HeaderConstant.USER_ID.getValue()), isAdminUser(request));
        return Result.ok(true);
    }

    /**
     * 当前请求是否管理员（读网关注入的 X-User-Type；判定口径与 RequirePermissionInterceptor 一致，勿另造常量）
     */
    private boolean isAdminUser(HttpServletRequest request) {
        return BlogRole.ADMIN.name().equals(request.getHeader(HeaderConstant.USER_TYPE.getValue()));
    }

}

