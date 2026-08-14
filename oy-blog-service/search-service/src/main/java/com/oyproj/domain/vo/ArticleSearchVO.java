package com.oyproj.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oyproj.domain.entity.ArticleDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章搜索结果 VO，包含 ES 高亮片段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleSearchVO extends ArticleDocument {

    /** 高亮后的标题片段（ES highlight with preTags/postTags） */
    private String highlightTitle;

    /** 命中关键字的内容上下文片段（~200 字），用于替代原文摘要展示 */
    private String highlightSnippet;
}
