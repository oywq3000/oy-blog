package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.config.HotWeightProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArticleEventWeights 事件权重映射")
class ArticleEventWeightsTest {

    private ArticleEventWeights weights;

    @BeforeEach
    void setUp() {
        HotWeightProperties props = new HotWeightProperties();
        props.setViews(1L);
        props.setLikes(2L);
        props.setComments(5L);
        props.setFavorites(3L);
        weights = new ArticleEventWeights(props);
    }

    @Test
    void view_mapsToViewsWeight() {
        assertEquals(1, weights.weightOf(ArticleBehaviorType.VIEW));
    }

    @Test
    void like_mapsToPositiveLikesWeight() {
        assertEquals(2, weights.weightOf(ArticleBehaviorType.LIKE));
    }

    @Test
    void unlike_mapsToNegativeLikesWeight() {
        assertEquals(-2, weights.weightOf(ArticleBehaviorType.UNLIKE));
    }

    @Test
    void favorite_mapsToPositiveFavoritesWeight() {
        assertEquals(3, weights.weightOf(ArticleBehaviorType.FAVORITE));
    }

    @Test
    void unfavorite_mapsToNegativeFavoritesWeight() {
        assertEquals(-3, weights.weightOf(ArticleBehaviorType.UNFAVORITE));
    }

    @Test
    void comment_mapsToCommentsWeight() {
        assertEquals(5, weights.weightOf(ArticleBehaviorType.COMMENT));
    }

    @Test
    void weightsFollowConfig_notHardcoded() {
        HotWeightProperties custom = new HotWeightProperties();
        custom.setViews(10L);
        custom.setLikes(20L);
        custom.setComments(50L);
        custom.setFavorites(30L);
        ArticleEventWeights customWeights = new ArticleEventWeights(custom);

        assertEquals(10, customWeights.weightOf(ArticleBehaviorType.VIEW));
        assertEquals(-20, customWeights.weightOf(ArticleBehaviorType.UNLIKE));
    }
}
