-- 管理员角色与授权种子（幂等，可重复执行）
-- 用法：在部署 admin-service 前于 oyblog 库执行；将下方博主的用户名替换为实际账号
-- 注意：role.code 在库中为小写 'admin'（与 BlogRole 枚举大小写无关，此为库内业务编码）

-- 1. 确保 admin 角色存在
INSERT INTO `role` (id, code, name, description, created_at, updated_at)
SELECT REPLACE(UUID(), '-', ''), 'admin', '管理员', '博客管理员', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE code = 'admin');

-- 2. 给博主授予 admin 角色（替换 'oywq' 为实际用户名）
INSERT INTO `user_role` (id, user_id, role_id, created_at)
SELECT REPLACE(UUID(), '-', ''), u.id, r.id, NOW()
FROM `user` u
JOIN `role` r ON r.code = 'admin'
WHERE u.username = 'oywq'
  AND NOT EXISTS (
      SELECT 1 FROM `user_role` ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
