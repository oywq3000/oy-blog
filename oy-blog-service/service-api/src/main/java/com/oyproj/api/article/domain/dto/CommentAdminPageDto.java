package com.oyproj.api.article.domain.dto;

import lombok.Data;

@Data
public class CommentAdminPageDto {
    private Integer page = 1;
    private Integer size = 10;
    /** 审核状态：0=待审 1=通过 2=拒绝，null=全部 */
    private Integer status;
}
