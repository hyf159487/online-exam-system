-- =====================================================
-- 管理员端下线「试卷管理」模块（仅保留教师端入口）
--
--   调整前：                              调整后：
--   教师端 / 管理员端                      教师端
--     试卷管理 (id=13)                       试卷管理
--       ├── 我的试卷   /papers                  ├── 我的试卷   /papers
--       └── 组卷模板   /paper-templates         └── 组卷模板   /paper-templates
--                                         管理员端不再显示该模块
--
-- 说明：
--   1) 菜单本身保留（教师继续使用），仅回收「管理员」角色（role_id = 3）的菜单授权；
--      不要改 sys_menu.visible，否则教师端会一起消失。
--   2) 登录返回的菜单来自 sys_role_menu 关联查询（无超管放行逻辑），
--      回收授权后管理员端侧边栏立即不再显示该模块。
--   3) 后端 /api/exam-paper/list、/api/exam-paper/{id} 有意保留「教师 + 管理员」可读：
--      管理员端的「考试管理」（新建考试选试卷）、「成绩管理」（按试卷筛选）、
--      「高频错题库」（错题加入试卷）仍依赖这两个接口。
--      管理员的试卷写操作入口由前端路由守卫拦截（meta.group = 'paper' 重定向首页）。
--   4) 幂等，可重复执行。
--
-- 执行：mysql -u root -p exam_system < migrate_remove_admin_paper_menu.sql
-- =====================================================

-- ---------- 1. 定位「试卷管理」及其子菜单 ----------
SET @paper_mgmt := (
  SELECT `id` FROM `sys_menu`
  WHERE `menu_name` = '试卷管理' AND `layout` = 'admin'
  ORDER BY `id` LIMIT 1
);

-- ---------- 2. 回收管理员角色的授权（父菜单 + 子菜单 + 已知路由三重定位） ----------
DELETE rm FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE rm.`role_id` = 3
  AND (
    m.`id` = @paper_mgmt
    OR m.`parent_id` = @paper_mgmt
    OR m.`route_path` IN ('/papers', '/paper-templates')
  );

-- ---------- 3. 结果自检 ----------
-- 期望：管理员剩余授权 = 0；教师授权 = 3
SELECT
  (SELECT COUNT(*) FROM `sys_role_menu` rm
   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
   WHERE rm.`role_id` = 3
     AND (m.`id` = @paper_mgmt OR m.`parent_id` = @paper_mgmt
          OR m.`route_path` IN ('/papers', '/paper-templates')))   AS `管理员剩余授权`,
  (SELECT COUNT(*) FROM `sys_role_menu` rm
   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
   WHERE rm.`role_id` = 2
     AND (m.`id` = @paper_mgmt OR m.`parent_id` = @paper_mgmt
          OR m.`route_path` IN ('/papers', '/paper-templates')))   AS `教师授权`;

-- ---------- 4. 明细核对 ----------
SELECT m.`id`, m.`menu_name`, m.`route_path`, m.`layout`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 2 AND rm.`menu_id` = m.`id`), '有', '无') AS `教师授权`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 3 AND rm.`menu_id` = m.`id`), '有', '无') AS `管理员授权`
FROM `sys_menu` m
WHERE m.`id` = @paper_mgmt OR m.`parent_id` = @paper_mgmt
   OR m.`route_path` IN ('/papers', '/paper-templates')
ORDER BY m.`parent_id`, m.`sort_order`;
