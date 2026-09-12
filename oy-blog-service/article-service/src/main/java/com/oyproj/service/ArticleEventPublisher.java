package com.oyproj.service;

import com.oyproj.common.mq.constants.ArticleBehaviorType;

/**
 * 用户行为事件发布器。
 *
 * <p><b>契约：实现方必须吞掉一切异常。</b>热榜是装饰功能，发布失败绝不能影响
 * 浏览/点赞接口的返回，也不重试、不回滚。</p>
 */
public interface ArticleEventPublisher {

    /** 发布浏览事件（游客 userId 可为 null） */
    void publishView(String articleId, String userId);

    /** 发布点赞/取消点赞事件 */
    void publishLike(String articleId, String userId, boolean cancelled);

    /** 发布收藏/取消收藏事件 */
    void publishFavorite(String articleId, String userId, boolean cancelled);

    /** 通用发布（评论等场景） */
    void publish(String articleId, String userId, ArticleBehaviorType type);
}
