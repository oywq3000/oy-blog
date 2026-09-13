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
    Result<PageVo<List<ArticleIndexMessage>>> snapshot(int pageNum, int pageSize);
}
