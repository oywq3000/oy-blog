package com.oyproj.controller;
import com.oyproj.common.base.OpLog;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.vo.*;
import com.oyproj.service.ArticleReadBizService;
import com.oyproj.service.ArticleStatsBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author LX
 * @date 2025/12/03
 * @description 文章阅读查询控制器
 */
@Tag(name = "文章阅读查询控制器", description = "文章基础信息、内容、已发布文章查询")
@RestController
@RequestMapping("/article/read")
@RequiredArgsConstructor
public class ArticleReadController {

    @NotNull private final ArticleReadBizService biz;
    @NotNull private final ArticleStatsBizService statsBiz;
    /**
     * 根据slug查询文章
     *
     * @param slug SEO别名
     * @return 文章信息
     */
    @GetMapping("/by-slug/{slug}")
    @OpLog(action = "view", func = "article.view")
    @Operation(summary = "根据slug查询文章", description = "根据SEO别名查询文章基础信息")
    public Result<ArticleInfoVo> getBySlug(@PathVariable("slug") String slug) {
        //文章不多使用id查询代替
        return biz.getBySlug(slug);
    }

    /**
     * 根据文章Id查询文章基础信息
     *
     * @param articleId 文章ID
     * @return 文章信息
     */
    @GetMapping("/{articleId}")
    @Operation(summary = "根据文章Id查询文章基础信息", description = "根据文章ID查询文章基础信息")
    public Result<ArticleInfoVo> getById(@PathVariable("articleId") String articleId) {
        return biz.getById(articleId);
    }

    /**
     * 查询文章内容
     *
     * @param articleId 文章ID
     * @return 文章内容
     */
    @GetMapping("/{articleId}/content")
    @Operation(summary = "查询文章内容", description = "根据文章ID查询文章内容")
    public Result<ArticleContentVo> getContent(@PathVariable("articleId") String articleId) {
        return biz.getContent(articleId);
    }

    /**
     * 查询文章章节目录
     *
     * @param articleId 文章ID
     * @return 章节列表
     */
    @GetMapping("/{articleId}/chapters")
    @Operation(summary = "查询文章章节目录", description = "根据文章ID查询文章章节目录")
    public Result<List<ArticleChapterVo>> listChapters(@PathVariable("articleId") String articleId) {
        return biz.listChapters(articleId);
    }

    /**
     * 按发布时间分页查询已发布文章列表
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页的文章列表
     */
    @GetMapping("/published")
    @Operation(summary = "按发布时间分页查询已发布文章列表", description = "查询已发布文章，置顶优先，其余按发布时间降序分页返回")
    public Result<PageVo<List<ArticleInfoVo>>> listPublished(
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") int pageSize) {
        return biz.listPublished(pageNum, pageSize);
    }

    /**
     * 按热度分页查询已发布文章列表
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页的文章列表
     */
    @GetMapping("/published/hot")
    @Operation(summary = "按热度分页查询已发布文章列表", description = "按 views*1 + likes*2 + comments*5 + favorites*3 加权评分降序分页返回（权重可在配置调整）")
    public Result<PageVo<List<ArticleInfoVo>>> listPublishedByHot(
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") int pageSize) {
        return biz.listPublishedByHot(pageNum, pageSize);
    }
    /**
     * 获取全库文章数据统计
     *
     * @return 已发布文章总数、阅读量总和、点赞数总和、标签总数
     */
    @GetMapping("/stats/global")
    @Operation(summary = "获取全库文章数据统计", description = "统计所有已发布文章总数、全库阅读量总和、点赞数总和、标签总数")
    public Result<ArticleStatsVo> getGlobalStats() {
        return statsBiz.getGlobalStats();
    }
    /**
     * 查询用户浏览历史
     *
     * @return 文章列表
     */
    @GetMapping("/history")
    @Operation(summary = "查询用户浏览历史", description = "查询当前登录用户的浏览历史")
    public Result<List<ArticleInfoVo>> listHistory() {
        return biz.listHistory();
    }

    /**
     * 查询热门标签
     *
     * @return 标签列表
     */
    @GetMapping("/tags/popular")
    @Operation(summary = "查询热门标签", description = "查询热门标签列表")
    public Result<List<TagStatVo>> listPopularTags() {
        return biz.listPopularTags();
    }


}


