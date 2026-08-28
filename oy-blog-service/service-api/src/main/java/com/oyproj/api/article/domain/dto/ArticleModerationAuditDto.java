package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArticleModerationAuditDto {
    @NotBlank(message = "文章ID不能为空")
    private String articleId;
    /** true=通过 false=驳回 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approve;
    private String reason;
}
