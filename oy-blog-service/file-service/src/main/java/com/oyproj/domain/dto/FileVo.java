package com.oyproj.domain.dto;

import lombok.Data;

/**
 * 文件视图
 */
@Data
public class FileVo {
    private String key;
    private String url;
    private String contentType;
    private Long size;
}
