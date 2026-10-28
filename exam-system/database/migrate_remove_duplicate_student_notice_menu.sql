-- =====================================================
-- 学生端「通知公告 / 消息通知」重复入口去重
--
-- 现象：
--   学生端侧边栏同时出现两个入口，且指向同一个页面：
--     1) 「通知公告」—— 顶部固定入口（StudentLayout.vue 硬编码）→ /student/messages
--     2) 「消息通知」—— 数据库菜单 id=42（layout=student）    → /student/messages
--
-- 处理：保留固定的「通知公告」，删除数据库中的重复菜单「消息通知」。
--   1) 「通知公告」由 StudentLayout.vue 固定渲染，并额外按
--      /student/messages 路由兜底去重，删除菜单不会让入口消失；
--   2) 菜单仅用于侧边栏渲染，后端无任何按 menu_id=42 的权限判断，
--      删除不影响接口访问与角色授权（学生通知数据仍来自 sys_notice / sys_user_notice）；
--   3) 老路径 /student/profile/notifications 在前端路由中保留重定向，不会白屏。
--
-- 执行顺序：请在本目录 migrate_student_message_page.sql 之后执行（幂等，可重复执行）。
-- 执行：mysql -u root -p exam_system < migrate_remove_duplicate_student_notice_menu.sql
-- =====================================================

-- ---------- 1. 定位重复菜单（按 id 42 + 路由双重匹配，兼容不同库的自增 id） ----------
SET @dup_notice_menu := (
  SELECT `id` FROM `sys_menu`
  WHERE `layout` = 'student'
    AND (`id` = 42 OR `route_path` IN ('/student/messages', '/student/profile/notifications'))
  ORDER BY (`id` = 42) DESC, `id`
  LIMIT 1
);

-- ---------- 2. 先解除角色授权，再删除菜单 ----------
DELETE FROM `sys_role_menu` WHERE `menu_id` = @dup_notice_menu;

DELETE FROM `sys_menu`
 WHERE `id` = @dup_notice_menu
   AND `layout` = 'student';

-- ---------- 3. 自检 ----------
-- 期望：第 1 个查询返回 0 行；第 2 个查询只剩「个人信息 / 关于系统」两条
SELECT `id`, `parent_id`, `menu_name`, `route_path`, `sort_order` AS `重复菜单（应为空）`
  FROM `sys_menu`
 WHERE `layout` = 'student'
   AND `route_path` IN ('/student/messages', '/student/profile/notifications');

SELECT `id`, `parent_id`, `menu_name`, `route_path`, `sort_order`
  FROM `sys_menu`
 WHERE `parent_id` = 39 AND `layout` = 'student'
 ORDER BY `sort_order`, `id`;
