package com.oyproj.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.service.CommonCache;
import com.oyproj.domain.entity.ArticleFavorite;
import com.oyproj.domain.entity.ArticleLike;
import com.oyproj.domain.entity.ArticleLog;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.mapper.ArticleFavoriteMapper;
import com.oyproj.mapper.ArticleLikeMapper;
import com.oyproj.mapper.ArticleLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendBackfillRunner")
class RecommendBackfillRunnerTest {

    @Mock private ArticleLogMapper viewMapper;
    @Mock private ArticleLikeMapper likeMapper;
    @Mock private ArticleFavoriteMapper favoriteMapper;
    @Mock private ArticleTagDao articleTagDao;
    @Mock private CommonCache<Object> commonCache;
    private RecommendBackfillRunner runner;

    @BeforeAll
    static void initMybatisTableInfo() {
        // LambdaQueryWrapper 解析方法引用需要实体表元数据；纯单测无 Spring 容器，手动注册
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ArticleLog.class);
        TableInfoHelper.initTableInfo(assistant, ArticleLike.class);
        TableInfoHelper.initTableInfo(assistant, ArticleFavorite.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        runner = new RecommendBackfillRunner(viewMapper, likeMapper, favoriteMapper,
                articleTagDao, commonCache, new HotWeightProperties());
        Field f = RecommendBackfillRunner.class.getDeclaredField("backfillEnabled");
        f.setAccessible(true);
        f.set(runner, true);
    }

    @Test
    void disabled_skipsAllDataAccess() throws Exception {
        Field f = RecommendBackfillRunner.class.getDeclaredField("backfillEnabled");
        f.setAccessible(true);
        f.set(runner, false);
        runner.run(null);
        verifyNoInteractions(viewMapper, likeMapper, favoriteMapper, articleTagDao, commonCache);
    }

    @Test
    void rebuildsProfile_deleteThenZAdd_skipsGuests() {
        when(viewMapper.selectList(any())).thenReturn(List.of(
                ArticleLog.builder().userId("u1").articleId("a1").build()));
        when(likeMapper.selectList(any())).thenReturn(List.of(
                ArticleLike.builder().userId("u1").articleId("a1").build(),
                ArticleLike.builder().userId(CachePrefix.GUEST_ID.getPrefix() + "g0").articleId("a9").build()));
        when(favoriteMapper.selectList(any())).thenReturn(List.of());
        when(articleTagDao.listTagIdMapByArticleIds(List.of("a1"))).thenReturn(Map.of("a1", List.of("t1")));

        runner.run(null);

        verify(commonCache).remove("rec:profile:user:u1");           // 先删后建：幂等
        verify(commonCache).zAdd("rec:profile:user:u1", 3L, "t1");   // view×1 + like×2 = 3
        verify(commonCache, never()).zAdd(argThat(s -> s.startsWith("rec:profile:guest:")), anyLong(), anyString());
        verify(commonCache, never()).remove("rec:profile:guest:" + CachePrefix.GUEST_ID.getPrefix() + "g0");
    }
}