-- =====================================================
-- 教师端模块更名：「自主练习管理」→「作业管理」
--
--   调整前：                            调整后：
--   自主练习管理 (id=32)                作业管理 (id=32)
--     ├── 练习卷列表  /practice/papers    ├── 发布作业  /practice/papers
--     └── 练习记录查看 /practice/records  ├── 批改作业  /practice/review  ← 新增子菜单
--                                         └── 作业记录  /practice/records
--
-- 说明：
--   1) 只改菜单显示名称（sys_menu.menu_name），复用原菜单行；
--      路由路径、页面组件、角色授权关系(sys_role_menu)全部不变，因此不会产生重复入口或丢权限。
--   2) 侧边栏文字来自登录接口返回的菜单树，执行后教师端刷新页面即可看到「作业管理」。
--   3) 兼容历史上已执行过更名的库（发布作业 / 作业列表 / 作业记录 等中间名称都会被归一到最终名称）。
--   4) 「批改作业」子菜单由 database/migrate_add_homework_review_menu.sql 新增，两个脚本都可重复执行。
--   5) 幂等，可重复执行。
--
-- 执行：mysql -u root -p exam_system < migrate_rename_practice_module_to_homework.sql
-- =====================================================

-- ---------- 1. 定位教师端「作业管理」分组（兼容未更名 / 已更名的库） ----------
SET @homework_mgmt := (
  SELECT `id` FROM `sys_menu`
  WHERE `layout` = 'admin'
    AND `menu_name` IN ('自主练习管理', '发布作业', '作业管理')
    AND `parent_id` = 0
  ORDER BY `id` LIMIT 1
);

-- ---------- 2. 一级菜单更名 ----------
UPDATE `sys_menu`
   SET `menu_name` = '作业管理'
 WHERE `id` = @homework_mgmt
   AND `menu_name` <> '作业管理';

-- ---------- 3. 若上面没定位到（parent_id 非 0 等异常数据），兜底按子菜单反查 ----------
SET @homework_mgmt := IFNULL(@homework_mgmt, (
  SELECT `parent_id` FROM `sys_menu`
  WHERE `route_path` = '/practice/papers' ORDER BY `id` LIMIT 1
));
UPDATE `sys_menu`
   SET `menu_name` = '作业管理'
 WHERE `id` = @homework_mgmt
   AND `menu_name` IN ('自主练习管理', '发布作业');

-- ---------- 4. 子菜单更名：练习卷列表/作业列表 → 发布作业；练习记录查看 → 作业记录 ----------
UPDATE `sys_menu` SET `menu_name` = '发布作业'
 WHERE `route_path` = '/practice/papers'
   AND `menu_name` IN ('练习卷列表', '作业列表', '发布作业');

UPDATE `sys_menu` SET `menu_name` = '作业记录'
 WHERE `route_path` = '/practice/records'
   AND `menu_name` IN ('练习记录查看', '作业记录');

-- ---------- 5. 子菜单排序：发布作业 1 → 批改作业 2 → 作业记录 3 ----------
UPDATE `sys_menu` SET `sort_order` = 1 WHERE `route_path` = '/practice/papers';
UPDATE `sys_menu` SET `sort_order` = 3 WHERE `route_path` = '/practice/records';

-- ---------- 6. 结果自检 ----------
-- 期望：一级菜单 = 作业管理；子菜单 = 发布作业 / 批改作业 / 作业记录；教师(role_id=2)均已授权
SELECT m.`id`, m.`parent_id`, m.`menu_name`, m.`route_path`, m.`icon`, m.`sort_order`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                   WHERE rm.`role_id` = 2 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `教师角色`
  FROM `sys_menu` m
 WHERE m.`id` = @homework_mgmt OR m.`parent_id` = @homework_mgmt
 ORDER BY m.`parent_id`, m.`sort_order`;
