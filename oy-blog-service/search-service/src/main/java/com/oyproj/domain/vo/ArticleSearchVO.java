package com.oyproj.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oyproj.domain.entity.ArticleDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

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

    /** 命中的标签名列表（纯文本，已去除 em 标签）；用于搜索结果卡片强制展示并高亮命中标签 */
    private List<String> highlightTags;

    /** 高亮后的作者名片段（含 <em class="highlight"> 标签，前端 v-html 渲染） */
    private String highlightAuthorName;
}
