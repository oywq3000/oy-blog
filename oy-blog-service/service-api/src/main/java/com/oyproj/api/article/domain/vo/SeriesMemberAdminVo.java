package com.oyproj.api.article.domain.vo;

import lombok.Data;

@Data
public class SeriesMemberAdminVo {
    private String articleId;
    private String title;
    private String status;      // draft/published/archived
    private String coverUrl;
    private Integer sortOrder;
}
