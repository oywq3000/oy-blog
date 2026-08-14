package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推荐问题（对齐前端 types/agent.ts 的 SuggestedQuestion）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedQuestionVo {

    /**
     * 图标标识
     */
    private String icon;

    /**
     * 问题文本
     */
    private String text;
}
