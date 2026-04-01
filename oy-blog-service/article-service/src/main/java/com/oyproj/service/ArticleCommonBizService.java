package com.oyproj.service;

import com.oyproj.api.domain.vo.FileVo;
import com.oyproj.common.base.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文章业务服务公共接口
 */
public interface ArticleCommonBizService {

    /**
     * 上传文章封面
     *
     * @param file 封面文件
     * @return 文件信息
     */
    Result<FileVo> uploadCover(MultipartFile file);

    /**
     * 上传文章内容资源（图片等）
     *
     * @param file 资源文件
     * @return 文件信息
     */
    Result<FileVo> uploadContentImage(MultipartFile file);
}
