package com.oyproj.api.article.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章待审队列项（NEW=待审新文章；EDIT=已发布文章的待审编辑）
 */
@Data
public class ArticleModerationItemVo {
    private String articleId;
    /** NEW / EDIT */
    private String kind;
    /** NEW=待审文章标题；EDIT=当前对外展示的旧标题 */
    private String title;
    private String authorId;
    private String summary;
    /** AI 转人工理由 */
    private String reviewReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    /** 仅 EDIT：待生效标题 */
    private String pendingTitle;
    /** 仅 EDIT：待生效摘要 */
    private String pendingSummary;
}
