package com.oyproj.service;

import com.oyproj.api.file.domain.vo.FileVo;
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
     * 上传专栏封面（创作者专栏专属端点，与文章封面语义分离）
     *
     * @param file 封面文件
     * @return 文件信息
     */
    Result<FileVo> uploadSeriesCover(MultipartFile file);

    /**
     * 上传文章内容资源（图片等）
     *
     * @param file 资源文件
     * @return 文件信息
     */
    Result<FileVo> uploadContentImage(MultipartFile file);
}
