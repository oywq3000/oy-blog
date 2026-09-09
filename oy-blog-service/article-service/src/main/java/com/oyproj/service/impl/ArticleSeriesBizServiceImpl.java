package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.vo.SeriesMemberAdminVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleSeries;
import com.oyproj.domain.entity.ArticleSeriesItem;
import com.oyproj.domain.vo.SeriesAddResultVo;
import com.oyproj.domain.vo.SeriesMemberCountVo;
import com.oyproj.domain.vo.SeriesReadVo;
import com.oyproj.domain.vo.SeriesSkipVo;
import com.oyproj.dto.ArticleDao;
import com.oyproj.mapper.ArticleMapper;
import com.oyproj.mapper.ArticleSeriesItemMapper;
import com.oyproj.mapper.ArticleSeriesMapper;
import com.oyproj.service.ArticleSeriesBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final ArticleMapper articleMapper;

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
     * 非 ADMIN 操作的 owner 校验：专栏非本人创建（authorId 缺失=站长级专栏，或归属他人）一律视同越权。
     */
    private void requireSeriesOwner(ArticleSeries series, String operatorId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (series.getAuthorId() == null || !series.getAuthorId().equals(operatorId)) {
            throw new ForbiddenException(I18nUtils.t("series.forbidden"));
        }
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
    public void replaceArticleSeries(String articleId, List<String> seriesIds, String operatorId) {
        List<String> want = seriesIds == null ? List.of() : seriesIds.stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (want.size() > MAX_SERIES_PER_ARTICLE) {
            throw new ValidationException(I18nUtils.t("series.limit_exceeded"));
        }
        for (String seriesId : want) {
            ArticleSeries series = requireSeries(seriesId); // 校验全部存在
            if (operatorId != null) {
                // 创作端 publish 链归属校验：只可绑定自己的专栏；站长级（authorId null）专栏仅 ADMIN 可绑
                requireSeriesOwner(series, operatorId, false);
            }
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

    // ================================================================
    //  创作端专栏 CRUD（spec §九）：仅本人专栏；authorId null=站长级专栏对非 ADMIN 越权
    // ================================================================

    @Override
    public List<SeriesReadVo> listOwnSeries(String userId) {
        if (userId == null) {
            return List.of();
        }
        List<ArticleSeries> mySeries = seriesMapper.selectList(
                new LambdaQueryWrapper<ArticleSeries>()
                        .eq(ArticleSeries::getAuthorId, userId)
                        .orderByAsc(ArticleSeries::getCreatedAt));
        if (mySeries.isEmpty()) {
            return List.of();
        }
        // 已发布公开成员数：全局分组统计后按我的专栏 id 交集（复用前台专栏列表同款口径）
        Map<String, Long> countMap = articleMapper.selectPublishedCountGroupBySeries().stream()
                .collect(Collectors.toMap(SeriesMemberCountVo::getSeriesId,
                        vo -> vo.getArticleCount() == null ? 0L : vo.getArticleCount()));
        List<SeriesReadVo> vos = new ArrayList<>(mySeries.size());
        for (ArticleSeries s : mySeries) {
            SeriesReadVo vo = copyProperties(s, SeriesReadVo.class);
            vo.setArticleCount(countMap.getOrDefault(s.getId(), 0L));
            vos.add(vo);
        }
        return vos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSeries(SeriesSaveDto dto, String userId) {
        if (userId == null) {
            // 缺会话身份（网关正常流不可能；防直连/内部误用创建出 author_id 为 null 的"伪站长级"专栏）
            throw new ForbiddenException();
        }
        if (!StringUtils.hasText(dto.getName())) {
            // 双层防御：HTTP 层 @Valid 已挡 400；直连 biz 调用兜底（DTO 注解不拦截非 MVC 调用）
            throw new ValidationException(I18nUtils.t("series.name_required"));
        }
        ArticleSeries series = copyProperties(dto, ArticleSeries.class);
        series.setId(getId());
        series.setAuthorId(userId); // 归属当前用户；id/author_id 不信任入参
        series.setCode(null);       // code 仅站长级预留：创作端新建不落 code（读写语义闭环）
        seriesMapper.insert(series);
        return series.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeries(String id, SeriesSaveDto dto, String operatorId, boolean isAdmin) {
        ArticleSeries series = requireSeries(id);
        requireSeriesOwner(series, operatorId, isAdmin);
        // 只允许改 名称/描述/封面（code 保留）；description/coverUrl 传 "" 即清空
        series.setName(dto.getName());
        series.setDescription(dto.getDescription());
        series.setCoverUrl(dto.getCoverUrl());
        seriesMapper.updateById(series);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnSeries(String id, String operatorId, boolean isAdmin) {
        ArticleSeries series = requireSeries(id);
        requireSeriesOwner(series, operatorId, isAdmin);
        clearBySeries(id); // 级联清成员（同一事务）
        seriesMapper.deleteById(id);
    }

    @Override
    public List<SeriesMemberAdminVo> listSeriesMembers(String seriesId) {
        requireSeries(seriesId);
        return buildMemberVos(seriesId);
    }

    /**
     * 成员 VO 列表主体（不做存在性/owner 校验，管理端与 creator 编辑页共用）。
     * 已不存在或已软删的文章不列入；按 sort_order 升序（itemsOfSeries 已排序）。
     */
    private List<SeriesMemberAdminVo> buildMemberVos(String seriesId) {
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

    // ================================================================
    //  creator 编辑页成员管理（spec §十）：owner 专属语义，内部复用上方既有逻辑
    //  既有管理端方法签名与行为不动；四个入口一律 requireSeriesOwner(..., isAdmin=false)
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeriesAddResultVo addOwnArticlesToSeries(String seriesId, String operatorId, List<String> articleIds) {
        ArticleSeries series = requireSeries(seriesId);
        requireSeriesOwner(series, operatorId, false); // 非本人（含站长级）整单 403，零写入
        if (articleIds == null || articleIds.isEmpty()) {
            return emptyAddResult();
        }
        List<ArticleSeriesItem> exists = itemsOfSeries(seriesId);
        Set<String> inSeries = exists.stream().map(ArticleSeriesItem::getArticleId).collect(Collectors.toSet());
        int maxSort = exists.stream().mapToInt(ArticleSeriesItem::getSortOrder).max().orElse(0);
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(articleIds)); // 去重保序
        Map<String, Article> byId = articleDao.listByIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        int added = 0;
        List<SeriesSkipVo> skipped = new ArrayList<>();
        for (String articleId : ids) {
            if (inSeries.contains(articleId)) {
                continue; // 已在目标栏：幂等静默跳过（不计 added 不进 skipped）
            }
            Article article = byId.get(articleId);
            if (article == null || article.getDeletedAt() != null) {
                skipped.add(skip(articleId, SKIP_REASON_NOT_FOUND)); // 不存在或已软删
                continue;
            }
            if (!Objects.equals(article.getAuthorId(), operatorId)) {
                skipped.add(skip(articleId, SKIP_REASON_NOT_OWNER));
                continue;
            }
            if (!"published".equals(article.getStatus())) {
                skipped.add(skip(articleId, SKIP_REASON_NOT_PUBLISHED));
                continue;
            }
            if (countColumnsOfArticle(articleId) >= MAX_SERIES_PER_ARTICLE) {
                // 占用计数按 articleId 维；含目标栏的重复行已在 inSeries 分支滤掉，不会误判
                skipped.add(skip(articleId, SKIP_REASON_LIMIT3));
                continue;
            }
            try {
                seriesItemMapper.insert(ArticleSeriesItem.builder()
                        .id(getId())
                        .seriesId(seriesId)
                        .articleId(articleId)
                        .sortOrder(++maxSort) // 追加队尾
                        .build());
            } catch (DuplicateKeyException e) {
                continue; // 并发下被 uk_series_article 兜底：幂等静默跳过
            }
            inSeries.add(articleId); // 本批内同 id 防重（入参已去重，双保险）
            added++;
        }
        SeriesAddResultVo result = new SeriesAddResultVo();
        result.setAddedCount(added);
        result.setSkipped(skipped);
        return result;
    }

    @Override
    public List<SeriesMemberAdminVo> listOwnSeriesMembers(String seriesId, String operatorId) {
        ArticleSeries series = requireSeries(seriesId);
        requireSeriesOwner(series, operatorId, false);
        return buildMemberVos(seriesId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean moveOwnSeriesArticle(String seriesId, String articleId, String direction, String operatorId) {
        ArticleSeries series = requireSeries(seriesId);
        requireSeriesOwner(series, operatorId, false);
        return moveSeriesArticle(seriesId, articleId, direction);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeOwnSeriesArticle(String seriesId, String articleId, String operatorId) {
        ArticleSeries series = requireSeries(seriesId);
        requireSeriesOwner(series, operatorId, false);
        return removeSeriesArticle(seriesId, articleId);
    }

    /** 空入参的宽容收录结果（skipped 非 null，JSON 序列化为 []） */
    private SeriesAddResultVo emptyAddResult() {
        SeriesAddResultVo result = new SeriesAddResultVo();
        result.setAddedCount(0);
        result.setSkipped(List.of());
        return result;
    }

    private SeriesSkipVo skip(String articleId, String reasonCode) {
        SeriesSkipVo vo = new SeriesSkipVo();
        vo.setArticleId(articleId);
        vo.setReasonCode(reasonCode);
        return vo;
    }
}
