package com.oyproj.service;

import com.oyproj.common.base.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * 公共服务接口
 */
public interface UserCommonBizService {
    /**
     * 上传用户头像
     *
     * @param file 头像文件
     * @return 访问URL
     */
    Result<String> uploadAvatar(MultipartFile file);
}
