package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentAuditDto {
    @NotBlank(message = "评论ID不能为空")
    private String commentId;
    /** 1=通过 2=拒绝 */
    @NotNull(message = "审核结果不能为空")
    private Integer status;
    private String reason;
}
