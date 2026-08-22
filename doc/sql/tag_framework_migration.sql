-- ============================================================
-- tag / article_tag 标签框架改造迁移脚本
-- 数据库: oyblog
-- 背景:
--   * 标签模型简化：仅 name 作唯一标识，删除 code 列
--     （现状不变式：所有行 code == name，可直接迁移）
--   * 新增 is_common 区分「常用标签(预置,1)」与「自创标签(自动创建,0)」
--   * article_tag 补唯一索引 (article_id, tag_id) 防重复关联；
--     补 tag_id 普通索引支撑「按标签统计文章数」
-- 执行: mysql -h<host> -uroot -proot --default-character-set=utf8mb4 oyblog < tag_framework_migration.sql
-- 注意: MySQL 8 不支持 ADD COLUMN / ADD INDEX IF NOT EXISTS；
--       本脚本用 INFORMATION_SCHEMA 判断存在性，可重复执行（幂等）
-- 分两段执行：
--   第一段（发布新代码前）: 加列 / 加索引 / code 改可空 —— 对旧代码零影响
--   第二段（新代码发布后）: 删 code 列 + 常用标签种子
--   回滚: 若需回退旧代码而 code 已删，执行
--     ALTER TABLE tag ADD COLUMN code VARCHAR(255) NULL;
--     UPDATE tag SET code = name WHERE code IS NULL;
-- ============================================================
SET NAMES utf8mb4;

-- ============================================================
-- 第一段：结构扩展（可重复执行）
-- ============================================================

-- 1.1 tag 加 is_common 列（不存在才加）
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tag'
                      AND COLUMN_NAME = 'is_common');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE tag ADD COLUMN is_common TINYINT NOT NULL DEFAULT 0 COMMENT ''1=常用(预置) 0=自创'' AFTER code',
    'SELECT ''is_common column exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 tag.name 唯一索引（建索引前先删同名重复行，保留最小 id）
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tag'
                      AND INDEX_NAME = 'uk_tag_name');
SET @ddl := IF(@idx_exists = 0,
    'DELETE t1 FROM tag t1 JOIN tag t2 ON t1.name = t2.name AND t1.id > t2.id; ALTER TABLE tag ADD UNIQUE KEY uk_tag_name (name)',
    'SELECT ''uk_tag_name exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 article_tag 唯一索引 (article_id, tag_id)（先删重复关联行）
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_tag'
                      AND INDEX_NAME = 'uk_article_tag');
SET @ddl := IF(@idx_exists = 0,
    'DELETE at1 FROM article_tag at1 JOIN article_tag at2 ON at1.article_id = at2.article_id AND at1.tag_id = at2.tag_id AND at1.id > at2.id; ALTER TABLE article_tag ADD UNIQUE KEY uk_article_tag (article_id, tag_id)',
    'SELECT ''uk_article_tag exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 article_tag.tag_id 普通索引（统计查询支撑）
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_tag'
                      AND INDEX_NAME = 'idx_article_tag_tag_id');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE article_tag ADD KEY idx_article_tag_tag_id (tag_id)',
    'SELECT ''idx_article_tag_tag_id exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.5 code 列改可空（过渡关键：旧代码仍写该列不受影响；新代码不写该列，NULL 可接受）
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tag'
                      AND COLUMN_NAME = 'code' AND IS_NULLABLE = 'NO');
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE tag MODIFY COLUMN code VARCHAR(128) NULL',
    'SELECT ''code already nullable or not exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 第二段：删列 + 常用标签种子（新代码发布后执行）
-- ============================================================

-- 2.1 删除 code 列（执行前确认无旧版本实例在运行）
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tag'
                      AND COLUMN_NAME = 'code');
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE tag DROP COLUMN code',
    'SELECT ''code column not exists, skip''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 常用标签种子（纯预置维护：后续增删改由管理员直接改库）
--     两段式：INSERT IGNORE 新建缺失标签（固定确定性 id，重复执行幂等）；
--             UPDATE 把同名旧自创标签升级为常用（防 INSERT IGNORE 因 name 冲突跳过）
INSERT IGNORE INTO tag (id, name, is_common, created_at, updated_at) VALUES
('1000000000000000001', 'Java',          1, NOW(), NOW()),
('1000000000000000002', 'Spring Boot',   1, NOW(), NOW()),
('1000000000000000003', 'Spring Cloud',  1, NOW(), NOW()),
('1000000000000000004', 'MyBatis',       1, NOW(), NOW()),
('1000000000000000005', 'MySQL',         1, NOW(), NOW()),
('1000000000000000006', 'Redis',         1, NOW(), NOW()),
('1000000000000000007', 'RabbitMQ',      1, NOW(), NOW()),
('1000000000000000008', 'Elasticsearch', 1, NOW(), NOW()),
('1000000000000000009', 'Docker',        1, NOW(), NOW()),
('1000000000000000010', 'Kubernetes',    1, NOW(), NOW()),
('1000000000000000011', 'Linux',         1, NOW(), NOW()),
('1000000000000000012', 'Vue 3',         1, NOW(), NOW()),
('1000000000000000013', 'TypeScript',    1, NOW(), NOW()),
('1000000000000000014', 'JavaScript',    1, NOW(), NOW()),
('1000000000000000015', '微服务',         1, NOW(), NOW()),
('1000000000000000016', '架构设计',       1, NOW(), NOW()),
('1000000000000000017', '数据库',         1, NOW(), NOW()),
('1000000000000000018', '性能优化',       1, NOW(), NOW()),
('1000000000000000019', '设计模式',       1, NOW(), NOW()),
('1000000000000000020', '算法',           1, NOW(), NOW());

UPDATE tag SET is_common = 1, updated_at = NOW()
WHERE name IN ('Java','Spring Boot','Spring Cloud','MyBatis','MySQL','Redis','RabbitMQ',
               'Elasticsearch','Docker','Kubernetes','Linux','Vue 3','TypeScript','JavaScript',
               '微服务','架构设计','数据库','性能优化','设计模式','算法');

-- ============================================================
-- 验证（执行后自查，与 Java 侧 listCommonTagStats 口径一致）
-- ============================================================
-- SELECT id, name, is_common FROM tag ORDER BY is_common DESC, name;
-- SELECT t.name, COUNT(CASE WHEN a.id IS NOT NULL THEN 1 END) AS article_count
--   FROM tag t
--   LEFT JOIN article_tag at ON at.tag_id = t.id
--   LEFT JOIN article a ON a.id = at.article_id AND a.status = 'published' AND a.deleted_at IS NULL
--  WHERE t.is_common = 1
--  GROUP BY t.id, t.name
--  ORDER BY article_count DESC, t.name ASC;
