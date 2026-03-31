package com.oyproj.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档键
 */

@Data
public class FileKeyDto {
    @NotBlank(message = "对象键不能为空")
    private String key;
}
