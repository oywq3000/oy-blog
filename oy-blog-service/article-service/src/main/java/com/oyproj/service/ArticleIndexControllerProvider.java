package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.domain.ArticleIndexMessage;

import java.util.List;

/**
 * 索引快照数据来源（由 {@code ArticleIndexController} 实现）。
 * 抽成接口只为让播种逻辑可单测，不去直接依赖 controller。
 */
public interface ArticleIndexControllerProvider {

    /**
     * 取一页快照。
     *
     * <p><b>页号是 1-based</b>（不是 controller 的 {@code @RequestParam(defaultValue = "0")} 那种 0-based
     * 历史约定）：实现最终把它传进 MyBatis-Plus 的 {@code new Page<>(pageNum, size)}，MP 的 Page 是
     * 1-based —— 传 0 与传 1 会命中<b>同一页</b>（{@code IPage.offset()}: {@code current <= 1 → 0}），
     * 从 0 开始翻页会漏掉第二页之后的全部文章。调用方（播种服务/对账）请从 1 开始。</p>
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     */
    Result<PageVo<List<ArticleIndexMessage>>> snapshot(int pageNum, int pageSize);
}
