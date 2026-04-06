package com.oyproj.controller;

import com.oyproj.api.file.domain.dto.FileUploadDto;
import com.oyproj.api.file.domain.vo.FileVo;
import com.oyproj.common.base.Result;
import com.oyproj.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件处理接口
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;
    @PostMapping("/upload")
    @Operation(description = "OpenFeign文件上传")
    Result<FileVo> upload(@RequestBody FileUploadDto fileUploadDto){
        FileVo upload = fileService.upload(fileUploadDto);
        return Result.ok(upload);
    }
    @PostMapping("/comUpload")
    @Operation(description = "常用文件上传")
    Result<FileVo> comUpload(@RequestPart("file") MultipartFile file) throws IOException {
        FileUploadDto uploadDto = new FileUploadDto();
        uploadDto.setContent(file.getBytes());
        uploadDto.setKey("oy");
        uploadDto.setContentType(file.getContentType());
        uploadDto.setContentLength(file.getSize());
        FileVo upload = fileService.upload(uploadDto);
        return Result.ok(upload);
    }

}
