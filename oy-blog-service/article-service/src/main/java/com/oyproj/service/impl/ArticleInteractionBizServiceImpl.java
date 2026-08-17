package com.oyproj.service.impl;


import com.oyproj.api.user.client.UserClient;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.service.CommonCache;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleFavorite;
import com.oyproj.domain.entity.ArticleLog;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.vo.ArticleInfoVo;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleFavoriteDao;
import com.oyproj.dto.ArticleLikeDao;
import com.oyproj.dto.ArticleLogDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.service.ArticleInteractionBizService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章互动业务服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleInteractionBizServiceImpl extends ArticleBaseBizService implements ArticleInteractionBizService {

    @NotNull private final ArticleLogDao viewDao;
    @NotNull private final ArticleStatsDao statsDao;
    @NotNull private final ArticleLikeDao likeDao;
    @NotNull private final ArticleFavoriteDao favoriteDao;
    @NotNull private final ArticleDao articleDao;
    @NotNull private final CommonCache<Object> commonCache;
    @NotNull private final UserClient userClient;

    /**
     * 点赞文章
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> like(String articleId) {
        String userId = getUserId();
        if (!likeDao.hasLiked(articleId, userId)) {
            likeDao.like(articleId, userId);
            statsDao.incLikes(articleId, 1);
        }
        return Result.ok();
    }

    /**
     * 取消点赞文章
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> unlike(String articleId) {
        String userId = getUserId();
        if (likeDao.hasLiked(articleId, userId)) {
            likeDao.unlike(articleId, userId);
            statsDao.incLikes(articleId, -1);
        }
        return Result.ok();
    }

    /**
     * 收藏文章（仅认证用户）
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> favorite(String articleId) {
        if (isGuest()) {
            return Result.error("游客不支持收藏");
        }
        String userId = getUserId();
        if (!favoriteDao.hasFavorited(articleId, userId)) {
            favoriteDao.favorite(articleId, userId);
            statsDao.incFavorites(articleId, 1);
        }
        return Result.ok();
    }

    /**
     * 取消收藏文章（仅认证用户）
     *
     * @param articleId 文章ID
     * @return 结果
     */
    @Override
    public Result<Object> unfavorite(String articleId) {
        if (isGuest()) {
            return Result.error("游客不支持收藏");
        }
        String userId = getUserId();
        if (favoriteDao.hasFavorited(articleId, userId)) {
            favoriteDao.unfavorite(articleId, userId);
            statsDao.incFavorites(articleId, -1);
        }
        return Result.ok();
    }

    /**
     * 判断当前请求是否为游客
     */
    private boolean isGuest() {
        String userId = getUserId();
        return userId != null && userId.startsWith(CachePrefix.GUEST_ID.getPrefix());
    }

     /**
      * 检查用户是否点赞文章
      *
      * @param articleId 文章ID
      * @return 是否点赞
      */
    @Override
    public Result<Boolean> isLiked(String articleId) {
        return Result.ok(likeDao.hasLiked(articleId, getUserId()));
    }

    /**
     * 检查用户是否收藏文章
     *
     * @param articleId 文章ID
     * @return 是否收藏
     */
    @Override
    public Result<Boolean> isFavorited(String articleId) {
        return Result.ok(favoriteDao.hasFavorited(articleId, getUserId()));
    }

    /**
     * 统计文章点赞数量
     *
     * @param articleId 文章ID
     * @return 点赞数量
     */
    @Override
    public Result<Long> likeCount(String articleId) {
        return Result.ok(likeDao.likeCount(articleId));
    }

     /**
      * 统计文章收藏数量
      *
      * @param articleId 文章ID
      * @return 收藏数量
      */
    @Override
    public Result<Long> favoriteCount(String articleId) {
        return Result.ok(favoriteDao.favoriteCount(articleId));
    }

    /**
     * 记录文章观看
     *
     * @param articleId 文章ID
     * @return 最新观看次数
     */
    @Override
    @Transactional
    public Result<Long> view(String articleId) {
        // 1. 检查文章是否存在
        Article article = articleDao.getById(articleId);
        if (article == null) {
            return Result.error("文章不存在");
        }

        String userId = getUserId();
        String clientIp = getClientIp();

        // 2. 记录浏览历史（仅登录用户；游客匿名浏览不写历史）
        // 必须先于计数去重：即使命中 Redis 去重窗口，也要刷新 viewAt，
        // 保证"刚浏览的文章排在历史第一位"（历史去重靠一人一文章一行）
        if (userId != null && !userId.startsWith(CachePrefix.GUEST_ID.getPrefix())) {
            try {
                ArticleLog viewLog = ArticleLog.builder()
                        .id(getId())
                        .articleId(articleId)
                        .userId(userId)
                        .action("view")
                        .ip(clientIp)
                        .ua(getRequestHeader("User-Agent"))
                        .referer(getRequestHeader("Referer"))
                        .viewAt(LocalDateTime.now())
                        .build();
                viewDao.appendView(viewLog);
            } catch (Exception e) {
                // 历史日志是辅助数据，写入失败不影响浏览计数
                log.warn("记录浏览历史失败, articleId: {}, userId: {}", articleId, userId, e);
            }
        }

        // 3. 计数去重检查
        // IP 去重：同一IP对同一文章 5 分钟内只计一次
        if (clientIp != null && !clientIp.isEmpty()) {
            String ipKey = "view:article:" + articleId + ":ip:" + clientIp;
            if (commonCache.hasKey(ipKey)) {
                return getCurrentViewCount(articleId);
            }
            commonCache.put(ipKey, "1", 300L);
        }

        // 用户去重：登录用户 30 分钟内只计一次
        if (userId != null && !userId.startsWith(CachePrefix.GUEST_ID.getPrefix())) {
            String userKey = "view:article:" + articleId + ":user:" + userId;
            if (commonCache.hasKey(userKey)) {
                return getCurrentViewCount(articleId);
            }
            commonCache.put(userKey, "1", 1800L);
        }

        // 4. 确保统计记录存在并递增
        ArticleStats stats = statsDao.getById(articleId);
        if (stats == null) {
            try {
                stats = ArticleStats.builder()
                        .articleId(articleId)
                        .views(1L)
                        .likes(0L)
                        .comments(0L)
                        .favorites(0L)
                        .build();
                statsDao.save(stats);
                return Result.ok(1L);
            } catch (DuplicateKeyException e) {
                // 并发创建时，另一请求已创建，降级为更新
                statsDao.incViews(articleId, 1);
            }
        } else {
            statsDao.incViews(articleId, 1);
        }

        // 5. 返回递增后的观看次数
        //stats = statsDao.getById(articleId);
        return Result.ok(stats != null ? stats.getViews()+1 : 1L);
    }

    /**
     * 获取当前观看次数（去重命中时返回）
     */
    private Result<Long> getCurrentViewCount(String articleId) {
        ArticleStats stats = statsDao.getById(articleId);
        return Result.ok(stats != null ? stats.getViews() : 0L);
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取指定请求头（无请求上下文时返回 null）
     */
    private String getRequestHeader(String name) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getHeader(name);
    }

    /**
     * 查询当前用户收藏的文章列表（仅认证用户）
     *
     * @return 收藏文章列表（按收藏时间倒序，含 favoritedAt）
     */
    @Override
    public Result<List<ArticleInfoVo>> listFavorites() {
        if (isGuest()) {
            return Result.error("游客不支持收藏");
        }
        String userId = getUserId();
        List<ArticleFavorite> favorites = getPage(() -> favoriteDao.listFavorites(userId), ArticleFavorite.class);
        if (favorites.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<String> articleIds = favorites.stream()
                .map(ArticleFavorite::getArticleId)
                .collect(Collectors.toList());
        Map<String, LocalDateTime> favoritedAtMap = favorites.stream()
                .collect(Collectors.toMap(ArticleFavorite::getArticleId,
                        ArticleFavorite::getFavoritedAt, (a, b) -> a));
        List<Article> articles = articleDao.listByIds(articleIds);
        List<Article> sortedArticles = new ArrayList<>();
        for (String id : articleIds) {
            articles.stream().filter(a -> a.getId().equals(id)).findFirst().ifPresent(sortedArticles::add);
        }
        List<ArticleInfoVo> voList = copyList(sortedArticles, ArticleInfoVo.class);
        for (ArticleInfoVo vo : voList) {
            vo.setFavoritedAt(favoritedAtMap.get(vo.getId()));
        }
        enrichWithStats(voList);
        enrichWithAuthorInfo(voList);
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
        List<ArticleStats> statsList = statsDao.listByArticleIds(articleIds);
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
}
