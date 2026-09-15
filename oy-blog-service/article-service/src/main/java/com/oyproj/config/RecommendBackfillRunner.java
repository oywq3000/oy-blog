package com.oyproj.config;

import com.oyproj.common.RecommendKeys;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.service.CommonCache;
import com.oyproj.domain.entity.ArticleFavorite;
import com.oyproj.domain.entity.ArticleLike;
import com.oyproj.domain.entity.ArticleLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.mapper.ArticleFavoriteMapper;
import com.oyproj.mapper.ArticleLikeMapper;
import com.oyproj.mapper.ArticleLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量用户推荐画像补齐（一次性 ApplicationRunner，开关控制）。
 *
 * <p>读 article_log(浏览)/article_like(点赞)/article_favorite(收藏) 三表行为对，
 * 按热榜同款权重（HotWeightProperties）累加出每个标签的画像分，写 rec:profile:user:{id}。
 * 语义为"全量重建"：写前 remove key，重复执行不会翻倍。游客行为不写入。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendBackfillRunner implements ApplicationRunner {

    private final ArticleLogMapper viewMapper;
    private final ArticleLikeMapper likeMapper;
    private final ArticleFavoriteMapper favoriteMapper;
    private final ArticleTagDao articleTagDao;
    private final CommonCache<Object> commonCache;
    private final HotWeightProperties weights;

    @Value("${oyblog.recommend.backfill-enabled:false}")
    private boolean backfillEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!backfillEnabled) {
            log.info("推荐画像 backfill 未开启（oyblog.recommend.backfill-enabled=false），跳过");
            return;
        }
        try {
            doBackfill();
        } catch (Exception e) {
            // 装饰功能哲学：backfill 是启动期的一次性增强，绝不让中间件抖动把应用启动打崩。
            // 语义"全量重建（先删后建）"，本次失败即"未完成"，下次重启可重跑恢复。
            log.error("推荐画像 backfill 中断，可重跑恢复（先删后建语义）：本次失败未完成，下次重启将重跑", e);
        }
    }

    private void doBackfill() {
        long t0 = System.currentTimeMillis();
        // user -> (article -> 权重和；同文章多行为相加)
        Map<String, Map<String, Long>> userArticleWeight = new HashMap<>();
        for (ArticleLog row : viewMapper.selectList(new LambdaQueryWrapper<ArticleLog>()
                .select(ArticleLog::getUserId, ArticleLog::getArticleId)
                .isNotNull(ArticleLog::getUserId).eq(ArticleLog::getAction, "view"))) {
            accumulate(userArticleWeight, row.getUserId(), row.getArticleId(), weights.getViews());
        }
        for (ArticleLike row : likeMapper.selectList(new LambdaQueryWrapper<ArticleLike>()
                .select(ArticleLike::getUserId, ArticleLike::getArticleId)
                .isNotNull(ArticleLike::getUserId))) {
            accumulate(userArticleWeight, row.getUserId(), row.getArticleId(), weights.getLikes());
        }
        for (ArticleFavorite row : favoriteMapper.selectList(new LambdaQueryWrapper<ArticleFavorite>()
                .select(ArticleFavorite::getUserId, ArticleFavorite::getArticleId)
                .isNotNull(ArticleFavorite::getUserId))) {
            accumulate(userArticleWeight, row.getUserId(), row.getArticleId(), weights.getFavorites());
        }
        // 游客行为不参与画像重建：其文章ID也不应进入标签查询；剔除后无登录用户则整体跳过
        userArticleWeight.entrySet().removeIf(e -> e.getKey().startsWith(CachePrefix.GUEST_ID.getPrefix()));
        if (userArticleWeight.isEmpty()) {
            log.info("推荐画像 backfill：无存量登录用户行为数据，结束");
            return;
        }
        List<String> allArticleIds = userArticleWeight.values().stream()
                .flatMap(m -> m.keySet().stream()).distinct().toList();
        Map<String, List<String>> articleTags = articleTagDao.listTagIdMapByArticleIds(allArticleIds);

        int userCount = 0;
        for (Map.Entry<String, Map<String, Long>> userEntry : userArticleWeight.entrySet()) {
            String userId = userEntry.getKey();
            if (userId.startsWith(CachePrefix.GUEST_ID.getPrefix())) {
                continue;   // 游客无历史表记录（浏览不落库），且画像应临时
            }
            Map<String, Long> tagScore = new HashMap<>();
            for (Map.Entry<String, Long> articleEntry : userEntry.getValue().entrySet()) {
                for (String tagId : articleTags.getOrDefault(articleEntry.getKey(), List.of())) {
                    tagScore.merge(tagId, articleEntry.getValue(), Long::sum);
                }
            }
            if (tagScore.isEmpty()) {
                continue;
            }
            String profileKey = RecommendKeys.profileUser(userId);
            commonCache.remove(profileKey);                 // 全量重建：先删，避免重复执行翻倍
            for (Map.Entry<String, Long> tag : tagScore.entrySet()) {
                commonCache.zAdd(profileKey, tag.getValue(), tag.getKey());
            }
            userCount++;
        }
        log.info("推荐画像 backfill 完成：重建 {} 个用户画像，耗时 {}ms", userCount, System.currentTimeMillis() - t0);
    }

    private void accumulate(Map<String, Map<String, Long>> userArticleWeight, String userId, String articleId, long weight) {
        userArticleWeight.computeIfAbsent(userId, k -> new HashMap<>())
                .merge(articleId, weight, Long::sum);
    }
}