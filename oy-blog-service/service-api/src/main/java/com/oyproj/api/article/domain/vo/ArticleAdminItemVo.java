package com.oyproj.api.article.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleAdminItemVo {
    private String id;
    private String title;
    private String summary;
    private String status;
    private String coverUrl;
    private LocalDateTime publishAt;
    private LocalDateTime updateAt;
    private Long views;
    private Long likes;
    private Long comments;
}
