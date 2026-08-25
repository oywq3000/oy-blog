-- 评论审核迁移（先执行 SQL 再发布新代码；旧代码不受新增字段影响）
ALTER TABLE `comment`
    ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态 0=待审 1=通过 2=拒绝' AFTER `content`,
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是' AFTER `status`;

ALTER TABLE `comment_reply`
    ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态 0=待审 1=通过 2=拒绝' AFTER `content`,
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是' AFTER `status`;
