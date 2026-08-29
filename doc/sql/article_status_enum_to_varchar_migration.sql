-- 文章状态列 ENUM → VARCHAR 迁移（文章 AI 审核功能的必要前置）
-- 背景：article 表建表时 status/review_status 是 ENUM（DDL 不在仓库，历史遗留），
--       审核功能的状态字面量（pending_review/rejected/ai_reviewing 与 manual/exempt/ai_reviewing）
--       不在枚举内，写入即报 "Data truncated for column ..."。
-- 部署顺序：先执行本 SQL，再发布新代码。
-- 已在服务器（100.110.148.14）执行于 2026-08-29。
ALTER TABLE `article`
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'draft'
    COMMENT '文章状态：draft/ai_reviewing/published/pending_review/rejected',
  MODIFY COLUMN `review_status` VARCHAR(32) NOT NULL DEFAULT 'pending'
    COMMENT '审核状态：pending/approved/rejected/manual/exempt/ai_reviewing';
