package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 文章保存/发布请求参数（service-api 镜像，供 Feign 传输）
 */
@Data
public class ArticleSaveDto {
    private String id;
    @NotBlank(message = "标题不能为空")
    private String title;
    private String summary;
    @NotBlank(message = "内容不能为空")
    private String contentMd;
    private String contentHtml;
    private String coverUrl;
    private List<String> tags;
    private Integer allowComment;
}
