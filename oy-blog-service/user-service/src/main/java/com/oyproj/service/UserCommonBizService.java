package com.oyproj.service;

import com.oyproj.api.file.domain.vo.FileVo;
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
     * @return 上传成功的文件信息（与封面等上传接口返回结构一致）
     */
    Result<FileVo> uploadAvatar(MultipartFile file);
}
