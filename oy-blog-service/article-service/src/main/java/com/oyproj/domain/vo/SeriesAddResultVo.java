package com.oyproj.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 宽容批量收录结果 VO（creator 编辑页"添加文章"弹窗回显）
 *
 * <p>语义：候选文章逐篇校验，违规文章跳过不中断整体（宽容语义），
 * 已在目标专栏内的重复请求静默跳过（幂等，既不计 added 也不进 skipped）。</p>
 */
@Data
public class SeriesAddResultVo {
    /**
     * 实际新增的关系数
     */
    private int addedCount;

    /**
     * 逐篇被跳过明细（按请求顺序）；无跳过为空列表
     */
    private List<SeriesSkipVo> skipped;
}
