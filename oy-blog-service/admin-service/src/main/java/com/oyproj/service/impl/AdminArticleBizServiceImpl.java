package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminArticleClient;
import com.oyproj.api.article.domain.dto.*;
import com.oyproj.api.article.domain.vo.*;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminArticleBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文章管理 BFF 实现：全部直接透传 Feign 结果
 */
@Service
@RequiredArgsConstructor
public class AdminArticleBizServiceImpl extends AdminBizBase implements AdminArticleBizService {

    private final AdminArticleClient client;

    @Override
    public Result<PageVo<List<ArticleAdminItemVo>>> page(ArticleAdminPageDto dto) {
        return client.adminArticlePage(dto);
    }

    @Override
    public Result<String> draft(ArticleSaveDto dto) {
        return client.saveDraft(dto);
    }

    @Override
    public Result<Map<String, String>> publish(ArticleSaveDto dto) {
        return client.publish(dto);
    }

    @Override
    public Result<Boolean> delete(String id) {
        return client.deleteArticle(id);
    }

    @Override
    public Result<String> saveTag(TagSaveDto dto) {
        return client.saveTag(dto);
    }

    @Override
    public Result<Boolean> deleteTag(String id) {
        return client.deleteTag(id);
    }

    @Override
    public Result<List<TagAdminVo>> listTags() {
        return client.listTags();
    }

    @Override
    public Result<String> saveSeries(SeriesSaveDto dto) {
        return client.saveSeries(dto);
    }

    @Override
    public Result<Boolean> deleteSeries(String id) {
        return client.deleteSeries(id);
    }

    @Override
    public Result<List<SeriesAdminVo>> listSeries() {
        return client.listSeries();
    }
}
