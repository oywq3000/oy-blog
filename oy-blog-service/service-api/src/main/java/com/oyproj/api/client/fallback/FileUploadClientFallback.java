package com.oyproj.api.client.fallback;

import com.oyproj.api.client.FileUploadClient;
import com.oyproj.api.domain.dto.FileUploadDto;
import com.oyproj.api.domain.vo.FileVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUploadClientFallback implements FileUploadClient {
    @Override
    public Result<FileVo> upload(FileUploadDto fileUploadDto) {
        log.warn("文件上传服务触发熔断降级，文件key: {}", fileUploadDto.getKey());
        return Result.error(ResultCode.SERVICE_UNAVAILABLE);
    }
}
