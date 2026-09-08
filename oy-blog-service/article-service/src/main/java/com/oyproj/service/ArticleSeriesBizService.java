package com.oyproj.service;

import com.oyproj.api.article.domain.vo.SeriesMemberAdminVo;

import java.util.List;

/**
 * 文章-专栏成员关系业务服务
 *
 * <p>数据正确性契约（本功能的心脏，后续发布链/管理端均消费本接口）：</p>
 * <ul>
 *   <li>单篇文章最多加入 {@link #MAX_SERIES_PER_ARTICLE} 个专栏，超出抛 {@code ValidationException} 并整体回滚；</li>
 *   <li>收录幂等：重复加入同一专栏静默跳过；</li>
 *   <li>排序为 sort_order 的相邻 swap，队首 up / 队尾 down / 非法 direction / 成员不存在均为 no-op false；</li>
 *   <li>整文改绑为全量替换语义：不在新集合的旧关系删除、缺失的新关系追加到各专栏队尾；</li>
 *   <li>删除文章/专栏时级联清理关系行（物理删）。</li>
 * </ul>
 */
public interface ArticleSeriesBizService {

    /**
     * 一篇文章最多加入的专栏数
     */
    int MAX_SERIES_PER_ARTICLE = 3;

    /**
     * 批量收录：逐篇校验 ≤{@link #MAX_SERIES_PER_ARTICLE}，重复（已在专栏内）跳过；
     * 任一文章超限抛 ValidationException（事务整体回滚）；返回实际新增数。
     *
     * @param seriesId   专栏 ID
     * @param articleIds 文章 ID 列表（可含重复；null/空返回 0）
     * @return 实际新增的关系行数
     */
    int addSeriesArticles(String seriesId, List<String> articleIds);

    /**
     * 将文章移出专栏；关系行不存在返回 false（幂等）。
     *
     * @param seriesId  专栏 ID
     * @param articleId 文章 ID
     * @return 是否确有删除
     */
    boolean removeSeriesArticle(String seriesId, String articleId);

    /**
     * 上移/下移（与相邻成员 swap sort_order）。
     * 队首 up、队尾 down、非法 direction、成员不存在均为 no-op false。
     *
     * @param seriesId   专栏 ID
     * @param articleId  文章 ID
     * @param direction  移动方向：up / down
     * @return 是否执行了 swap
     */
    boolean moveSeriesArticle(String seriesId, String articleId, String direction);

    /**
     * 整文全量改绑（publish 链与管理端用）：seriesIds=null 视为空。
     * 新集合超 {@link #MAX_SERIES_PER_ARTICLE} 或含不存在的专栏抛异常（整体回滚，不产生部分改动）。
     *
     * @param articleId 文章 ID
     * @param seriesIds 目标专栏 ID 列表（可含 null/重复，会被去重）
     */
    void replaceArticleSeries(String articleId, List<String> seriesIds);

    /**
     * 删除文章时清理其全部关联行（物理删，软删文章不参与展示）
     *
     * @param articleId 文章 ID
     */
    void clearByArticle(String articleId);

    /**
     * 删除专栏时清空成员
     *
     * @param seriesId 专栏 ID
     */
    void clearBySeries(String seriesId);

    /**
     * 管理端成员列表（含草稿成员，按 sort_order 升序，标题由 article 联查）。
     * 已不存在或已软删的文章不列入。
     *
     * @param seriesId 专栏 ID（不存在抛 NotFoundException）
     * @return 成员 VO 列表（可为空）
     */
    List<SeriesMemberAdminVo> listSeriesMembers(String seriesId);
}
