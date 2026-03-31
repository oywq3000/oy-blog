package com.oyproj.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 文章保存/发布请求参数
 */
@Data
public class ArticleSaveDto {
    /**
     * 文章ID（更新时必填，新建时为空）
     */
    @Schema(description = "文章ID", example = "uuid")
    private String id;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    @Schema(description = "标题", example = "我的第一篇文章")
    private String title;

    /**
     * 摘要
     */
    @Schema(description = "摘要", example = "这是文章摘要...")
    private String summary;

    /**
     * 内容（Markdown）
     */
    @NotBlank(message = "内容不能为空")
    @Schema(description = "文章内容(Markdown)", example = "# Hello World\n...")
    private String contentMd;

    /**
     * 内容（HTML）
     */
    @NotBlank(message = "HTML内容不能为空")
    @Schema(description = "文章内容(HTML)", example = "<h1>Hello World</h1>...")
    private String contentHtml;

    /**
     * 封面图
     */
    @Schema(description = "封面图URL", example = "https://example.com/cover.jpg")
    private String coverUrl;

    /**
     * 分类编码
     */
    @Schema(description = "分类编码", example = "tech")
    private String categoryCode;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表", example = "[\"Java\", \"Spring\"]")
    private List<String> tags;

    /**
     * 是否允许评论
     */
    @Schema(description = "是否允许评论", example = "1")
    private Integer allowComment;
}
