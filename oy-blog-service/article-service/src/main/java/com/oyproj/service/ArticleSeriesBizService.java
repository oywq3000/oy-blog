package com.oyproj.service;

import com.oyproj.api.article.domain.dto.SeriesSaveDto;
import com.oyproj.api.article.domain.vo.SeriesMemberAdminVo;
import com.oyproj.domain.vo.SeriesReadVo;

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
 *
 * <p>归属契约（spec §九）：专栏可属于某用户（author_id）或为 null（站长级）。
 * 创作端（creator 端点/publish 链）只能操作自己的专栏：更新/删除校验 owner，
 * 发布绑定时逐目标校验 author_id 归属；authorId 为 null 的站长级专栏对非 ADMIN 视同越权
 * （抛 {@code ForbiddenException}）。ADMIN 例外放行（operatorId=null 或 isAdmin=true）。</p>
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
     * 新集合超 {@link #MAX_SERIES_PER_ARTICLE}、含不存在的专栏抛异常；
     * operatorId 非 null（创作端 publish 链）时逐目标校验归属：
     * 专栏 author_id 必须等于 operatorId，author_id 为 null 的站长级专栏一律拒绝——
     * 两种越权均抛 {@code ForbiddenException}（整体回滚，不产生部分改动）。
     *
     * @param articleId  文章 ID
     * @param seriesIds  目标专栏 ID 列表（可含 null/重复，会被去重）
     * @param operatorId 操作者用户 ID；null = 管理端/免校验模式（admin 改绑走此路径）
     */
    void replaceArticleSeries(String articleId, List<String> seriesIds, String operatorId);

    /**
     * 我的专栏（创作中心列表/发布选栏器用）：仅本人创建的专栏（author_id=userId），
     * 附已发布公开文章计数（复用 {@code ArticleMapper.selectPublishedCountGroupBySeries} 后按我的 id 交集）。
     *
     * @param userId 当前登录用户 ID
     * @return 我的专栏列表（按创建时间升序；无则空列表）
     */
    List<SeriesReadVo> listOwnSeries(String userId);

    /**
     * 新建专栏：author_id = userId（任何登录用户可创建自己的专栏）。
     *
     * @param dto    名称必填；description/coverUrl 可选
     * @param userId 创建者用户 ID
     * @return 新专栏 ID
     */
    String createSeries(SeriesSaveDto dto, String userId);

    /**
     * 更新专栏（改名/描述/封面，code 不动）。
     *
     * @param id         专栏 ID（不存在抛 NotFoundException）
     * @param dto        新值（name 必填）
     * @param operatorId 操作者用户 ID
     * @param isAdmin    是否 ADMIN（X-User-Type=ADMIN 例外放行，可改全站含站长级专栏）
     * @throws com.oyproj.common.exception.ForbiddenException 非本人专栏（含站长级）且非 ADMIN
     */
    void updateSeries(String id, SeriesSaveDto dto, String operatorId, boolean isAdmin);

    /**
     * 删除我的专栏：owner 校验 + 事务内级联清空成员（复用 {@link #clearBySeries}）再删行。
     *
     * @param id         专栏 ID（不存在抛 NotFoundException）
     * @param operatorId 操作者用户 ID
     * @param isAdmin    是否 ADMIN（例外放行，可删全站含站长级专栏）
     * @throws com.oyproj.common.exception.ForbiddenException 非本人专栏（含站长级）且非 ADMIN
     */
    void deleteOwnSeries(String id, String operatorId, boolean isAdmin);

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
