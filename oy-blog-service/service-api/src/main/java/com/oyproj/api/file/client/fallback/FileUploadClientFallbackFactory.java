package com.oyproj.api.file.client.fallback;

import com.oyproj.api.file.client.FileUploadClient;
import com.oyproj.api.file.domain.dto.FileUploadDto;
import com.oyproj.api.file.domain.vo.FileVo;
import com.oyproj.common.base.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileUploadClientFallbackFactory implements FallbackFactory<FileUploadClient> {
    @Override
    public FileUploadClient create(Throwable cause) {
        log.warn("FileUploadClient 触发熔断，异常信息: {}", cause.getMessage());
        return new FileUploadClient() {
            @Override
            public Result<FileVo> upload(FileUploadDto fileUploadDto) {
                return Result.ok(new FileVo());
            }
        };
    }

}
