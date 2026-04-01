package com.oyproj.api.client;
import com.oyproj.api.domain.dto.FileUploadDto;
import com.oyproj.api.domain.vo.FileVo;
import com.oyproj.common.base.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(value = "file-service",url = "http://localhost:8090")
public interface FileUploadClient {
    /**
     * 上传文件
     * @param fileUploadDto 文件上传DTO
     * @return 上传结果
     */
    @PostMapping("/file/upload")
    Result<FileVo> upload(@RequestBody FileUploadDto fileUploadDto);
}
