-- ============================================================
-- category / article_category 彻底移除迁移脚本
-- 数据库: oyblog
-- 背景:
--   * 分类是「只写不读」死链路：无查询接口、文章 VO 不返回、
--     前端零入口零展示零输入；标签系统已完全覆盖其功能
--   * 两表为手工建表（doc/sql 下无建表脚本），数据量极小
-- 前置（重要）:
--   必须先发布新代码（不再访问两表）并确认无旧版本实例运行，
--   否则旧代码 publish 时 DELETE FROM article_category 会报错回滚
-- 备份:
--   mysqldump -h<host> -uroot -p oyblog category article_category > category_backup.sql
-- 执行:
--   mysql -h<host> -uroot -proot --default-character-set=utf8mb4 oyblog < category_removal_migration.sql
-- 回滚:
--   若需回退旧代码，先恢复备份: mysql -h<host> -uroot -proot oyblog < category_backup.sql
-- 幂等: 用 INFORMATION_SCHEMA 判断存在性，可重复执行
-- ============================================================
SET NAMES utf8mb4;

-- 1. 删除 article_category（先删子表，防外键依赖）
SET @tbl_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_category');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE article_category',
               'SELECT ''article_category not exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 删除 category
SET @tbl_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category');
SET @ddl := IF(@tbl_exists = 1, 'DROP TABLE category',
               'SELECT ''category not exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证
-- SELECT COUNT(*) FROM article_category;  -- 预期报错（表不存在）即删除成功
-- SELECT COUNT(*) FROM category;          -- 预期报错（表不存在）即删除成功
