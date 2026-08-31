package com.oyproj.service;

/**
 * 文章章节目录解析与保存（原 ArticleBizServiceImpl 的 7 个私有方法原样迁出）。
 * 迁出原因：人工审核通过待审编辑后正文变化，需要与发布路径共用同一套章节重建逻辑。
 */
public interface ArticleChapterService {

    /** 解析正文标题并重建章节目录（原 parseAndSaveChapters 原样迁移，仅改私有→公开） */
    void rebuild(String articleId, String content);
}
