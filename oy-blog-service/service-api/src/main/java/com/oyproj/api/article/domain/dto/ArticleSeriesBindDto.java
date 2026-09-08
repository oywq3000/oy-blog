package com.oyproj.api.article.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class ArticleSeriesBindDto {
    private List<String> seriesIds;
}
