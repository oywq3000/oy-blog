-- ============================================================
-- article_log（文章操作日志）索引调整脚本
-- 数据库: oyblog
-- 背景:
--   * article_log 承担"用户最近读过哪些文章"（浏览历史），由
--     ArticleLogDao.listHistoryLogs 查询: WHERE user_id=? AND action='view' ORDER BY view_at DESC
--   * 原索引 idx_article_log_user_article(user_id, article_id) 只能匹配 user_id，
--     ORDER BY view_at 需 filesort；补 (user_id, action, view_at) 覆盖过滤+排序
--   * appendView 的 upsert 查询 (user_id, article_id, action) 仍由既有
--     idx_article_log_user_article 覆盖，保留不动
-- 执行: mysql -h<host> -uroot -proot --default-character-set=utf8mb4 oyblog < article_log.sql
-- 注意: MySQL 8 不支持 ADD INDEX IF NOT EXISTS，本脚本一次性执行；重复执行会报
--       Duplicate key name，忽略即可
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE article_log
    ADD INDEX idx_article_log_user_action_time (user_id, action, view_at);
