package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.vo.UserArticleStatsVo;
import com.oyproj.api.article.domain.UserArticleStatDto;

import java.util.Map;

/**
 * 文章业务服务接口（管理/写入）
 */
public interface ArticleBizService {
    /**
     * 保存草稿
     */
    Result<String> saveDraft(ArticleSaveDto dto);

    /**
     * 发布文章
     *
     * @param dto 文章信息
     * @return 文章ID
     */
    Result<Map<String, String>> publish(ArticleSaveDto dto);

    /**
     * 删除文章
     *
     * @param id 文章ID
     * @return 是否成功
     */
    Result<Boolean> delete(String id);

    /**
     * 获取当前用户文章统计
     *
     * @return 统计信息
     */
    Result<UserArticleStatDto> getMyStats();

    /**
     * 获取指定用户文章统计
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    Result<UserArticleStatDto> getUserStats(String userId);

    /**
     * 判断是否为当前用户的文章
     *
     * @param articleId 文章ID
     * @return 是否为当前用户的文章
     */
    Result<Boolean> checkOwnership(String articleId);

}
