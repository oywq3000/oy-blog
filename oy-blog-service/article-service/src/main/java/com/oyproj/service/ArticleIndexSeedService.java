package com.oyproj.service;

import java.util.List;

/**
 * 把现有全量文章"种"进 {@code article.index} 压实主题。
 *
 * <p>让重放能力从切换第一天就完整——否则 topic 从空开始，重放只能重建切换后的文章。</p>
 */
public interface ArticleIndexSeedService {

    /**
     * @param total          逐页读到的<b>记录条数</b>（<b>含重复</b>；播种期间若有人发布/删除，
     *                       可能多于或少于实际文章数）——<b>不是文章总数</b>，别拿它当计数看；
     *                       要核对"是不是每篇都种进去了"，请按 articleId 去重后与库内 id 集合比对
     * @param succeeded      成功发出的条数
     * @param failedArticleIds 发送失败的文章 id（可据此重跑，播种是幂等的）
     */
    record SeedResult(int total, int succeeded, List<String> failedArticleIds) {}

    /** 播种；幂等，可重复调用（压实后每篇仍只有一条记录） */
    SeedResult seedIndexTopic();
}
