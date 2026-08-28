-- 文章 AI 审核迁移：待生效编辑区
-- 部署顺序：先执行本 SQL，再发布新代码（旧代码不受新增表影响，两段式）
-- 用途：已发布文章的编辑被 AI 判"有歧义"时，新版本暂存于此等待人工审核；
--       人工通过 → 替换进 article/article_content；人工驳回 → 整行删除。
CREATE TABLE IF NOT EXISTS `article_pending_content` (
  `article_id`           VARCHAR(64)  NOT NULL COMMENT '文章ID（复用 article.id，一篇最多一份待审编辑）',
  `pending_title`        VARCHAR(255) NOT NULL COMMENT '待生效标题',
  `pending_summary`      VARCHAR(500) DEFAULT NULL COMMENT '待生效摘要',
  `pending_content_md`   LONGTEXT COMMENT '待生效 Markdown 正文',
  `pending_content_html` LONGTEXT COMMENT '待生效 HTML 正文',
  `review_reason`        VARCHAR(500) DEFAULT NULL COMMENT 'AI 转人工理由',
  `created_at`           DATETIME NOT NULL COMMENT '创建时间',
  `updated_at`           DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章待生效编辑（已发布文章编辑被判歧义时暂存）';
