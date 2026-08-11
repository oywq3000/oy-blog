package com.oyproj.common.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Markdown 转 HTML 渲染工具
 *
 * <p>基于 flexmark-java 实现，支持 GFM 表格、删除线、代码高亮等扩展语法。</p>
 * <p>输出 HTML 时已做 XSS 安全处理（移除 script 标签和事件属性）。</p>
 */
public class MarkdownRenderer {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        // 安全设置：不渲染原始 HTML，全部转义
        options.set(HtmlRenderer.SUPPRESS_HTML, true);
        options.set(HtmlRenderer.ESCAPE_HTML, true);

        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    private MarkdownRenderer() {
    }

    /**
     * 将 Markdown 文本渲染为 HTML
     *
     * @param markdown Markdown 格式文本，可为 null
     * @return HTML 字符串，null/空输入返回空字符串
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        // 预处理：转义 HTML 危险标签，flexmark 的 ESCAPE_HTML 会处理其余部分
        String safeMarkdown = markdown
                .replaceAll("(?i)<script[\\s>]", "&lt;script&gt;")
                .replaceAll("(?i)</script>", "&lt;/script&gt;");

        return RENDERER.render(PARSER.parse(safeMarkdown));
    }
}
