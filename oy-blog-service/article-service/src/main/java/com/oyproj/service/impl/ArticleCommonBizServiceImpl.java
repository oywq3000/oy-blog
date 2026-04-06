package com.oyproj.service.impl;


import com.oyproj.api.file.client.FileUploadClient;
import com.oyproj.api.file.domain.dto.FileUploadDto;
import com.oyproj.api.file.domain.vo.FileVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.utils.FileUtils;
import com.oyproj.common.utils.UUIDUtils;
import com.oyproj.service.ArticleCommonBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


/**
 * 文章业务服务公共接口实现类
 */
@Service
@RequiredArgsConstructor
public class ArticleCommonBizServiceImpl extends ArticleBaseBizService implements ArticleCommonBizService {

    @NotNull private final FileUploadClient fileUploadClient;

    @Override
    public Result<FileVo> uploadCover(MultipartFile file) {
        return uploadFile(file, "article/cover");
    }

    @Override
    public Result<FileVo> uploadContentImage(MultipartFile file) {
        return uploadFile(file, "article/content");
    }

    private Result<FileVo> uploadFile(MultipartFile file, String folder) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = FileUtils.getExtension(originalFilename);
            String filename = UUIDUtils.getId() + "." + extension;
            
            // 按日期分目录: article/cover/2023/10/01/xxx.png
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String path = folder + "/" + datePath + "/" + filename;

            FileUploadDto uploadDto = new FileUploadDto();
            uploadDto.setContent(file.getBytes());
            uploadDto.setKey(path);
            uploadDto.setContentType(file.getContentType());
            uploadDto.setContentLength(file.getSize());
            return fileUploadClient.upload(uploadDto);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }
}
