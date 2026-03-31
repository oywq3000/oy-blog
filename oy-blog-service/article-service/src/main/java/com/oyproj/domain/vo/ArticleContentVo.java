package com.oyproj.domain.vo;

import lombok.Data;

/**
 * 文章内容VO
 */
@Data
public class ArticleContentVo {
    private String articleId;
    private String contentMd;
    private String contentHtml;
    private String wordsCount;
}
