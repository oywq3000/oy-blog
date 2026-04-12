package com.oyproj.api.article.domain;

import lombok.Data;

@Data
public class UserArticleStatDto {
    private Integer articleCount;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
}
