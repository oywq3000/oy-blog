package com.oyproj.service;

import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.api.article.domain.vo.ArticleAdminItemVo;
import com.oyproj.api.article.domain.vo.SeriesAdminVo;
import com.oyproj.api.article.domain.vo.TagAdminVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 文章管理后台业务
 */
public interface ArticleAdminBizService {

    /** 管理视角文章分页列表（全状态筛选，不含软删除） */
    Result<PageVo<List<ArticleAdminItemVo>>> adminPage(ArticleAdminPageDto dto);

    /** 新建/更新标签（id 空=新建） */
    Result<String> saveTag(TagSaveDto dto);

    /** 删除标签 */
    Result<Boolean> deleteTag(String id);

    /** 标签全量列表 */
    Result<List<TagAdminVo>> listTags();

    /** 新建/更新系列 */
    Result<String> saveSeries(SeriesSaveDto dto);

    /** 删除系列 */
    Result<Boolean> deleteSeries(String id);

    /** 系列全量列表 */
    Result<List<SeriesAdminVo>> listSeries();
}
