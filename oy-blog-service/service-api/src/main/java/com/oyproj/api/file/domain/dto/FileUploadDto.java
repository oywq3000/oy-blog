package com.oyproj.api.file.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文件上传dto
 */
@Data
public class FileUploadDto {
    @NotBlank(message = "对象键不能为空")
    private String key;

    @NotBlank(message = "内容类型不能为空")
    private String contentType;

    @NotNull(message = "文件内容不能为空")
    @Size(min = 1, message = "文件内容不能为空")
    private byte[] content;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须为正数")
    private Long contentLength;
}
