package com.oyproj.api.article.domain.dto;

import lombok.Data;

@Data
public class ArticleModerationPageDto {
    private Integer page = 1;
    private Integer size = 10;
}
