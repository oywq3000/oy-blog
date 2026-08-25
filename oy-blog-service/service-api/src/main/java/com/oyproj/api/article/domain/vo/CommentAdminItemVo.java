package com.oyproj.api.article.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentAdminItemVo {
    private String id;
    private String articleId;
    private String userId;
    private String content;
    private Integer status;
    private Integer isPinned;
    private LocalDateTime commentAt;
}
