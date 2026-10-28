-- =============================================================================
-- 菜单整合迁移：学生端「个人中心 → 修改密码」下线，功能合并进「个人信息」页面
-- -----------------------------------------------------------------------------
-- 背景：
--   个人资料与密码管理整合到同一页面，学生不用在菜单间来回切换；
--   侧边「个人中心」只剩「个人信息」「关于系统」（旧库还有已提升为顶级的「消息通知」）。
-- 前端对应改动：
--   StudentProfileInfo.vue 底部新增「修改登录密码」分区（独立表单 / 独立校验 / 独立提交）；
--   路由 /student/profile/change-password 及页面 StudentChangePassword.vue 已移除。
-- 适用：已经执行过 migrate_add_rbac.sql 的旧库（新库直接跑最新的 migrate_add_rbac.sql 即可）。
-- 幂等：可重复执行。
-- =============================================================================

-- 1) 解除学生角色与「修改密码」菜单的关联（sys_role_menu）
DELETE FROM `sys_role_menu` WHERE `menu_id` = 41;

-- 2) 删除菜单本身：sys_menu id=41「修改密码」（layout=student，parent_id=39「个人中心」）
DELETE FROM `sys_menu` WHERE `id` = 41 AND `layout` = 'student';

-- 3) 整理「个人中心」子菜单排序号：个人信息(1) / 关于系统(2)
UPDATE `sys_menu` SET `sort_order` = 2 WHERE `id` = 43 AND `layout` = 'student';

-- 4) 自检：应只剩 40 / 43 两条「个人中心」子菜单
-- SELECT id, parent_id, menu_name, route_path, sort_order, visible, layout
--   FROM sys_menu WHERE parent_id = 39 ORDER BY sort_order, id;
