package com.oyproj.controller;

import com.oyproj.api.file.domain.vo.FileVo;
import com.oyproj.common.base.OpLog;
import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.vo.UserArticleStatsVo;
import com.oyproj.service.ArticleBizService;
import com.oyproj.service.ArticleCommonBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.oyproj.api.article.domain.UserArticleStatDto;
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
     * 根据用户id统计文章
     */


}

