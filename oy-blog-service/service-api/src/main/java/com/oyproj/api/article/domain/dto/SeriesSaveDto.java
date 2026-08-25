package com.oyproj.api.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SeriesSaveDto {
    private String id;
    @NotBlank(message = "系列名不能为空")
    private String name;
    private String description;
    private String code;
}
