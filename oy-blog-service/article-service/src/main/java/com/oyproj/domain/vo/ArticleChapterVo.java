package com.oyproj.domain.vo;

import lombok.Data;


/**
 * 文章章节VO
 */
@Data
public class ArticleChapterVo {
    private String id;
    private String articleId;
    private Integer chapterOrder;
    private Integer level;
    private String title;
    private String anchor;
    private String parentId;
    private String path;
}
