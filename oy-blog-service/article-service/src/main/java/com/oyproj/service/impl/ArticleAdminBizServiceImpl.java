package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.article.domain.dto.ArticleAdminPageDto;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.dto.TagSaveDto;
import com.oyproj.api.article.domain.vo.ArticleAdminItemVo;
import com.oyproj.api.article.domain.vo.SeriesAdminVo;
import com.oyproj.api.article.domain.vo.TagAdminVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleSeries;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.entity.Tag;
import com.oyproj.mapper.ArticleMapper;
import com.oyproj.mapper.ArticleSeriesMapper;
import com.oyproj.mapper.ArticleStatsMapper;
import com.oyproj.mapper.TagMapper;
import com.oyproj.service.ArticleAdminBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章管理后台业务实现（只做管理视角查询与标签/系列维护，写文章仍走 ArticleBizService）
 */
@Service
@RequiredArgsConstructor
public class ArticleAdminBizServiceImpl extends ArticleBaseBizService implements ArticleAdminBizService {

    private final ArticleMapper articleMapper;
    private final ArticleStatsMapper articleStatsMapper;
    private final TagMapper tagMapper;
    private final ArticleSeriesMapper seriesMapper;

    @Override
    public Result<PageVo<List<ArticleAdminItemVo>>> adminPage(ArticleAdminPageDto dto) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(Article::getDeletedAt)
                .eq(StringUtils.hasText(dto.getStatus()), Article::getStatus, dto.getStatus())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(Article::getTitle, dto.getKeyword())
                        .or()
                        .like(Article::getSummary, dto.getKeyword()))
                .orderByDesc(Article::getUpdateAt);
        Page<Article> page = articleMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), wrapper);

        // 批量补统计（浏览/点赞/评论）
        List<String> ids = page.getRecords().stream().map(Article::getId).toList();
        Map<String, ArticleStats> statsMap = ids.isEmpty() ? Collections.emptyMap()
                : articleStatsMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(ArticleStats::getArticleId, Function.identity()));

        List<ArticleAdminItemVo> items = page.getRecords().stream().map(a -> {
            ArticleAdminItemVo vo = copyProperties(a, ArticleAdminItemVo.class);
            ArticleStats s = statsMap.get(a.getId());
            if (s != null) {
                vo.setViews(s.getViews());
                vo.setLikes(s.getLikes());
                vo.setComments(s.getComments());
            }
            return vo;
        }).toList();

        PageVo<List<ArticleAdminItemVo>> resultPage = new PageVo<>(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), (int) page.getPages(), items);
        return Result.ok(resultPage);
    }

    @Override
    public Result<String> saveTag(TagSaveDto dto) {
        Tag tag = copyProperties(dto, Tag.class);
        if (!StringUtils.hasText(tag.getId())) {
            tag.setId(getId());
            tagMapper.insert(tag);
        } else {
            tagMapper.updateById(tag);
        }
        // 注意：Result.ok(String) 会被解析为 ok(String errMsg)（data 为 null），
        // 返回 id 作 data 必须走二参 ok(data, errMsg)，errMsg 用标准成功消息
        return Result.ok(tag.getId(), I18n(ResultCode.SUCCESS));
    }

    @Override
    public Result<Boolean> deleteTag(String id) {
        boolean ok = tagMapper.deleteById(id) > 0;
        return ok ? Result.ok(true) : Result.error(false);
    }

    @Override
    public Result<List<TagAdminVo>> listTags() {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getCreatedAt));
        return Result.ok(copyList(tags, TagAdminVo.class));
    }

    @Override
    public Result<String> saveSeries(SeriesSaveDto dto) {
        ArticleSeries series = copyProperties(dto, ArticleSeries.class);
        if (!StringUtils.hasText(series.getId())) {
            series.setId(getId());
            seriesMapper.insert(series);
        } else {
            seriesMapper.updateById(series);
        }
        // 同 saveTag：Result.ok(String) 会被解析为 ok(String errMsg)，必须走二参 ok
        return Result.ok(series.getId(), I18n(ResultCode.SUCCESS));
    }

    @Override
    public Result<Boolean> deleteSeries(String id) {
        boolean ok = seriesMapper.deleteById(id) > 0;
        return ok ? Result.ok(true) : Result.error(false);
    }

    @Override
    public Result<List<SeriesAdminVo>> listSeries() {
        List<ArticleSeries> series = seriesMapper.selectList(
                new LambdaQueryWrapper<ArticleSeries>().orderByAsc(ArticleSeries::getCreatedAt));
        return Result.ok(copyList(series, SeriesAdminVo.class));
    }
}
