package com.oyproj.api.client.fallback;

import com.oyproj.api.client.FileUploadClient;
import com.oyproj.api.domain.dto.FileUploadDto;
import com.oyproj.api.domain.vo.FileVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

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
