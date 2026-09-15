package com.oyproj.service;

import java.util.List;

/**
 * 相似内容推荐业务接口
 */
public interface RecommendationBizService {

    /**
     * @return 排序后的文章 id 列表（top resultCacheSize）；空列表=画像冷启动/无候选 → 调用方回退热榜
     */
    List<String> recommendArticleIds(String actorId, boolean guest);
}