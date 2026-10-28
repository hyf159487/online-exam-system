-- =====================================================
-- 学生端「课程学习」子菜单更名：练习卷 → 课程作业；课程资料 → 资料下载
--
--   调整前：                                  调整后：
--   课程学习 (分组)                            课程学习 (分组)
--     ├── 练习卷    /student/practice/papers     ├── 课程作业  /student/practice/papers
--     ├── 自由刷题  /student/practice/free       ├── 自由刷题  /student/practice/free
--     └── 课程资料  /student/course/resources    └── 资料下载  /student/course/resources
--
-- 说明：
--   1) 只改菜单显示名称（sys_menu.menu_name）与顺序（sort_order），复用原菜单行；
--      路由路径、页面组件、角色授权关系（sys_role_menu）全部不变，不会产生重复入口或丢权限。
--   2) 侧边栏文字来自登录接口返回的菜单树，执行后学生端刷新页面即可看到新名称。
--      前端 StudentLayout.vue 亦做了显示名兼容映射，未执行本脚本时界面同样显示「课程作业」「资料下载」。
--   3) 按 route_path 定位，兼容菜单挂在「课程学习」下或仍挂在「自主练习」下的两种历史结构。
--   4) 幂等，可重复执行。
--
-- 执行：mysql -u root -p exam_system < migrate_rename_student_course_children.sql
-- =====================================================

-- ---------- 1. 子菜单更名（按路由定位，新旧名称都兼容） ----------
UPDATE `sys_menu` SET `menu_name` = '课程作业'
 WHERE `route_path` = '/student/practice/papers'
   AND (`menu_name` IS NULL OR `menu_name` <> '课程作业');

UPDATE `sys_menu` SET `menu_name` = '资料下载'
 WHERE `route_path` = '/student/course/resources'
   AND (`menu_name` IS NULL OR `menu_name` <> '资料下载');

-- ---------- 2. 顺序对齐前端：课程作业 1 → 自由刷题 2 → 资料下载 3 ----------
UPDATE `sys_menu` SET `sort_order` = 1 WHERE `route_path` = '/student/practice/papers';
UPDATE `sys_menu` SET `sort_order` = 2 WHERE `route_path` = '/student/practice/free';
UPDATE `sys_menu` SET `sort_order` = 3 WHERE `route_path` = '/student/course/resources';

-- ---------- 3. 结果自检 ----------
-- 期望：课程学习下依次为 课程作业 / 自由刷题 / 资料下载，且学生角色(role_id=1)均已授权
SELECT m.`id`, m.`parent_id`, p.`menu_name` AS `父菜单`, m.`menu_name`, m.`route_path`, m.`icon`, m.`sort_order`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `学生角色`
  FROM `sys_menu` m
  LEFT JOIN `sys_menu` p ON p.`id` = m.`parent_id`
 WHERE m.`route_path` IN ('/student/practice/papers', '/student/practice/free', '/student/course/resources')
 ORDER BY m.`parent_id`, m.`sort_order`;
