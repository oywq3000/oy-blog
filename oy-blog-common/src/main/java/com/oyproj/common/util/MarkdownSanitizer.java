package com.oyproj.common.util;

/**
 * Markdown 文本清洗工具
 * 将 Markdown 内容转为纯文本，供 Elasticsearch IK 分词器索引。
 * 在生产者侧调用，确保 MQ 消息中已为清洗后的纯文本。
 */
public class MarkdownSanitizer {

    private MarkdownSanitizer() {
    }

    /**
     * 清洗 Markdown 文本，保留纯文本内容
     */
    public static String sanitize(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String text = markdown;

        // 1. 移除代码块（三个反引号包裹的内容）
        text = text.replaceAll("(?s)```[\\s\\S]*?```", " ");

        // 2. 移除内联代码
        text = text.replaceAll("`[^`]+`", " ");

        // 3. 移除图片 ![alt](url)
        text = text.replaceAll("!\\[[^]]*]\\([^)]*\\)", " ");

        // 4. 移除链接 [text](url)，保留链接文本
        text = text.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");

        // 5. 移除标题标记 #
        text = text.replaceAll("(?m)^#{1,6}\\s+", " ");

        // 6. 移除粗体/斜体标记
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("__([^_]+)__", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");

        // 7. 移除删除线
        text = text.replaceAll("~~([^~]+)~~", "$1");

        // 8. 移除引用标记 >
        text = text.replaceAll("(?m)^>\\s+", " ");

        // 9. 移除水平线
        text = text.replaceAll("(?m)^[-*_]{3,}\\s*$", " ");

        // 10. 移除无序列表标记
        text = text.replaceAll("(?m)^\\s*[-*+]\\s+", " ");

        // 11. 移除有序列表标记
        text = text.replaceAll("(?m)^\\s*\\d+\\.\\s+", " ");

        // 12. 移除 HTML 标签
        text = text.replaceAll("<[^>]*>", " ");

        // 13. 移除多余空白，保留单个空格
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }
}
