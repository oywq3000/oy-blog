package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.user.client.UserClient;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.RecommendKeys;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.config.HotRankProperties;
import com.oyproj.config.HotWeightProperties;
import com.oyproj.config.RecommendProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleLog;
import com.oyproj.domain.entity.ArticleSeries;
import com.oyproj.domain.entity.ArticleSeriesItem;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.vo.ArticleChapterVo;
import com.oyproj.domain.vo.ArticleContentVo;
import com.oyproj.domain.vo.ArticleInfoVo;
import com.oyproj.domain.vo.PageDomain;
import com.oyproj.domain.vo.SeriesArticleLinkVo;
import com.oyproj.domain.vo.SeriesDetailVo;
import com.oyproj.domain.vo.SeriesMemberCountVo;
import com.oyproj.domain.vo.SeriesReadVo;
import com.oyproj.domain.vo.TableSupport;
import com.oyproj.domain.vo.TagStatVo;
import com.oyproj.dto.*;
import com.oyproj.mapper.ArticleMapper;
import com.oyproj.mapper.ArticleSeriesItemMapper;
import com.oyproj.mapper.ArticleSeriesMapper;
import com.oyproj.service.ArticleReadBizService;
import com.oyproj.service.HotRankService;
import com.oyproj.service.RecommendationBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章阅读查询业务服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleReadBizServiceImpl extends ArticleBaseBizService implements ArticleReadBizService {

    /** 首页随机专栏推荐上限 */
    private static final int RANDOM_SERIES_LIMIT = 8;

    @NotNull private final ArticleDao articleDao;
    @NotNull private final ArticleContentDao contentDao;
    @NotNull private final ArticleChapterDao chapterDao;
    @NotNull private final ArticleLogDao viewDao;
    @NotNull private final TagDao tagDao;
    @NotNull private final ArticleTagDao articleTagDao;
    @NotNull private final ArticleStatsDao articleStatsDao;
    @NotNull private final UserClient userClient;
    @NotNull private final HotWeightProperties hotWeightProperties;
    private final ArticleMapper articleMapper;
    private final ArticleSeriesMapper seriesMapper;
    private final ArticleSeriesItemMapper seriesItemMapper;
    @NotNull private final HotRankService hotRankService;
    @NotNull private final HotRankProperties hotRankProperties;
    @NotNull private final CommonCache<Object> commonCache;
    @NotNull private final RecommendationBizService recommendationBizService;
    @NotNull private final RecommendProperties recommendProperties;

    /**
     * 根据slug查询文章
     *
     * @param slug SEO别名
     * @return 文章
     */
    @Override
    public Result<ArticleInfoVo> getBySlug(String slug) {
        Article article = articleDao.getBySlug(slug);
        if (!canView(article)) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        ArticleInfoVo vo = copyProperties(article, ArticleInfoVo.class);
        enrichWithAuthorInfo(Collections.singletonList(vo));
        enrichWithStats(Collections.singletonList(vo));
        enrichWithTags(Collections.singletonList(vo));
        enrichWithSeries(Collections.singletonList(vo));
        return Result.ok(vo);
    }

    /**
     * 查询文章内容
     *
     * @param articleId 文章ID
     * @return 文章内容
     */
    @Override
    public Result<ArticleContentVo> getContent(String articleId) {
        if (!canView(articleDao.getById(articleId))) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        return Result.ok(copyProperties(contentDao.getById(articleId), ArticleContentVo.class));
    }

    /**
     * 查询文章章节目录
     *
     * @param articleId 文章ID
     * @return 章节列表
     */
    @Override
    public Result<List<ArticleChapterVo>> listChapters(String articleId) {
        if (!canView(articleDao.getById(articleId))) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        return Result.ok(copyList(chapterDao.listByArticle(articleId), ArticleChapterVo.class));
    }

    /**
     * 按发布时间分页查询已发布文章列表（置顶优先 + publishAt/createdAt/id 降序）
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 分页的文章列表
     */
    @Override
    public Result<PageVo<List<ArticleInfoVo>>> listPublished(int pageNum, int pageSize) {
        int[] p = normalizePage(pageNum, pageSize);
        long total = articleDao.countPublished();
        List<ArticleInfoVo> voList = total == 0
                ? Collections.emptyList()
                : copyList(articleDao.listPublishedByTime(p[0], p[1]), ArticleInfoVo.class);
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        enrichWithTags(voList);
        return Result.ok(buildPageVo(p[0], p[1], total, voList));
    }

    /**
     * 按作者分页查询已发布文章列表（置顶优先 + publishAt/createdAt/id 降序）
     *
     * @param authorId 作者ID
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 分页的文章列表
     */
    @Override
    public Result<PageVo<List<ArticleInfoVo>>> listPublishedByAuthor(String authorId, int pageNum, int pageSize) {
        int[] p = normalizePage(pageNum, pageSize);
        long total = articleDao.countPublishedByAuthor(authorId);
        List<ArticleInfoVo> voList = total == 0
                ? Collections.emptyList()
                : copyList(articleDao.listPublishedByAuthor(authorId, p[0], p[1]), ArticleInfoVo.class);
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        enrichWithTags(voList);
        return Result.ok(buildPageVo(p[0], p[1], total, voList));
    }

    /**
     * 按热度分页查询已发布文章（优先读 Redis 近 7 天榜，读不到回退 MySQL 全时段总榜）。
     */
    @Override
    public Result<PageVo<List<ArticleInfoVo>>> listPublishedByHot(int pageNum, int pageSize, String period) {
        return listPublishedByRank(HotRankServiceImpl.rankKeyForPeriod(period), pageNum, pageSize);
    }

    /**
     * 按趋势分页查询已发布文章（Redis 趋势榜，读不到回退 MySQL 全时段总榜）。
     */
    @Override
    public Result<PageVo<List<ArticleInfoVo>>> listPublishedByTrend(int pageNum, int pageSize) {
        return listPublishedByRank(HotRankServiceImpl.KEY_TREND, pageNum, pageSize);
    }

    /**
     * 猜你喜欢（个性化推荐）：按用户/游客标签画像推荐相似文章。
     * 结果缓存命中直接复用 id 列表；画像为空（冷启动）或无身份时回退热榜链路。
     */
    @Override
    public Result<PageVo<List<ArticleInfoVo>>> recommend(int pageNum, int pageSize) {
        int[] p = normalizePage(pageNum, pageSize);
        String actorId = getUserId();
        if (actorId == null) {
            return listPublishedByHot(p[0], p[1], "7d");   // 理论不出现：网关恒注入身份头
        }
        boolean guest = actorId.startsWith(CachePrefix.GUEST_ID.getPrefix());
        String cacheKey = guest ? RecommendKeys.resultGuest(actorId) : RecommendKeys.resultUser(actorId);
        String cached = commonCache.getString(cacheKey);
        if (cached != null && !cached.isBlank()) {
            List<String> ids = Arrays.stream(cached.split(","))
                    .filter(s -> !s.isBlank()).toList();
            return Result.ok(buildRankedPage(ids, p[0], p[1]));
        }
        List<String> ids = recommendationBizService.recommendArticleIds(actorId, guest);
        if (ids.isEmpty()) {
            return listPublishedByHot(p[0], p[1], "7d");   // 冷启动回退热榜链路
        }
        commonCache.put(cacheKey, String.join(",", ids), recommendProperties.getResultCacheTtlSeconds());
        return Result.ok(buildRankedPage(ids, p[0], p[1]));
    }

    /**
     * 榜单读取通用路径：Redis 有数据且请求页在窗口内就走 Redis，否则回退 MySQL。
     */
    private Result<PageVo<List<ArticleInfoVo>>> listPublishedByRank(String rankKey, int pageNum, int pageSize) {
        int[] p = normalizePage(pageNum, pageSize);
        PageVo<List<ArticleInfoVo>> fromRedis = buildRankPageFromRedis(rankKey, p[0], p[1]);
        if (fromRedis != null) {
            return Result.ok(fromRedis);
        }
        return listPublishedByHotFromDb(p[0], p[1]);
    }

    /**
     * 从 Redis 榜组装分页结果。
     *
     * @return null 表示"应该回退 MySQL"（榜为空 / 请求页超出窗口 / 该页无有效文章 / Redis 异常）
     */
    private PageVo<List<ArticleInfoVo>> buildRankPageFromRedis(String rankKey, int pageNum, int pageSize) {
        int window = hotRankProperties.getWindowSize();
        // 这里必须用 long 相乘：normalizePage 只兜 pageNum 的下界，公开端点可传 1073741825，
        // int 乘法翻负后会骗过窗口判断，把负 offset 带进下面的 subList（500）。
        if (window <= 0 || (long) pageNum * pageSize > window) {
            return null;   // 请求页超出 Redis 榜窗口，交回 MySQL
        }
        List<String> rankedIds = hotRankService.topArticleIds(rankKey, window);
        if (rankedIds.isEmpty()) {
            return null;   // 冷水启动尚无榜数据，或 Redis 不可用
        }
        return buildRankedPage(rankedIds, pageNum, pageSize);
    }

    /**
     * 排序 id 列表 → 分页 VO（按榜序装载/过滤/分页/组装）。
     * <p>热榜链路（buildRankPageFromRedis）与个性化推荐（recommend）复用同一段排序页组装：
     * 过滤已发布未软删 → 按榜序重排 → subList 分页 → enrich → PageVo。</p>
     *
     * @param rankedIds 已排好序的文章 id 列表（调用方负责排序语义）
     * @return null 表示"该页无有效文章"（返回空列表由调用方决定降级）
     */
    private PageVo<List<ArticleInfoVo>> buildRankedPage(List<String> rankedIds, int pageNum, int pageSize) {
        Map<String, Article> byId = articleDao.listByIds(rankedIds).stream()
                .filter(a -> "published".equals(a.getStatus()) && a.getDeletedAt() == null)
                .collect(Collectors.toMap(Article::getId, Function.identity(), (a, b) -> a));

        // listByIds 不保证顺序，且排序列表里可能残留已下架/删除的文章 —— 按榜序重排并剔除
        List<Article> ordered = rankedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int from = (pageNum - 1) * pageSize;
        if (from >= ordered.size()) {
            return null;
        }
        int to = Math.min(from + pageSize, ordered.size());

        List<ArticleInfoVo> voList = copyList(ordered.subList(from, to), ArticleInfoVo.class);
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        enrichWithTags(voList);
        return buildPageVo(pageNum, pageSize, (long) ordered.size(), voList);
    }

    /**
     * 按热度分页查询已发布文章列表（加权评分降序）
     *
     * <p>MySQL 全时段加权总榜：Redis 榜不可用（冷启动 / 挂了 / 请求页超出窗口）时的降级路径，
     * 逻辑与引入 Redis 前完全一致。不对外暴露（接口只声明 Redis 优先的 listPublishedByHot），
     * 故不再带 @Override。</p>
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页大小
     * @return 分页的文章列表
     */
    public Result<PageVo<List<ArticleInfoVo>>> listPublishedByHotFromDb(int pageNum, int pageSize) {
        int[] p = normalizePage(pageNum, pageSize);
        long total = articleDao.countPublished();
        List<ArticleInfoVo> voList = total == 0
                ? Collections.emptyList()
                : copyList(articleDao.listPublishedByHot(p[0], p[1],
                        hotWeightProperties.getViews(), hotWeightProperties.getLikes(),
                        hotWeightProperties.getComments(), hotWeightProperties.getFavorites()),
                        ArticleInfoVo.class);
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        enrichWithTags(voList);
        return Result.ok(buildPageVo(p[0], p[1], total, voList));
    }

    /**
     * 归一化分页参数：pageNum 最小 1，pageSize 限制在 1~100
     *
     * @return {pageNum, pageSize}
     */
    private int[] normalizePage(int pageNum, int pageSize) {
        int p = Math.max(pageNum, 1);
        int s = Math.min(Math.max(pageSize, 1), 100);
        return new int[]{p, s};
    }

    /**
     * 组装分页 VO（totalPages = ceil(total / pageSize)）
     */
    private PageVo<List<ArticleInfoVo>> buildPageVo(int pageNum, int pageSize, long total, List<ArticleInfoVo> data) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageVo<>(pageNum, pageSize, total, totalPages, data);
    }

    /**
     * 查询用户浏览历史
     *
     * @return 文章列表
     */
    @Override
    public Result<List<ArticleInfoVo>> listHistory() {
        PageDomain pd = TableSupport.getPageDomain();
        List<ArticleLog> logs = viewDao.listHistoryLogs(getUserId(), new Page<>(pd.getPageNum(), pd.getPageSize()));
        if (logs.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 按浏览时间倒序去重，并保留每条记录对应的浏览时间
        List<String> articleIds = new ArrayList<>();
        Map<String, java.time.LocalDateTime> viewedAtMap = new HashMap<>();
        for (ArticleLog log : logs) {
            String articleId = log.getArticleId();
            if (articleId == null || viewedAtMap.containsKey(articleId)) {
                continue;
            }
            articleIds.add(articleId);
            viewedAtMap.put(articleId, log.getViewAt());
        }
        List<Article> articles = articleDao.listByIds(articleIds);
        List<Article> sortedArticles = new ArrayList<>();
        for (String id : articleIds) {
            articles.stream().filter(a -> a.getId().equals(id)).findFirst().ifPresent(sortedArticles::add);
        }
        List<ArticleInfoVo> voList = copyList(sortedArticles, ArticleInfoVo.class);
        for (ArticleInfoVo vo : voList) {
            vo.setViewedAt(viewedAtMap.get(vo.getId()));
        }
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        enrichWithTags(voList);
        return Result.ok(voList);
    }

    /**
     * 为文章VO列表批量注入统计数据
     *
     * @param voList 文章VO列表
     */
    private void enrichWithStats(List<ArticleInfoVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<String> articleIds = voList.stream()
                .map(ArticleInfoVo::getId)
                .collect(Collectors.toList());
        List<ArticleStats> statsList = articleStatsDao.listByArticleIds(articleIds);
        Map<String, ArticleStats> statsMap = statsList.stream()
                .collect(Collectors.toMap(ArticleStats::getArticleId, Function.identity()));
        for (ArticleInfoVo vo : voList) {
            ArticleStats stats = statsMap.get(vo.getId());
            if (stats != null) {
                vo.setViewCount(stats.getViews());
                vo.setLikeCount(stats.getLikes());
                vo.setCommentCount(stats.getComments());
                vo.setFavorites(stats.getFavorites());
            }
        }
    }

    /**
     * 为文章VO列表批量注入标签名
     *
     * @param voList 文章VO列表
     */
    private void enrichWithTags(List<ArticleInfoVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<String> articleIds = voList.stream()
                .map(ArticleInfoVo::getId)
                .collect(Collectors.toList());
        Map<String, List<String>> tagMap = articleTagDao.listTagNamesByArticleIds(articleIds);
        for (ArticleInfoVo vo : voList) {
            vo.setTags(tagMap.getOrDefault(vo.getId(), Collections.emptyList()));
        }
    }

    /**
     * 为文章VO列表批量注入作者信息（名称和头像）
     *
     * @param voList 文章VO列表
     */
    private void enrichWithAuthorInfo(List<ArticleInfoVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<String> authorIds = voList.stream()
                .map(ArticleInfoVo::getAuthorId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, UserDTO> userMap = new HashMap<>();
        try {
            Result<List<UserDTO>> result = userClient.getUserDTOs(authorIds);
            if (result != null && result.getIsSuccess() && result.getData() != null) {
                result.getData().forEach(dto -> userMap.put(dto.getId(), dto));
            }
        } catch (Exception e) {
            log.warn("批量获取作者信息失败, authorIds: {}", authorIds, e);
        }
        for (ArticleInfoVo vo : voList) {
            UserDTO user = userMap.get(vo.getAuthorId());
            if (user != null) {
                vo.setAuthorName(user.getUsername());
                vo.setAuthorAvatar(user.getAvatarUrl());
            }
        }
    }

    /**
     * 为文章VO列表批量注入所属专栏链接（含专栏名称/封面、文内排序与各栏已发布总数）。
     * 无专栏关联时保持 seriesList 为 null，前端判空隐藏。
     *
     * @param voList 文章VO列表（详情接口用，通常单元素）
     */
    private void enrichWithSeries(List<ArticleInfoVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<String> articleIds = voList.stream()
                .map(ArticleInfoVo::getId)
                .collect(Collectors.toList());
        if (articleIds.isEmpty()) {
            return;
        }
        List<ArticleSeriesItem> items = seriesItemMapper.selectList(
                new LambdaQueryWrapper<ArticleSeriesItem>()
                        .in(ArticleSeriesItem::getArticleId, articleIds)
                        .orderByAsc(ArticleSeriesItem::getSortOrder));
        if (items.isEmpty()) {
            return;
        }
        Set<String> involvedSeriesIds = items.stream()
                .map(ArticleSeriesItem::getSeriesId)
                .collect(Collectors.toSet());
        Map<String, ArticleSeries> seriesMap = seriesMapper.selectBatchIds(involvedSeriesIds).stream()
                .collect(Collectors.toMap(ArticleSeries::getId, s -> s));
        Map<String, Long> countMap = articleMapper.selectPublishedCountGroupBySeries().stream()
                .collect(Collectors.toMap(SeriesMemberCountVo::getSeriesId,
                        vo -> vo.getArticleCount() == null ? 0L : vo.getArticleCount()));
        Map<String, List<ArticleSeriesItem>> byArticle = items.stream()
                .collect(Collectors.groupingBy(ArticleSeriesItem::getArticleId));
        for (ArticleInfoVo vo : voList) {
            List<ArticleSeriesItem> mine = byArticle.get(vo.getId());
            if (mine == null) {
                continue;
            }
            List<SeriesArticleLinkVo> links = new ArrayList<>();
            for (ArticleSeriesItem item : mine) {
                ArticleSeries s = seriesMap.get(item.getSeriesId());
                if (s == null) {
                    continue; // 专栏已删、关系行残留则跳过
                }
                SeriesArticleLinkVo link = new SeriesArticleLinkVo();
                link.setSeriesId(s.getId());
                link.setName(s.getName());
                link.setCoverUrl(s.getCoverUrl());
                link.setSortOrder(item.getSortOrder());
                link.setTotalCount(countMap.getOrDefault(s.getId(), 0L));
                links.add(link);
            }
            links.sort(Comparator.comparing(SeriesArticleLinkVo::getSeriesId)); // 稳定展示序
            vo.setSeriesList(links.isEmpty() ? null : links);
        }
    }

    /**
     * 查询常用标签及文章数统计
     *
     * @return 常用标签统计列表（按文章数降序，仅统计已发布且未软删的文章）
     */
    @Override
    public Result<List<TagStatVo>> listPopularTags() {
        return Result.ok(tagDao.listCommonTagStats());
    }

     /**
      * 根据文章Id查询文章基础信息
      *
      * @param articleId 文章ID
      * @return 文章信息
      */
    @Override
    public Result<ArticleInfoVo> getById(String articleId) {
        Article article = articleDao.getById(articleId);
        if (!canView(article)) {
            throw new NotFoundException(I18nUtils.t("article.not_found"));
        }
        ArticleInfoVo vo = copyProperties(article, ArticleInfoVo.class);
        enrichWithAuthorInfo(Collections.singletonList(vo));
        enrichWithStats(Collections.singletonList(vo));
        enrichWithTags(Collections.singletonList(vo));
        enrichWithSeries(Collections.singletonList(vo));
        return Result.ok(vo);
    }

    /**
     * 公开可见性：已发布人人可见；非已发布仅作者本人可见（供创作中心读取草稿/待审/驳回）。
     * 不可见一律 NotFound，不泄露文章存在性。
     */
    private boolean canView(Article article) {
        if (article == null || article.getDeletedAt() != null) {
            return false;
        }
        if ("published".equals(article.getStatus())) {
            return true;
        }
        String userId = getUserId();
        return userId != null && userId.equals(article.getAuthorId());
    }

    /**
     * 查询当前用户的文章列表（按状态分页，含分页元数据）
     *
     * @param status 文章状态 (published/draft，或 all=三个审核中状态合并)
     * @return 分页的文章列表
     */
    @Override
    public Result<PageVo<List<ArticleInfoVo>>> listMine(String status) {
        String userId = getUserId();
        PageDomain pd = TableSupport.getPageDomain();
        Page<Article> page = new Page<>(pd.getPageNum(), pd.getPageSize());
        List<Article> articles = articleDao.listByAuthorAndStatus(userId, status, page);
        List<ArticleInfoVo> voList = copyList(articles, ArticleInfoVo.class);
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
        enrichWithTags(voList);
        return Result.ok(new PageVo<>((int) page.getCurrent(), (int) page.getSize(),
                page.getTotal(), (int) page.getPages(), voList));
    }

    // ===== 专栏前台读 =====

    /**
     * 专栏列表（前台）：全部专栏按创建时间升序，并附已发布成员数角标
     *
     * @return 专栏列表（含 articleCount，无有效成员为 0）
     */
    @Override
    public Result<List<SeriesReadVo>> listSeriesRead() {
        return Result.ok(buildSeriesReadVos());
    }

    /**
     * 首页随机专栏推荐：只含有已发布文章的专栏参与随机，上限 8。
     * 应用层 Collections.shuffle（表量小，避免 SQL RAND() 不可测），每次请求结果不同。
     *
     * @return 随机专栏列表（最多 8 个；无有效专栏返回空列表）
     */
    @Override
    public Result<List<SeriesReadVo>> randomSeriesRead() {
        List<SeriesReadVo> nonEmpty = buildSeriesReadVos().stream()
                .filter(vo -> vo.getArticleCount() > 0)
                .collect(Collectors.toList());
        Collections.shuffle(nonEmpty);
        return Result.ok(nonEmpty.subList(0, Math.min(nonEmpty.size(), RANDOM_SERIES_LIMIT)));
    }

    /**
     * 全量专栏 VO 组装（按创建时间升序 + 已发布成员数角标，无有效成员为 0）
     */
    private List<SeriesReadVo> buildSeriesReadVos() {
        List<ArticleSeries> seriesList = seriesMapper.selectList(
                new LambdaQueryWrapper<ArticleSeries>().orderByAsc(ArticleSeries::getCreatedAt));
        Map<String, Long> countMap = articleMapper.selectPublishedCountGroupBySeries().stream()
                .collect(Collectors.toMap(SeriesMemberCountVo::getSeriesId,
                        vo -> vo.getArticleCount() == null ? 0L : vo.getArticleCount()));
        List<SeriesReadVo> vos = new ArrayList<>();
        for (ArticleSeries s : seriesList) {
            SeriesReadVo vo = copyProperties(s, SeriesReadVo.class);
            vo.setArticleCount(countMap.getOrDefault(s.getId(), 0L));
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 专栏详情（前台）：已发布成员按 sort_order 升序分页。
     * 分页钳制（公开白名单端点，防超大 LIMIT / 负 offset）：page 1~10000（深分页钳制）、
     * size 1~100（默认 10，上限与既有 normalizePage 口径一致）；钳制后 (page-1)*size ≤
     * 9999*100 ≈ 1e6，int 运算无溢出，mapper int 参数无需改动。
     *
     * @param seriesId 专栏 ID
     * @param pageNum  页码（1-based，null/<1 按 1，>10000 按 10000）
     * @param pageSize 每页大小（null/<1 按 10，>100 按 100）
     * @return 专栏详情（成员文章含统计/作者/标签 enrich）
     */
    @Override
    public Result<SeriesDetailVo> getSeriesDetail(String seriesId, Integer pageNum, Integer pageSize) {
        ArticleSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new NotFoundException(I18nUtils.t("series.not_found"));
        }
        int page = pageNum == null || pageNum < 1 ? 1 : Math.min(pageNum, 10000);
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        List<Article> articles = articleMapper.selectSeriesMemberPage(seriesId, (page - 1) * size, size);
        List<ArticleInfoVo> vos = articles.stream()
                .map(a -> copyProperties(a, ArticleInfoVo.class))
                .collect(Collectors.toList());
        enrichWithStats(vos);
        enrichWithAuthorInfo(vos);
        enrichWithTags(vos);
        long total = articleMapper.selectSeriesMemberCount(seriesId);
        SeriesDetailVo detail = copyProperties(series, SeriesDetailVo.class);
        detail.setPageNum(page);
        detail.setPageSize(size);
        detail.setTotal(total);
        detail.setTotalPages((int) ((total + size - 1) / size));
        detail.setArticles(vos);
        // 专栏归属作者 enrich：author_id 为 NULL 的旧数据/站长级专栏三项保持空，前端判空隐藏作者行
        if (series.getAuthorId() != null && !series.getAuthorId().isEmpty()) {
            try {
                Result<UserDTO> userResult = userClient.getUserDTO(series.getAuthorId());
                if (userResult != null && userResult.getIsSuccess() && userResult.getData() != null) {
                    detail.setAuthorName(userResult.getData().getUsername());
                    detail.setAuthorAvatar(userResult.getData().getAvatarUrl());
                }
            } catch (Exception e) {
                log.warn("获取专栏作者信息失败, seriesId: {}", seriesId, e);
            }
        }
        return Result.ok(detail);
    }
}

