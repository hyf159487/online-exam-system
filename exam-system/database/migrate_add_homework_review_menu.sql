-- =====================================================
-- 教师端「作业管理」新增子菜单：批改作业
--
--   作业管理 (id=32)
--     ├── 发布作业  /practice/papers    (sort 1)
--     ├── 批改作业  /practice/review    (sort 2)  ← 本脚本新增
--     └── 作业记录  /practice/records   (sort 3)
--
-- 说明：
--   1) 菜单 id 固定为 56（当前库最大 id 为 55），按 route_path=/practice/review 判重，
--      重复执行不会插入第二条菜单。
--   2) 仅授权教师角色（role_id = 2）：该模块归属教师端，管理员端已由
--      database/migrate_remove_admin_practice_menu.sql 下线，这里同步回收 role_id = 3 的授权。
--   3) 配套功能：practice_answer / practice_record 的批改字段由
--      database/migrate_add_practice_review.sql 增加。
--   4) 幂等，可重复执行。
--
-- 执行：mysql -u root -p exam_system < migrate_add_homework_review_menu.sql
-- =====================================================

-- ---------- 1. 定位「作业管理」分组（兼容未更名的库） ----------
SET @homework_mgmt := (
  SELECT `id` FROM `sys_menu`
  WHERE `layout` = 'admin' AND `parent_id` = 0
    AND `menu_name` IN ('作业管理', '发布作业', '自主练习管理')
  ORDER BY `id` LIMIT 1
);
SET @homework_mgmt := IFNULL(@homework_mgmt, (
  SELECT `parent_id` FROM `sys_menu` WHERE `route_path` = '/practice/papers' ORDER BY `id` LIMIT 1
));

-- ---------- 2. 新增「批改作业」子菜单 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`, `create_time`)
SELECT 56, @homework_mgmt, '批改作业', '/practice/review', 'Checked', 2, 1, 'admin', NOW()
  FROM DUAL
 WHERE @homework_mgmt IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `route_path` = '/practice/review');

-- 已存在时（例如 id 不是 56）也把属性归一到期望值
UPDATE `sys_menu`
   SET `parent_id` = IFNULL(@homework_mgmt, `parent_id`),
       `menu_name` = '批改作业',
       `icon`      = 'Checked',
       `sort_order` = 2,
       `visible`   = 1,
       `layout`    = 'admin'
 WHERE `route_path` = '/practice/review';

-- ---------- 3. 子菜单排序：发布作业 1 → 批改作业 2 → 作业记录 3 ----------
UPDATE `sys_menu` SET `sort_order` = 1 WHERE `route_path` = '/practice/papers';
UPDATE `sys_menu` SET `sort_order` = 3 WHERE `route_path` = '/practice/records';

-- ---------- 4. 授权：仅教师(role_id = 2) ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, m.`id` FROM `sys_menu` m
 WHERE m.`route_path` = '/practice/review'
   AND NOT EXISTS (
     SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 2 AND rm.`menu_id` = m.`id`
   );

-- 管理员端不再显示该模块（与 migrate_remove_admin_practice_menu.sql 保持一致）
DELETE rm FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
 WHERE rm.`role_id` = 3
   AND (m.`id` = @homework_mgmt OR m.`parent_id` = @homework_mgmt
        OR m.`route_path` IN ('/practice/papers', '/practice/review', '/practice/records'));

-- ---------- 5. 结果自检 ----------
SELECT m.`id`, m.`parent_id`, m.`menu_name`, m.`route_path`, m.`icon`, m.`sort_order`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                   WHERE rm.`role_id` = 2 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `教师角色`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                   WHERE rm.`role_id` = 3 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `管理员角色`
  FROM `sys_menu` m
 WHERE m.`id` = @homework_mgmt OR m.`parent_id` = @homework_mgmt
 ORDER BY m.`parent_id`, m.`sort_order`;
