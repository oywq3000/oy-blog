package com.oyproj.service;

import com.oyproj.api.file.domain.vo.FileVo;
import com.oyproj.domain.dto.FileKeyDto;
import com.oyproj.api.file.domain.dto.FileUploadDto;

/**
 * 文件服务入口
 */
public interface FileService {
    /**
     * 上传文件
     *
     * @param dto 文件上传 dto
     * @return 文件 vo
     */
    FileVo upload(FileUploadDto dto);

    /**
     * 删除文件
     *
     * @param dto 文件键 dto
     * @return 是否删除成功
     */
    boolean delete(FileKeyDto dto);

    /**
     * 下载文件
     *
     * @param dto 文件键 dto
     * @return 文件字节数组
     */
    byte[] download(FileKeyDto dto);

    /**
     * 获取文件 URL
     *
     * @param dto 文件键 dto
     * @return 文件 URL
     */
    String getUrl(FileKeyDto dto);

    /**
     * 检查文件是否存在
     *
     * @param dto 文件键 dto
     * @return 是否存在
     */
    boolean exists(FileKeyDto dto);
}
