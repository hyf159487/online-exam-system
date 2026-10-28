-- =====================================================
-- 教师端「我的课程」菜单
-- 位置：课程管理 -> 学习资料管理 / 我的课程 / 课程申请审核
-- 只分配给教师角色（role_id=2）；管理员用「课程列表」即可
-- 幂等：可重复执行，不会产生重复菜单或重复授权
-- =====================================================

SET @course_mgmt := (SELECT `id` FROM `sys_menu`
                     WHERE `menu_name` = '课程管理' AND `layout` = 'admin'
                     ORDER BY `id` LIMIT 1);

-- ---------- 1. 新增菜单（不存在时才插入） ----------
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT @course_mgmt, '我的课程', '/my-courses', 'Files', 4, 1, 'admin'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `menu_name` = '我的课程' AND `layout` = 'admin'
);

-- ---------- 2. 位置与顺序修正 ----------
-- 课程管理下教师端三个菜单固定为：我的课程(3) → 学习资料管理(4) → 课程申请审核(5)
-- 管理员端的「课程列表」(1)、「知识点管理」(2) 不受影响
UPDATE `sys_menu`
SET `parent_id` = @course_mgmt, `sort_order` = 3, `route_path` = '/my-courses', `visible` = 1
WHERE `menu_name` = '我的课程' AND `layout` = 'admin';

UPDATE `sys_menu`
SET `parent_id` = @course_mgmt, `sort_order` = 4
WHERE `menu_name` = '学习资料管理' AND `layout` = 'admin';

UPDATE `sys_menu`
SET `parent_id` = @course_mgmt, `sort_order` = 5
WHERE `menu_name` = '课程申请审核' AND `layout` = 'admin';

SET @my_course_menu := (SELECT `id` FROM `sys_menu`
                        WHERE `menu_name` = '我的课程' AND `layout` = 'admin'
                        ORDER BY `id` LIMIT 1);

-- ---------- 3. 授权给教师角色（role_id=2） ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, @my_course_menu FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` = @my_course_menu
);

-- ---------- 4. 幂等清理：若曾误分配给管理员（role_id=3），移除 ----------
DELETE `srm` FROM `sys_role_menu` srm
JOIN `sys_menu` m ON m.`id` = srm.`menu_id`
WHERE m.`menu_name` = '我的课程' AND m.`layout` = 'admin' AND srm.`role_id` = 3;
