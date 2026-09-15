package com.oyproj.service.impl;

import com.oyproj.common.constant.CachePrefix;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationBizServiceImpl 打分引擎")
class RecommendationBizServiceImplTest {

    @Mock private CommonCache<Object> commonCache;
    @Mock private ArticleTagDao articleTagDao;
    @Mock private ArticleDao articleDao;
    @Mock private ArticleStatsDao articleStatsDao;
    @Mock private ArticleLogDao viewDao;
    @Mock private ArticleLikeDao likeDao;
    @Mock private ArticleFavoriteDao favoriteDao;
    private RecommendationBizService svc;

    @BeforeEach
    void setUp() {
        RecommendProperties props = new RecommendProperties();
        svc = new RecommendationBizServiceImpl(commonCache, articleTagDao, articleDao,
                articleStatsDao, viewDao, likeDao, favoriteDao, props);
    }

    private ZSetOperations.TypedTuple<Object> tuple(String id, double score) {
        return new ZSetOperations.TypedTuple<>() {
            @Override public Object getValue() { return id; }
            @Override public Double getScore() { return score; }
            @Override public int compareTo(ZSetOperations.TypedTuple<Object> o) {
                return Double.compare(getScore(), o.getScore());
            }
        };
    }

    @Test
    void emptyProfile_coldStart_returnsEmpty() {
        when(commonCache.reverseRangeWithScores("rec:profile:user:u1", 0, 29)).thenReturn(null);
        assertTrue(svc.recommendArticleIds("u1", false).isEmpty());
        verifyNoInteractions(articleTagDao);
    }

    @Test
    void rankByTagWeight_excludesConsumed_andTieBreakByViews() {
        when(commonCache.reverseRangeWithScores("rec:profile:user:u1", 0, 29)).thenReturn(Set.of(tuple("t1", 10d), tuple("t2", 5d)));
        when(articleTagDao.listByTagIds(anyList())).thenReturn(List.of(
                ArticleTag.builder().articleId("a1").tagId("t1").build(),
                ArticleTag.builder().articleId("a2").tagId("t1").build(),
                ArticleTag.builder().articleId("a2").tagId("t2").build(),
                ArticleTag.builder().articleId("a3").tagId("t1").build()));
        when(articleDao.listByIds(List.of("a1", "a2", "a3"))).thenReturn(List.of(
                article("a1"), article("a2"), article("a3")));
        when(viewDao.listHistoryArticleIds("u1")).thenReturn(List.of("a1"));       // a1 已读排除
        when(likeDao.listLikedArticleIds("u1")).thenReturn(List.of());
        when(favoriteDao.listFavoritedArticleIds("u1")).thenReturn(List.of());
        when(articleStatsDao.listByArticleIds(anyList())).thenReturn(List.of(
                stats("a2", 100L), stats("a3", 50L)));
        List<String> ids = svc.recommendArticleIds("u1", false);
        assertEquals(List.of("a2", "a3"), ids);   // a2=15分(100views) > a3=10分(50views)
    }

    @Test
    void guestProfile_usesGuestConsumedKey_notDb() {
        String gid = CachePrefix.GUEST_ID.getPrefix() + "xyz";
        when(commonCache.reverseRangeWithScores("rec:profile:guest:" + gid, 0, 29))
                .thenReturn(Set.of(tuple("t1", 8d)));
        when(articleTagDao.listByTagIds(List.of("t1"))).thenReturn(List.of(
                ArticleTag.builder().articleId("a5").tagId("t1").build(),
                ArticleTag.builder().articleId("a6").tagId("t1").build()));
        when(articleDao.listByIds(List.of("a5", "a6"))).thenReturn(List.of(article("a5"), article("a6")));
        when(commonCache.reverseRangeWithScores("rec:consumed:guest:" + gid, 0, 499))
                .thenReturn(Set.of(tuple("a5", 1d)));   // a5 已看排除
        when(articleStatsDao.listByArticleIds(List.of("a6"))).thenReturn(List.of(stats("a6", 9L)));
        assertEquals(List.of("a6"), svc.recommendArticleIds(gid, true));
        verify(viewDao, never()).listHistoryArticleIds(anyString());
    }

    @Test
    void nonPublishedArticles_filteredOut() {
        when(commonCache.reverseRangeWithScores("rec:profile:user:u7", 0, 29)).thenReturn(Set.of(tuple("t1", 3d)));
        when(articleTagDao.listByTagIds(List.of("t1"))).thenReturn(List.of(
                ArticleTag.builder().articleId("draftA").tagId("t1").build()));
        when(articleDao.listByIds(List.of("draftA"))).thenReturn(List.of(Article.builder()
                .id("draftA").status("draft").deletedAt(null).build()));
        assertTrue(svc.recommendArticleIds("u7", false).isEmpty());
    }

    private Article article(String id) {
        return Article.builder().id(id).status("published").deletedAt(null).build();
    }
    private ArticleStats stats(String id, Long views) {
        return ArticleStats.builder().articleId(id).views(views).build();
    }
}