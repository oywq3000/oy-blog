package com.oyproj.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评论点赞或踩DTO
 */
@Data
public class CommentReactionDto {

    /**
     * 文章ID（必填）
     */
    @NotNull(message = "文章ID不能为空")
    private String articleId;

    /**
     * 评论ID（可空）
     */
    private String commentId;

    /**
     * 回复ID（可空）
     */
    private String replyId;

    /**
     * 操作类型（like/dislike）
     */
    @NotNull(message = "操作类型不能为空")
    private String type;
}
