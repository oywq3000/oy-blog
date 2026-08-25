package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopArticleVo {
    private String id;
    private String title;
    private Long views;
    private Long likes;
    private Long comments;
}
