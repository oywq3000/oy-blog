package com.oyproj.api.article.domain.dto;

import lombok.Data;

@Data
public class ArticleAdminPageDto {
    private Integer page = 1;
    private Integer size = 10;
    /** 状态：draft/published/archived，null=全部 */
    private String status;
    /** 标题/摘要模糊搜索，null=不限 */
    private String keyword;
}
