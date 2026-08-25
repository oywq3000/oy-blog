package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagSaveDto {
    private String id;
    @NotBlank(message = "标签名不能为空")
    private String name;
    /** 1=常用(管理员预置) 0=自创 */
    private Integer isCommon;
}
