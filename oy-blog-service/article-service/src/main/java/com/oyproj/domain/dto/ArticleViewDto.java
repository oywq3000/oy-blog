package com.oyproj.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

/**
 * 文章阅读记录DTO
 */
@Data
@Builder
public class ArticleViewDto {
    @NotBlank
    private String articleId;
    private String ip;
    private String ua;
}
