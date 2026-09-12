package com.oyproj.common.mq.constants;

/**
 * 文章用户行为事件类型。
 *
 * <p>UNLIKE / UNFAVORITE 用于把取消操作的负权重回灌热榜，缺少它们会导致榜单只涨不跌。</p>
 */
public enum ArticleBehaviorType {
    VIEW,
    LIKE,
    UNLIKE,
    FAVORITE,
    UNFAVORITE,
    COMMENT
}
