package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.api.article.domain.vo.SeriesMemberAdminVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleSeries;
import com.oyproj.domain.entity.ArticleSeriesItem;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticleSeriesItemMapper;
import com.oyproj.mapper.ArticleSeriesMapper;
import com.oyproj.service.ArticleSeriesBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章-专栏成员关系业务服务实现
 *
 * <p>数据正确性心脏：上限 3 校验、收录幂等（业务去重 + uk_series_article 唯一键兜底）、
 * sort_order 相邻 swap、整文全量改绑、级联清理。</p>
 */
@Service
@RequiredArgsConstructor
public class ArticleSeriesBizServiceImpl extends ArticleBaseBizService implements ArticleSeriesBizService {

    private final ArticleSeriesMapper seriesMapper;
    private final ArticleSeriesItemMapper seriesItemMapper;
    private final ArticleDao articleDao;

    /**
     * 取专栏，不存在抛 NotFoundException
     */
    private ArticleSeries requireSeries(String seriesId) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new NotFoundException(I18nUtils.t("series.not_found"));
        }
        return series;
    }

    /**
     * 取某专栏现有成员行（升序）
     */
    private List<ArticleSeriesItem> itemsOfSeries(String seriesId) {
        return seriesItemMapper.selectList(new LambdaQueryWrapper<ArticleSeriesItem>()
                .eq(ArticleSeriesItem::getSeriesId, seriesId)
                .orderByAsc(ArticleSeriesItem::getSortOrder));
    }

    /**
     * 文章当前占用专栏数
     */
    private long countColumnsOfArticle(String articleId) {
        return seriesItemMapper.selectCount(new LambdaQueryWrapper<ArticleSeriesItem>()
                .eq(ArticleSeriesItem::getArticleId, articleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addSeriesArticles(String seriesId, List<String> articleIds) {
        requireSeries(seriesId);
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        List<ArticleSeriesItem> exists = itemsOfSeries(seriesId);
        Set<String> inSeries = exists.stream().map(ArticleSeriesItem::getArticleId).collect(Collectors.toSet());
        int maxSort = exists.stream().mapToInt(ArticleSeriesItem::getSortOrder).max().orElse(0);
        int added = 0;
        for (String articleId : new LinkedHashSet<>(articleIds)) {
            if (inSeries.contains(articleId)) {
                continue; // 幂等跳过重复
            }
            if (countColumnsOfArticle(articleId) >= MAX_SERIES_PER_ARTICLE) {
                throw new ValidationException(I18nUtils.t("series.limit_exceeded"));
            }
            try {
                seriesItemMapper.insert(ArticleSeriesItem.builder()
                        .id(getId())
                        .seriesId(seriesId)
                        .articleId(articleId)
                        .sortOrder(++maxSort)
                        .build());
            } catch (DuplicateKeyException e) {
                continue; // 并发下被 uk_series_article 兜底，按幂等跳过（added 不计）
            }
            added++;
        }
        return added;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeSeriesArticle(String seriesId, String articleId) {
        return seriesItemMapper.delete(new LambdaQueryWrapper<ArticleSeriesItem>()
                .eq(ArticleSeriesItem::getSeriesId, seriesId)
                .eq(ArticleSeriesItem::getArticleId, articleId)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean moveSeriesArticle(String seriesId, String articleId, String direction) {
        List<ArticleSeriesItem> items = itemsOfSeries(seriesId);
        for (int i = 0; i < items.size(); i++) {
            ArticleSeriesItem cur = items.get(i);
            if (!cur.getArticleId().equals(articleId)) {
                continue;
            }
            int target = "up".equals(direction) ? i - 1 : "down".equals(direction) ? i + 1 : -2;
            if (target < 0 || target >= items.size()) {
                return false; // 队首上移/队尾下移/非法方向：幂等 no-op
            }
            ArticleSeriesItem other = items.get(target);
            int tmp = cur.getSortOrder();
            cur.setSortOrder(other.getSortOrder());
            other.setSortOrder(tmp);
            seriesItemMapper.updateById(cur);
            seriesItemMapper.updateById(other);
            return true;
        }
        return false; // 成员不存在（可能已被移除）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceArticleSeries(String articleId, List<String> seriesIds) {
        List<String> want = seriesIds == null ? List.of() : seriesIds.stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (want.size() > MAX_SERIES_PER_ARTICLE) {
            throw new ValidationException(I18nUtils.t("series.limit_exceeded"));
        }
        for (String seriesId : want) {
            requireSeries(seriesId); // 校验全部存在
        }
        List<ArticleSeriesItem> oldItems = seriesItemMapper.selectList(
                new LambdaQueryWrapper<ArticleSeriesItem>()
                        .eq(ArticleSeriesItem::getArticleId, articleId));
        Set<String> oldSeriesIds = oldItems.stream().map(ArticleSeriesItem::getSeriesId).collect(Collectors.toSet());
        Set<String> wantSet = new HashSet<>(want);

        // 移除不在新集合里的
        List<String> toDelete = oldItems.stream()
                .map(ArticleSeriesItem::getSeriesId)
                .filter(s -> !wantSet.contains(s))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            seriesItemMapper.delete(new LambdaQueryWrapper<ArticleSeriesItem>()
                    .eq(ArticleSeriesItem::getArticleId, articleId)
                    .in(ArticleSeriesItem::getSeriesId, toDelete));
        }
        // 新集合里缺失的追加到各专栏队尾
        for (String seriesId : want) {
            if (oldSeriesIds.contains(seriesId)) {
                continue;
            }
            int maxSort = itemsOfSeries(seriesId).stream()
                    .mapToInt(ArticleSeriesItem::getSortOrder).max().orElse(0);
            seriesItemMapper.insert(ArticleSeriesItem.builder()
                    .id(getId()).seriesId(seriesId).articleId(articleId)
                    .sortOrder(maxSort + 1).build());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearByArticle(String articleId) {
        seriesItemMapper.delete(new LambdaQueryWrapper<ArticleSeriesItem>()
                .eq(ArticleSeriesItem::getArticleId, articleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearBySeries(String seriesId) {
        seriesItemMapper.delete(new LambdaQueryWrapper<ArticleSeriesItem>()
                .eq(ArticleSeriesItem::getSeriesId, seriesId));
    }

    @Override
    public List<SeriesMemberAdminVo> listSeriesMembers(String seriesId) {
        requireSeries(seriesId);
        List<ArticleSeriesItem> items = itemsOfSeries(seriesId);
        if (items.isEmpty()) {
            return List.of();
        }
        List<String> articleIds = items.stream().map(ArticleSeriesItem::getArticleId).collect(Collectors.toList());
        Map<String, Article> byId = articleDao.listByIds(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        List<SeriesMemberAdminVo> result = new ArrayList<>();
        for (ArticleSeriesItem item : items) {
            Article article = byId.get(item.getArticleId());
            if (article == null || article.getDeletedAt() != null) {
                continue; // 已不存在或已软删的文章不列
            }
            SeriesMemberAdminVo vo = new SeriesMemberAdminVo();
            vo.setArticleId(item.getArticleId());
            vo.setTitle(article.getTitle());
            vo.setStatus(article.getStatus());
            vo.setCoverUrl(article.getCoverUrl());
            vo.setSortOrder(item.getSortOrder());
            result.add(vo);
        }
        return result;
    }
}
