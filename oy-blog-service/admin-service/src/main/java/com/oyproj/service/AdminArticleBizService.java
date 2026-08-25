package com.oyproj.service;

import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;
import java.util.Map;

/**
 * 文章管理 BFF 业务：透传 Feign 调用 article-service
 */
public interface AdminArticleBizService {

    Result<PageVo<List<ArticleAdminItemVo>>> page(ArticleAdminPageDto dto);

    Result<String> draft(ArticleSaveDto dto);

    Result<Map<String, String>> publish(ArticleSaveDto dto);

    Result<Boolean> delete(String id);

    Result<String> saveTag(TagSaveDto dto);

    Result<Boolean> deleteTag(String id);

    Result<List<TagAdminVo>> listTags();

    Result<String> saveSeries(SeriesSaveDto dto);

    Result<Boolean> deleteSeries(String id);

    Result<List<SeriesAdminVo>> listSeries();
}
