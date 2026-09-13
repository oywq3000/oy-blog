package com.oyproj.service;

import java.util.List;

/**
 * 把现有全量文章"种"进 {@code article.index} 压实主题。
 *
 * <p>让重放能力从切换第一天就完整——否则 topic 从空开始，重放只能重建切换后的文章。</p>
 */
public interface ArticleIndexSeedService {

    /**
     * @param total          快照中的文章总数
     * @param succeeded      成功发出的条数
     * @param failedArticleIds 发送失败的文章 id（可据此重跑，播种是幂等的）
     */
    record SeedResult(int total, int succeeded, List<String> failedArticleIds) {}

    /** 播种；幂等，可重复调用（压实后每篇仍只有一条记录） */
    SeedResult seedIndexTopic();
}
