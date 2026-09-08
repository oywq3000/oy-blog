-- 专栏功能：article_series / article_series_item 建表归档 + 结构补齐
-- 已执行: 2026-09-08 于本地开发库（100.110.148.14，修复拆分为单语句 PREPARE 后重验两遍幂等通过；服务器执行记录见验收文档）
-- 数据库: oyblog
-- 背景: 两张表此前只存在于服务器库（DDL 未归档）。本期补归档并加 cover_url 列与唯一索引。
-- 部署顺序：先执行本 SQL，再发布新代码（两段式，旧代码不受影响，可重复执行）。
-- 执行: mysql -h<host> -uroot -p --default-character-set=utf8mb4 oyblog < article_series_migration.sql
-- 注意: 先 SET NAMES utf8mb4，防 GBK 控制台重定向导致中文注释乱码（dev 库既有列注释乱码即此成因）
SET NAMES utf8mb4;

-- 1. article_series 主表（幂等建表，仅对全新环境生效）
CREATE TABLE IF NOT EXISTS `article_series` (
    `id`          VARCHAR(32)  NOT NULL COMMENT '系列/专栏ID（UUID32）',
    `name`        VARCHAR(100) NOT NULL COMMENT '专栏名称',
    `description` TEXT         NULL COMMENT '专栏描述',
    `code`        VARCHAR(100) NULL COMMENT '唯一编码（保留字段）',
    `cover_url`   VARCHAR(500) NULL COMMENT '专栏封面URL',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章专栏';

-- 1.1 已有库补 cover_url 列（不存在才加；MySQL 8 无 ADD COLUMN IF NOT EXISTS）
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_series'
                      AND COLUMN_NAME = 'cover_url');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE article_series ADD COLUMN cover_url VARCHAR(500) NULL COMMENT ''专栏封面URL'' AFTER code',
    'SELECT ''cover_url column exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. article_series_item 关联表（幂等建表）
CREATE TABLE IF NOT EXISTS `article_series_item` (
    `id`         VARCHAR(32) NOT NULL COMMENT '关联ID（UUID32）',
    `series_id`  VARCHAR(32) NOT NULL COMMENT '专栏ID',
    `article_id` VARCHAR(32) NOT NULL COMMENT '文章ID',
    `sort_order` INT         NOT NULL DEFAULT 0 COMMENT '专栏内排序（越小越前）',
    PRIMARY KEY (`id`),
    KEY `idx_series_id` (`series_id`),
    KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章-专栏关联表';

-- 2.1 唯一索引（防并发双写；建前先清历史重复行，保留最小 id）
--     注意: MySQL 8 的 PREPARE 只接受单语句（多语句报 ERROR 1064），
--           DELETE 与 ALTER 必须拆成两个 PREPARE 块，同受 @idx_exists 守卫
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_series_item'
                      AND INDEX_NAME = 'uk_series_article');
-- 块 A：清历史重复行（保留最小 id）
SET @ddl := IF(@idx_exists = 0,
    'DELETE t1 FROM article_series_item t1 JOIN article_series_item t2
        ON t1.series_id = t2.series_id AND t1.article_id = t2.article_id AND t1.id > t2.id',
    'SELECT ''uk_series_article exists, skip dedupe''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 块 B：加唯一索引
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE article_series_item ADD UNIQUE KEY uk_series_article (series_id, article_id)',
    'SELECT ''uk_series_article exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
