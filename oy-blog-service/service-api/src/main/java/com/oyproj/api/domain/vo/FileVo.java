package com.oyproj.api.domain.vo;

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
