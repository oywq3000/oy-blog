-- 专栏归属用户：article_series 加 author_id 列（幂等）
-- 数据库: oyblog
-- 背景: 二期迭代 spec §九——每用户自己的专栏；author_id NULL = 历史/站长级专栏
-- 部署顺序：先执行本 SQL，再发布新代码（两段式，旧代码不受影响，可重复执行）。
-- 执行: mysql -h<host> -uroot -p --default-character-set=utf8mb4 oyblog < article_series_author_migration.sql
-- 注意: 先 SET NAMES utf8mb4，防 GBK 控制台重定向导致中文注释乱码（与 article_series_migration.sql 同口径）
SET NAMES utf8mb4;

-- author_id 不存在才加（MySQL 8 无 ADD COLUMN IF NOT EXISTS；PREPARE 单语句模式见 archive 迁移注释）
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_series'
                      AND COLUMN_NAME = 'author_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE article_series ADD COLUMN author_id VARCHAR(64) NULL COMMENT ''创建者用户ID（NULL=站长级专栏）'' AFTER cover_url',
    'SELECT ''author_id column exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
