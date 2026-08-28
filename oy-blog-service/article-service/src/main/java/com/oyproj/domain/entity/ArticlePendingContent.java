package com.oyproj.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章待生效编辑（已发布文章的编辑被 AI 判"有歧义"时暂存，人工通过后替换生效）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_pending_content")
public class ArticlePendingContent {

    /**
     * 文章ID（复用 article.id，一篇最多一份待审编辑）
     */
    @TableId(value = "article_id")
    private String articleId;

    /**
     * 待生效标题
     */
    @TableField("pending_title")
    private String pendingTitle;

    /**
     * 待生效摘要
     */
    @TableField("pending_summary")
    private String pendingSummary;

    /**
     * 待生效 Markdown 正文
     */
    @TableField("pending_content_md")
    private String pendingContentMd;

    /**
     * 待生效 HTML 正文
     */
    @TableField("pending_content_html")
    private String pendingContentHtml;

    /**
     * AI 转人工理由
     */
    @TableField("review_reason")
    private String reviewReason;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
