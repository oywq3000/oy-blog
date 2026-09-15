package com.oyproj.service.impl;

import com.oyproj.common.RecommendKeys;
import com.oyproj.common.service.CommonCache;
import com.oyproj.config.RecommendProperties;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.domain.entity.ArticleTag;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleFavoriteDao;
import com.oyproj.dto.ArticleLikeDao;
import com.oyproj.dto.ArticleLogDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.service.RecommendationBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 相似内容推荐打分引擎：
 * 画像 top 标签 → 候选文章（published、非已消费）→ Σ 命中标签画像权重 排序，
 * 同分按阅读量降序。返回空列表表示冷启动，调用方回退热榜。
 */
@Service
@RequiredArgsConstructor
public class RecommendationBizServiceImpl implements RecommendationBizService {

    private final CommonCache<Object> commonCache;
    private final ArticleTagDao articleTagDao;
    private final ArticleDao articleDao;
    private final ArticleStatsDao articleStatsDao;
    private final ArticleLogDao viewDao;
    private final ArticleLikeDao likeDao;
    private final ArticleFavoriteDao favoriteDao;
    private final RecommendProperties props;

    @Override
    public List<String> recommendArticleIds(String actorId, boolean guest) {
        String profileKey = guest
                ? RecommendKeys.profileGuest(actorId)
                : RecommendKeys.profileUser(actorId);
        Set<ZSetOperations.TypedTuple<Object>> top =
                commonCache.reverseRangeWithScores(profileKey, 0, props.getProfileTopTags() - 1);
        if (top == null || top.isEmpty()) {
            return List.of();
        }
        Map<String, Double> tagWeight = new HashMap<>();
        for (ZSetOperations.TypedTuple<Object> tuple : top) {
            Object value = tuple.getValue();
            if (value != null) {
                tagWeight.put(String.valueOf(value),
                        tuple.getScore() == null ? 0d : tuple.getScore());
            }
        }
        List<ArticleTag> relations = articleTagDao.listByTagIds(new ArrayList<>(tagWeight.keySet()));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<String> candidateIds = relations.stream()
                .map(ArticleTag::getArticleId).distinct().toList();
        Set<String> published = articleDao.listByIds(candidateIds).stream()
                .filter(a -> "published".equals(a.getStatus()) && a.getDeletedAt() == null)
                .map(Article::getId)
                .collect(Collectors.toSet());
        if (published.isEmpty()) {
            return List.of();
        }
        Set<String> consumed = resolveConsumed(actorId, guest, published);
        Map<String, Double> score = new HashMap<>();
        for (ArticleTag rel : relations) {
            String articleId = rel.getArticleId();
            if (!published.contains(articleId) || consumed.contains(articleId)) {
                continue;
            }
            double w = tagWeight.getOrDefault(rel.getTagId(), 0d);
            if (w > 0) {
                score.merge(articleId, w, Double::sum);
            }
        }
        if (score.isEmpty()) {
            return List.of();   // 候选全部被消费 → 空=冷启动，Task6 据此回退热榜
        }
        Map<String, Integer> views = loadViews(score.keySet());
        Comparator<Map.Entry<String, Double>> byScore =
                Map.Entry.<String, Double>comparingByValue().reversed();
        Comparator<Map.Entry<String, Double>> byViews =
                Comparator.comparingInt(e -> views.getOrDefault(e.getKey(), 0));
        return score.entrySet().stream()
                .sorted(byScore.thenComparing(byViews.reversed()).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .limit(props.getResultCacheSize())
                .toList();
    }

    /**
     * 登录用户：视图历史 ∪ 点赞 ∪ 收藏（DB 记录）；
     * 游客：Redis 已消费 ZSet（推荐请求缓存 key）。
     */
    private Set<String> resolveConsumed(String actorId, boolean guest, Set<String> candidates) {
        if (guest) {
            Set<ZSetOperations.TypedTuple<Object>> seen =
                    commonCache.reverseRangeWithScores(RecommendKeys.consumedGuest(actorId), 0, 499);
            if (seen == null) {
                return Set.of();
            }
            return seen.stream().map(ZSetOperations.TypedTuple::getValue)
                    .filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toSet());
        }
        Set<String> consumed = new HashSet<>();
        consumed.addAll(viewDao.listHistoryArticleIds(actorId));
        consumed.addAll(likeDao.listLikedArticleIds(actorId));
        consumed.addAll(favoriteDao.listFavoritedArticleIds(actorId));
        return consumed;
    }

    private Map<String, Integer> loadViews(Set<String> articleIds) {
        Map<String, Integer> m = new HashMap<>();
        List<ArticleStats> stats = articleStatsDao.listByArticleIds(new ArrayList<>(articleIds));
        for (ArticleStats s : stats) {
            m.put(s.getArticleId(), s.getViews() == null ? 0 : s.getViews().intValue());
        }
        return m;
    }
}