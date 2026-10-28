-- =====================================================
-- 管理员端下线「作业管理」模块（仅保留教师端管理入口）
--
--   调整前：                          调整后：
--   教师端/管理员端                    教师端
--     作业管理 (id=32)                   作业管理
--       ├── 发布作业  /practice/papers     ├── 发布作业  /practice/papers
--       ├── 批改作业  /practice/review     ├── 批改作业  /practice/review
--       └── 作业记录  /practice/records    └── 作业记录  /practice/records
--                                     管理员端不再显示该模块
--
-- 说明：
--   1) 菜单本身仍保留（教师依旧使用），仅回收「管理员」角色（role_id = 3）的菜单授权；
--      sys_menu.visible 不能改为 0，否则教师端也会一起消失。
--   2) 登录返回的菜单来自 sys_role_menu 关联查询（无超管放行逻辑），
--      因此回收授权后管理员端侧边栏立即不再显示该模块。
--   3) 配合后端 PracticePaperController 收紧为「仅教师（role=1）可访问」，
--      管理员直接输入 URL 也会被 403 拦截。
--   4) 模块历史名称：自主练习管理 → 发布作业 → 作业管理，脚本对旧名一并兼容。
--   5) 幂等，可重复执行。
--
-- 执行：mysql -u root -p exam_system < migrate_remove_admin_practice_menu.sql
-- =====================================================

-- ---------- 1. 定位「作业管理」及其子菜单（兼容未执行更名脚本的老库） ----------
SET @practice_mgmt := (
  SELECT `id` FROM `sys_menu`
  WHERE `menu_name` IN ('作业管理', '发布作业', '自主练习管理') AND `layout` = 'admin'
  ORDER BY `id` LIMIT 1
);

-- ---------- 2. 回收管理员角色的授权（按分组 + 已知路由双保险定位） ----------
DELETE rm FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE rm.`role_id` = 3
  AND (
    m.`id` = @practice_mgmt
    OR m.`parent_id` = @practice_mgmt
    OR m.`route_path` IN ('/practice/papers', '/practice/review', '/practice/records')
  );

-- ---------- 3. 结果自检 ----------
-- 期望：管理员剩余授权 = 0；教师授权 = 3
SELECT
  (SELECT COUNT(*) FROM `sys_role_menu` rm
   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
   WHERE rm.`role_id` = 3
     AND (m.`id` = @practice_mgmt OR m.`parent_id` = @practice_mgmt
          OR m.`route_path` IN ('/practice/papers', '/practice/review', '/practice/records')))        AS `管理员剩余授权`,
  (SELECT COUNT(*) FROM `sys_role_menu` rm
   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
   WHERE rm.`role_id` = 2
     AND (m.`id` = @practice_mgmt OR m.`parent_id` = @practice_mgmt
          OR m.`route_path` IN ('/practice/papers', '/practice/review', '/practice/records')))        AS `教师授权`;

-- ---------- 4. 明细核对 ----------
SELECT m.`id`, m.`menu_name`, m.`route_path`, m.`layout`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 2 AND rm.`menu_id` = m.`id`), '有', '无') AS `教师授权`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 3 AND rm.`menu_id` = m.`id`), '有', '无') AS `管理员授权`
FROM `sys_menu` m
WHERE m.`id` = @practice_mgmt OR m.`parent_id` = @practice_mgmt
   OR m.`route_path` IN ('/practice/papers', '/practice/review', '/practice/records')
ORDER BY m.`parent_id`, m.`sort_order`;
