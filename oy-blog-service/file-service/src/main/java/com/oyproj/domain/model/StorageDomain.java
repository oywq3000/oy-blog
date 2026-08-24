package com.oyproj.domain.model;

import lombok.Data;

/**
 * @description 存储域名实体类
 */
@Data
public class StorageDomain {

    /**
     * 文件存储方式(1:MinIO、2:七牛云)
     */
    private Integer storagePlatform;

    /**
     * 上传文件目录（使用平台bucket时区别不同文件）
     */
    private String fileDir;

    /**
     * 文档预览
     */
    private String filePreviewUrl;

    /**
     * Minio
     */
    private String minioEndpoint;
    private String minioAccessKey;
    private String minioSecretKey;
    private String minioFileDomain;
    private String minioBucket;

    /**
     * 项目名称
     */
    private String qiniuOssImmProjectName;
}
