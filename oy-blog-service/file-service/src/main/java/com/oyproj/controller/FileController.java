package com.oyproj.controller;

import com.oyproj.api.domain.dto.FileUploadDto;
import com.oyproj.api.domain.vo.FileVo;
import com.oyproj.common.base.Result;
import com.oyproj.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件处理接口
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;
    @PostMapping("/upload")
    @Operation(description = "文件上传")
    Result<FileVo> upload(FileUploadDto fileUploadDto){
        FileVo upload = fileService.upload(fileUploadDto);
        return Result.ok(upload);
    }
}
