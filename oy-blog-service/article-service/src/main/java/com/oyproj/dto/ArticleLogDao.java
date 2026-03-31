package com.oyproj.dto;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.ArticleLog;

import java.util.List;

/**
 * @author LX
 * @date 2025/12/03
 * @description 文章阅读数据访问接口
 */
public interface ArticleLogDao extends IService<ArticleLog> {

    /**
     * 记录阅读
     *
     * @param view 阅读实体
     */
    void appendView(ArticleLog view);

    /**
     * 查询用户阅读历史文章列表
     * @param userId 用户ID
     * @return 文章ID列表
     */
    List<String> listHistoryArticleIds(String userId);
}

