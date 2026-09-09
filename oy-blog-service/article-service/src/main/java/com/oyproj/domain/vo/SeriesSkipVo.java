package com.oyproj.domain.vo;

import lombok.Data;

/**
 * 宽容批量收录中被跳过的文章明细 VO（creator 编辑页"添加文章"用）
 *
 * <p>reasonCode 为机器码（{@code limit3 / not_published / not_owner / not_found}），
 * 不在服务端做文案，由前端按码做 i18n 本地化提示（spec §十决策 2）。</p>
 */
@Data
public class SeriesSkipVo {
    /**
     * 被跳过的文章 ID
     */
    private String articleId;

    /**
     * 跳过原因码：limit3（占用专栏已满 3）/ not_published（非已发布）/
     * not_owner（非操作者本人文章）/ not_found（文章不存在或已删除）
     */
    private String reasonCode;
}
