#!/usr/bin/env python3
"""
oy-blog 文章自动上传脚本

读取本地 Markdown 文件，自动处理图片上传（本地路径 → MinIO URL），
然后通过 API 一步发布到博客。

用法:
    python upload_article.py <文章.md> [选项]

示例:
    python upload_article.py my-post.md
    python upload_article.py my-post.md --summary "这是一篇好文章" --tags Java,Spring
    python upload_article.py my-post.md --base-url http://your-server:8080

依赖:
    pip install requests
"""

import argparse
import mimetypes
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

import requests


# ============================================================
#  Configuration
# ============================================================

DEFAULT_BASE_URL = "http://localhost:8080"
CONFIG_FILE = ".oy-blog-upload.conf"


def load_config(config_path: str | None = None) -> dict:
    """从配置文件加载认证信息"""
    paths = [config_path] if config_path else [
        os.path.join(os.path.dirname(__file__) or ".", CONFIG_FILE),
        os.path.join(Path.home(), CONFIG_FILE),
    ]
    for p in paths:
        if p and os.path.exists(p):
            config = {}
            with open(p, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        key, val = line.split("=", 1)
                        config[key.strip()] = val.strip()
            return config
    return {}


# ============================================================
#  API Client
# ============================================================

class OyBlogClient:
    """oy-blog API 客户端"""

    def __init__(self, base_url: str, username: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.access_token: str | None = None
        self._session = requests.Session()

    def login(self) -> bool:
        """登录获取 token"""
        resp = self._session.post(
            f"{self.base_url}/user-service/auth/login",
            json={
                "username": self.username,
                "password": self.password,
                "ipAddress": "127.0.0.1",
            },
            timeout=30,
        )
        data = resp.json()
        if not data.get("isSuccess"):
            print(f"❌ 登录失败: {data.get('errMsg', '未知错误')}")
            return False
        token_info = data["data"]
        self.access_token = token_info["accessToken"]
        print(f"✅ 登录成功, userId={token_info.get('userId')}")
        return True

    def _auth_headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self.access_token}",
        } if self.access_token else {}

    def upload_image(self, file_path: str) -> str | None:
        """上传图片到 MinIO，返回远程 URL"""
        if not os.path.exists(file_path):
            print(f"  ⚠ 图片不存在: {file_path}")
            return None

        filename = os.path.basename(file_path)
        mime_type = mimetypes.guess_type(file_path)[0] or "image/png"

        with open(file_path, "rb") as f:
            resp = self._session.post(
                f"{self.base_url}/article-service/article/image",
                files={"file": (filename, f, mime_type)},
                headers=self._auth_headers(),
                timeout=120,
            )

        data = resp.json()
        if not data.get("isSuccess"):
            print(f"  ❌ 上传失败 {filename}: {data.get('errMsg')}")
            return None

        url = data["data"]["url"]
        print(f"  📤 {filename} → {url}")
        return url

    def publish(self, title: str, content_md: str, **kwargs) -> dict | None:
        """发布文章"""
        payload = {
            "title": title,
            "contentMd": content_md,
            "summary": kwargs.get("summary", ""),
            "coverUrl": kwargs.get("cover_url", ""),
            "tags": kwargs.get("tags", []),
            "allowComment": kwargs.get("allow_comment", 1),
        }
        # 如果有 id 则为更新
        if kwargs.get("article_id"):
            payload["id"] = kwargs["article_id"]

        resp = self._session.post(
            f"{self.base_url}/article-service/article/publish",
            json=payload,
            headers=self._auth_headers(),
            timeout=60,
        )
        data = resp.json()
        if not data.get("isSuccess"):
            print(f"❌ 发布失败: {data.get('errMsg', '未知错误')}")
            return None
        return data["data"]


# ============================================================
#  Markdown Processor
# ============================================================

# 匹配本地图片引用: ![alt](path)  但不匹配 http/https URL
LOCAL_IMAGE_RE = re.compile(
    r'!\[([^\]]*)\]\((?!https?://|data:)([^)]+)\)'
)

# 匹配标题
HEADING_RE = re.compile(r'^#\s+(.+)$', re.MULTILINE)


def find_local_images(content: str) -> list[tuple[str, str, str]]:
    """
    查找本地图片引用。
    返回 [(full_match, alt_text, local_path), ...]
    """
    return [
        (m.group(0), m.group(1), m.group(2))
        for m in LOCAL_IMAGE_RE.finditer(content)
    ]


def extract_title(md_path: str, content: str = "") -> str:
    """从文件路径提取博客标题：取文件名（不含扩展名），如 xx/name.md → name"""
    return Path(md_path).stem


def generate_summary(markdown: str | None, max_chars: int = 50) -> str:
    """
    从 Markdown 正文自动生成摘要：去格式后取前 max_chars 个字符。

    处理顺序：代码块 → 图片 → 链接 → 标题 → 粗体/斜体 → 列表标记 →
              删除线 → HTML标签 → 空白归一化 → 截断
    """
    if not markdown:
        return ""

    text = markdown

    # 1. 移除围栏代码块
    text = re.sub(r'(?s)```[\s\S]*?```', ' ', text)
    # 2. 移除内联代码
    text = re.sub(r'`[^`]+`', ' ', text)
    # 3. 移除图片 ![alt](url)
    text = re.sub(r'!\[[^\]]*\]\([^)]*\)', ' ', text)
    # 4. 移除链接 [text](url)，保留文本
    text = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', text)
    # 5. 移除标题标记 #
    text = re.sub(r'(?m)^#{1,6}\s+', ' ', text)
    # 6. 移除粗体/斜体标记
    text = re.sub(r'\*\*([^*]+)\*\*', r'\1', text)
    text = re.sub(r'\*([^*]+)\*', r'\1', text)
    text = re.sub(r'__([^_]+)__', r'\1', text)
    text = re.sub(r'_([^_]+)_', r'\1', text)
    # 7. 移除删除线
    text = re.sub(r'~~([^~]+)~~', r'\1', text)
    # 8. 移除引用标记
    text = re.sub(r'(?m)^>\s+', ' ', text)
    # 9. 移除水平线
    text = re.sub(r'(?m)^[-*_]{3,}\s*$', ' ', text)
    # 10. 移除无序列表标记
    text = re.sub(r'(?m)^\s*[-*+]\s+', ' ', text)
    # 11. 移除有序列表标记
    text = re.sub(r'(?m)^\s*\d+\.\s+', ' ', text)
    # 12. 移除 HTML 标签
    text = re.sub(r'<[^>]*>', ' ', text)
    # 13. 合并空白，trim
    text = re.sub(r'\s+', ' ', text).strip()

    return text[:max_chars]


def process_images(content: str, md_dir: str, client: OyBlogClient) -> str:
    """
    处理 Markdown 中的本地图片：
    1. 找到所有本地图片引用
    2. 上传到 MinIO
    3. 替换为远程 URL
    """
    images = find_local_images(content)
    if not images:
        print("  (无本地图片)")
        return content

    print(f"\n📷 发现 {len(images)} 张本地图片，开始上传...")
    result = content
    for full_match, alt_text, local_path in images:
        # 解析相对路径
        abs_path = os.path.normpath(os.path.join(md_dir, local_path))
        url = client.upload_image(abs_path)
        if url:
            replacement = f"![{alt_text}]({url})"
            result = result.replace(full_match, replacement, 1)
    return result


# ============================================================
#  Main
# ============================================================

def main():
    config = load_config()

    parser = argparse.ArgumentParser(
        description="oy-blog 文章自动上传脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python upload_article.py my-post.md
  python upload_article.py my-post.md -s "摘要" -t Java,Spring
  python upload_article.py my-post.md --username admin --password 123456

配置文件 (~/.oy-blog-upload.conf):
  USERNAME=admin
  PASSWORD=123456
  BASE_URL=http://localhost:8080
        """,
    )
    parser.add_argument("file", help="Markdown 文件路径")
    parser.add_argument("-s", "--summary", default="", help="文章摘要")
    parser.add_argument("-t", "--tags", default="", help="标签，逗号分隔")
    parser.add_argument("--cover", default="", help="封面图 URL")
    parser.add_argument("--title", default="", help="文章标题（默认从文件提取）")
    parser.add_argument("--article-id", default="", help="文章 ID（更新已有文章时使用）")
    parser.add_argument("--base-url", default=config.get("BASE_URL", DEFAULT_BASE_URL),
                        help="博客网关地址")
    parser.add_argument("--username", default=config.get("USERNAME", ""), help="用户名")
    parser.add_argument("--password", default=config.get("PASSWORD", ""), help="密码")

    args = parser.parse_args()

    # 验证文件存在
    if not os.path.exists(args.file):
        print(f"❌ 文件不存在: {args.file}")
        sys.exit(1)

    # 验证认证信息
    if not args.username or not args.password:
        print("❌ 请提供用户名和密码（--username / --password 或配置文件）")
        print(f"   配置文件位置: ~/{CONFIG_FILE} 或 ./{CONFIG_FILE}")
        sys.exit(1)

    # 读取 Markdown 内容
    with open(args.file, "r", encoding="utf-8") as f:
        content = f.read()

    # 提取标题（文件名 stem）
    title = args.title or extract_title(args.file)
    # 摘要：参数优先，否则自动从正文生成
    summary = args.summary if args.summary else generate_summary(content)
    md_dir = os.path.dirname(os.path.abspath(args.file)) or "."

    print(f"\n{'='*60}")
    print(f"📝 文章: {title}")
    print(f"📂 文件: {args.file}")
    print(f"🏷  标签: {args.tags or '(无)'}")
    print(f"{'='*60}")

    # 登录
    client = OyBlogClient(args.base_url, args.username, args.password)
    if not client.login():
        sys.exit(1)

    # 处理图片
    processed_content = process_images(content, md_dir, client)

    # 发布
    print(f"\n🚀 发布文章...")
    result = client.publish(
        title=title,
        content_md=processed_content,
        summary=summary,
        cover_url=args.cover,
        tags=[t.strip() for t in args.tags.split(",") if t.strip()],
        article_id=args.article_id,
    )

    if result:
        print(f"\n✅ 发布成功! articleId={result.get('articleId')}")
        print(f"   {args.base_url}/article-service/article/read/{result.get('articleId')}")
    else:
        print("\n❌ 发布失败")
        sys.exit(1)


if __name__ == "__main__":
    main()
