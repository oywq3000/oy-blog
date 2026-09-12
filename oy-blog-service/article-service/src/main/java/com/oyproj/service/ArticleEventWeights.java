package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.config.HotWeightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 行为事件 → 热度权重映射。
 *
 * <p>复用 {@link HotWeightProperties}（与全时段总榜同一套权重），
 * 避免新老榜单排序自相矛盾。取消类事件取负值，使榜单可涨可跌。</p>
 */
@Component
@RequiredArgsConstructor
public class ArticleEventWeights {

    private final HotWeightProperties hotWeightProperties;

    public int weightOf(ArticleBehaviorType type) {
        return switch (type) {
            case VIEW -> (int) hotWeightProperties.getViews();
            case LIKE -> (int) hotWeightProperties.getLikes();
            case UNLIKE -> -(int) hotWeightProperties.getLikes();
            case FAVORITE -> (int) hotWeightProperties.getFavorites();
            case UNFAVORITE -> -(int) hotWeightProperties.getFavorites();
            case COMMENT -> (int) hotWeightProperties.getComments();
        };
    }
}
