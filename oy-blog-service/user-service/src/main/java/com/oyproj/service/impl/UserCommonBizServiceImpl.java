package com.oyproj.service.impl;

import com.oyproj.api.file.client.FileUploadClient;
import com.oyproj.api.file.domain.dto.FileUploadDto;
import com.oyproj.api.file.domain.vo.FileVo;
import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.FileUtils;
import com.oyproj.common.utils.UUIDUtils;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.entity.User;
import com.oyproj.service.UserCommonBizService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class UserCommonBizServiceImpl extends UserBizBase implements UserCommonBizService {
    private final FileUploadClient fileUploadClient;
    public UserCommonBizServiceImpl(UserDao userDao,
                                    FileUploadClient fileUploadClient,
                                    CommonCache commonCache) {
        super(userDao,commonCache);
        this.fileUploadClient =fileUploadClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> uploadAvatar(MultipartFile file) {
        String userId = getCurrentUserId();
        User user = userDao.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        try {
            // 上传头像
            // 路径：avatar/{userId}/{filename}
            String originalFilename = file.getOriginalFilename();
            String extension = FileUtils.getExtension(originalFilename);
            String filename = UUIDUtils.getId() + "." + extension;
            String path = "avatar/" + userId + "/" + filename;
            FileUploadDto uploadDto = new FileUploadDto();
            uploadDto.setContent(file.getBytes());
            uploadDto.setKey(path);
            uploadDto.setContentType(file.getContentType());
            uploadDto.setContentLength(file.getSize());
            Result<FileVo> upload = fileUploadClient.upload(uploadDto);
            // 更新用户头像
            user.setAvatarUrl(upload.getData().getUrl());
            userDao.updateById(user);
            return Result.ok(upload.getData().getUrl());
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }
}
