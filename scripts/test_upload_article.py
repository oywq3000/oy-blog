"""
upload_article.py 纯逻辑函数的单元测试

测试范围:
  - extract_title: 标题提取（文件名 stem）
  - generate_summary: 摘要自动生成（去格式前 N 字）
  - find_local_images: 本地图片引用解析
"""

import os
import sys
import tempfile
from pathlib import Path

import pytest

# 确保能 import upload_article（在 scripts/ 目录下）
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + "/scripts")

import upload_article as ua


# ================================================================
#  extract_title
# ================================================================

class TestExtractTitle:
    """标题应以文件名（不含扩展名）为准"""

    def test_uses_filename_stem(self):
        """文件名 name.md → 标题 'name'"""
        title = ua.extract_title("/some/path/my-article.md", "# Some Heading\n\ncontent")
        assert title == "my-article"

    def test_handles_path_with_spaces(self):
        title = ua.extract_title("/path/hello world 2024.md", "# Different Title")
        assert title == "hello world 2024"

    def test_handles_no_extension(self):
        title = ua.extract_title("/path/README", "content without heading")
        assert title == "README"

    def test_handles_multiple_dots(self):
        title = ua.extract_title("/path/v1.2.3-release.md", "# changelog")
        assert title == "v1.2.3-release"


# ================================================================
#  generate_summary
# ================================================================

class TestGenerateSummary:
    """未提供摘要时自动从正文提取前 N 个纯文字"""

    def test_returns_first_50_chars_of_plain_text(self):
        md = "这是一段" + "很" * 50 + "长的文字"
        summary = ua.generate_summary(md, max_chars=50)
        assert len(summary) <= 50

    def test_strips_markdown_headings(self):
        md = "# 第一章\n\n正文内容从这里开始。"
        summary = ua.generate_summary(md, max_chars=50)
        assert "#" not in summary, f"# 号应被去除: {summary}"
        # 标题文字本身是有效内容，应当保留（只是去掉了 # 标记）
        assert "第一章" in summary, f"标题文字应保留: {summary}"
        assert "正文内容从这里开始" in summary, f"正文应保留: {summary}"

    def test_strips_bold_and_italic(self):
        md = "**粗体**和*斜体*还有普通文字"
        summary = ua.generate_summary(md, max_chars=50)
        assert "**" not in summary
        assert "*" not in summary

    def test_strips_links(self):
        md = "[点击这里](https://example.com) 后面的文字"
        summary = ua.generate_summary(md, max_chars=50)
        assert "https://example.com" not in summary
        assert "点击这里" in summary

    def test_strips_images(self):
        md = "![图片](img.png) 正文内容"
        summary = ua.generate_summary(md, max_chars=50)
        assert "![" not in summary
        assert "img.png" not in summary
        assert "正文内容" in summary

    def test_strips_code_blocks(self):
        md = "```python\nprint('hello')\n```\n\n正文开始"
        summary = ua.generate_summary(md, max_chars=50)
        assert "```" not in summary
        assert "print" not in summary
        assert "正文开始" in summary

    def test_handles_null_input(self):
        assert ua.generate_summary(None) == ""
        assert ua.generate_summary("") == ""

    def test_shorter_than_max_chars(self):
        md = "短文字"
        summary = ua.generate_summary(md, max_chars=50)
        assert summary == "短文字"

    def test_custom_max_chars(self):
        md = "一二三四五六七八九十" * 10  # 100 chars
        summary = ua.generate_summary(md, max_chars=30)
        assert len(summary) == 30

    def test_collapses_whitespace(self):
        md = "hello   world\n\n\nnew  line"
        summary = ua.generate_summary(md, max_chars=50)
        # 多余空白应合并为单个空格
        assert "   " not in summary
        assert "\n" not in summary


# ================================================================
#  find_local_images
# ================================================================

class TestFindLocalImages:
    """解析 Markdown 中的本地图片引用"""

    def test_finds_relative_path(self):
        imgs = ua.find_local_images("![alt](./images/photo.png)")
        assert len(imgs) == 1
        full_match, alt, path = imgs[0]
        assert alt == "alt"
        assert path == "./images/photo.png"

    def test_finds_absolute_local_path(self):
        imgs = ua.find_local_images("![](/home/user/img.jpg)")
        assert len(imgs) == 1
        assert imgs[0][2] == "/home/user/img.jpg"

    def test_ignores_http_urls(self):
        imgs = ua.find_local_images("![web](https://cdn.com/img.png)")
        assert len(imgs) == 0

    def test_ignores_data_uris(self):
        imgs = ua.find_local_images("![inline](data:image/png;base64,abc)")
        assert len(imgs) == 0

    def test_finds_multiple_images(self):
        md = "![a](1.png)\n![b](2.jpg)\n![c](https://cdn.com/3.png)"
        imgs = ua.find_local_images(md)
        assert len(imgs) == 2  # 只找到两个本地图片
        paths = [p for _, _, p in imgs]
        assert "1.png" in paths
        assert "2.jpg" in paths

    def test_returns_empty_for_no_images(self):
        assert ua.find_local_images("plain text without images") == []
