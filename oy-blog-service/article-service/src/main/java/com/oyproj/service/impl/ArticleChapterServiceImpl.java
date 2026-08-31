package com.oyproj.service.impl;

import com.oyproj.common.utils.UUIDUtils;
import com.oyproj.domain.entity.ArticleChapter;
import com.oyproj.dto.ArticleChapterDao;
import com.oyproj.service.ArticleChapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章章节目录解析与保存（原 ArticleBizServiceImpl 的 7 个私有方法原样迁出）。
 * 迁出原因：人工审核通过待审编辑后正文变化，需要与发布路径共用同一套章节重建逻辑。
 */
@Service
@RequiredArgsConstructor
public class ArticleChapterServiceImpl implements ArticleChapterService {

    private final ArticleChapterDao chapterDao;

    /** 解析正文标题并重建章节目录（原 parseAndSaveChapters 原样迁移，仅改私有→公开） */
    @Override
    public void rebuild(String articleId, String content) {
        // 1. 清理旧章节
        List<ArticleChapter> oldChapters = chapterDao.listByArticle(articleId);
        for (ArticleChapter ch : oldChapters) {
            chapterDao.removeById(ch.getId());
        }

        if (!StringUtils.hasText(content)) {
            return;
        }

        // 2. 预处理内容：保护代码块
        String processedContent = protectCodeBlocks(content);

        // 3. 解析新章节
        Pattern pattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(processedContent);

        ArrayDeque<ArticleChapter> stack = new ArrayDeque<>();
        HashMap<String, Integer> anchorCount = new HashMap<>();
        int order = 1;
        List<ArticleChapter> chapters = new ArrayList<>();

        while (matcher.find()) {
            String hashes = matcher.group(1);
            String rawTitle = matcher.group(2).trim();
            int level = hashes.length();
            int start = matcher.start();

            // 检查是否在代码块内（如果是占位符，则跳过）
            if (isInsideCodeBlock(matcher.group(0), processedContent, start)) {
                continue;
            }

            // 提取纯文本标题，去除HTML标签
            String title = extractPlainTextFromTitle(rawTitle);

            // 清理标题
            title = cleanTitleText(title);

            if (title.isEmpty()) {
                continue;
            }

            String base = slugify(title);
            Integer cnt = anchorCount.getOrDefault(base, 0);
            String anchor = cnt == 0 ? base : base + "-" + cnt;
            anchorCount.put(base, cnt + 1);

            // 处理层级栈
            while (!stack.isEmpty() && stack.peek().getLevel() >= level) {
                stack.pop();
            }

            String parentId = stack.isEmpty() ? null : stack.peek().getId();

            ArticleChapter chapter = ArticleChapter.builder()
                    .id(UUIDUtils.getId())
                    .articleId(articleId)
                    .chapterOrder(order++)
                    .level(level)
                    .title(title)
                    .anchor(anchor)
                    .parentId(parentId)
                    .startOffset(start)
                    .endOffset(matcher.end())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            chapters.add(chapter);
            stack.push(chapter);
        }

        // 批量保存章节并建立路径关系
        saveChaptersWithPaths(chapters);
    }

    /** 保护代码块，将代码块替换为占位符，避免被误解析（原样迁移） */
    private String protectCodeBlocks(String content) {
        if (content == null) {
            return "";
        }

        // 匹配代码块（三个反引号包裹的）
        Pattern codeBlockPattern = Pattern.compile("(?s)```[\\s\\S]*?```");
        Matcher codeBlockMatcher = codeBlockPattern.matcher(content);

        // 匹配内联代码（单个反引号包裹的）
        Pattern inlineCodePattern = Pattern.compile("`[^`]+`");

        // 先处理代码块
        StringBuilder result = new StringBuilder();
        List<String> codeBlocks = new ArrayList<>();

        while (codeBlockMatcher.find()) {
            String codeBlock = codeBlockMatcher.group(0);
            String placeholder = "###CODE_BLOCK_" + codeBlocks.size() + "###";
            codeBlocks.add(codeBlock);
            codeBlockMatcher.appendReplacement(result, placeholder);
        }
        codeBlockMatcher.appendTail(result);

        String processed = result.toString();

        // 再处理内联代码
        List<String> inlineCodes = new ArrayList<>();
        Matcher inlineMatcher = inlineCodePattern.matcher(processed);
        result = new StringBuilder();

        while (inlineMatcher.find()) {
            String inlineCode = inlineMatcher.group(0);
            String placeholder = "###INLINE_CODE_" + inlineCodes.size() + "###";
            inlineCodes.add(inlineCode);
            inlineMatcher.appendReplacement(result, placeholder);
        }
        inlineMatcher.appendTail(result);

        processed = result.toString();

        // 保存到临时存储，供恢复使用（占位符列表，与迁移前行为一致）
        ThreadLocal<List<String>> codeBlocksStore = new ThreadLocal<>();
        ThreadLocal<List<String>> inlineCodesStore = new ThreadLocal<>();
        codeBlocksStore.set(codeBlocks);
        inlineCodesStore.set(inlineCodes);

        return processed;
    }

    /** 检查是否在代码块内（原样迁移） */
    private boolean isInsideCodeBlock(String match, String processedContent, int start) {
        // 检查是否为代码块占位符
        if (match.contains("###CODE_BLOCK_") || match.contains("###INLINE_CODE_")) {
            return true;
        }

        // 检查前一行是否为空行（标题应该前面有空行或文档开头）
        if (start > 0) {
            // 查找前一个换行符
            int prevNewline = processedContent.lastIndexOf('\n', start - 1);
            if (prevNewline >= 0) {
                // 检查前一行内容
                String prevLine = processedContent.substring(prevNewline + 1, start).trim();
                // 如果前一行不是空行，且不是标题（以#开头），则可能是列表项或其他内容
                if (!prevLine.isEmpty() && !prevLine.startsWith("#")) {
                    // 检查是否是列表项（如1. xxx）
                    return prevLine.matches("^\\d+\\.\\s+.+") ||
                            prevLine.matches("^[-*+]\\s+.+") ||
                            prevLine.matches("^>\\s+.+");
                }
            }
        }
        return false;
    }

    /** 简化标题文本提取（原样迁移） */
    private String extractPlainTextFromTitle(String titleWithHtml) {
        if (titleWithHtml == null || titleWithHtml.isEmpty()) {
            return "";
        }

        // 移除所有HTML标签
        String plainText = titleWithHtml.replaceAll("<[^>]*>", "");

        // 移除代码占位符
        plainText = plainText.replaceAll("###CODE_BLOCK_\\d+###", "");
        plainText = plainText.replaceAll("###INLINE_CODE_\\d+###", "");

        // 清理多余空格
        plainText = plainText.replaceAll("\\s+", " ").trim();

        if (plainText.isEmpty()) {
            // 尝试从属性中提取文本
            Pattern altPattern = Pattern.compile("alt\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
            Matcher altMatcher = altPattern.matcher(titleWithHtml);
            if (altMatcher.find()) {
                plainText = altMatcher.group(1);
            }
        }

        return plainText;
    }

    /** 清理标题文本（原样迁移） */
    private String cleanTitleText(String title) {
        if (title == null) {
            return "";
        }

        // 移除Markdown格式标记
        String cleaned = title
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")   // 加粗
                .replaceAll("\\*([^*]+)\\*", "$1")         // 斜体
                .replaceAll("__([^_]+)__", "$1")          // 加粗（下划线）
                .replaceAll("_([^_]+)_", "$1")            // 斜体（下划线）
                .replaceAll("~~([^~]+)~~", "$1")          // 删除线
                .replaceAll("`([^`]+)`", "$1")            // 内联代码
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")  // 链接
                .replaceAll("!\\[[^]]+]\\([^)]+\\)", "");    // 图片

        // 清理空格
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        cleaned = cleaned.replaceAll("^[\\s,.;:!?]+|[\\s,.;:!?]+$", "");

        return cleaned;
    }

    /** 批量保存章节并建立路径关系（原样迁移） */
    private void saveChaptersWithPaths(List<ArticleChapter> chapters) {
        // 先保存所有章节
        for (ArticleChapter chapter : chapters) {
            chapterDao.save(chapter);
        }

        // 建立路径关系
        Map<String, ArticleChapter> chapterMap = new HashMap<>();
        for (ArticleChapter chapter : chapters) {
            chapterMap.put(chapter.getId(), chapter);
        }

        // 为每个章节计算路径
        for (ArticleChapter chapter : chapters) {
            StringBuilder path = new StringBuilder();

            if (chapter.getParentId() != null) {
                ArticleChapter parent = chapterMap.get(chapter.getParentId());
                if (parent != null && parent.getPath() != null) {
                    path.append(parent.getPath()).append("/");
                }
            }

            path.append(chapter.getId());
            chapter.setPath(path.toString());
            chapterDao.updateById(chapter);
        }
    }

    /** slugify函数（原样迁移） */
    private String slugify(String s) {
        if (s == null || s.isEmpty()) {
            return "section";
        }

        String t = s.toLowerCase();
        t = t.replaceAll("[^\\p{L}\\p{N}\\s-]", ""); // 只保留字母、数字、空格、连字符
        t = t.replaceAll("\\s+", "-");
        t = t.replaceAll("-+", "-");
        t = t.replaceAll("^-|-$", "");

        if (t.isEmpty()) {
            return "section";
        }

        if (t.length() > 1000) {
            t = t.substring(0, 1000);
        }

        return t;
    }
}
