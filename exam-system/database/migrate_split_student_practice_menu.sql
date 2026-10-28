-- =====================================================
-- 学生端「自主练习」菜单拆分为两个子菜单
--
--   自主练习（分组，无路由）
--     ├── 练习卷    /student/practice/papers   查看教师发布的成套练习试卷，开始做题
--     └── 自由刷题  /student/practice/free     自选学科/题型，随机抽题练习
--
-- 说明：
--   1) 原「自主练习」叶子菜单（route_path = /student/practice）改为分组节点；
--      前端路由保留 /student/practice → /student/practice/papers 重定向，
--      因此本脚本没执行时点击菜单也不会白屏。
--   2) 学生端原先「自主练习」页内的「课程学习资料」Tab 已下线，
--      学习资料统一走 课程学习 ▸ 课程资料（/student/course/resources）。
--   3) 只影响学生角色（role_id=1）；教师端「自主练习管理」（练习卷列表/练习记录查看）不受影响。
--
-- 幂等：可重复执行，不会产生重复菜单或重复授权。
-- 执行：mysql -u root -p exam_system < migrate_split_student_practice_menu.sql
-- =====================================================

-- ---------- 1. 定位原「自主练习」叶子菜单（优先按名称，兜底按老路由） ----------
SET @practice_menu := IFNULL(
  (SELECT `id` FROM `sys_menu`
   WHERE `menu_name` = '自主练习' AND `layout` = 'student' ORDER BY `id` LIMIT 1),
  (SELECT `id` FROM `sys_menu`
   WHERE `route_path` = '/student/practice' AND `layout` = 'student' ORDER BY `id` LIMIT 1)
);

-- ---------- 2. 改为分组节点：去掉路由（点击只展开子菜单，不再直接跳转） ----------
UPDATE `sys_menu`
SET `route_path` = NULL, `icon` = 'EditPen', `visible` = 1
WHERE `id` = @practice_menu;

-- ---------- 3. 新增两个子菜单（已存在则跳过插入，仅在下面统一纠正位置/顺序） ----------
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT @practice_menu, '练习卷', '/student/practice/papers', 'Files', 1, 1, 'student'
FROM DUAL
WHERE @practice_menu IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_menu`
    WHERE `layout` = 'student' AND `parent_id` = @practice_menu AND `menu_name` = '练习卷'
  );

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT @practice_menu, '自由刷题', '/student/practice/free', 'MagicStick', 2, 1, 'student'
FROM DUAL
WHERE @practice_menu IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_menu`
    WHERE `layout` = 'student' AND `parent_id` = @practice_menu AND `menu_name` = '自由刷题'
  );

-- ---------- 4. 位置/顺序纠正（顺序：练习卷 → 自由刷题） ----------
UPDATE `sys_menu`
SET `parent_id` = @practice_menu, `route_path` = '/student/practice/papers',
    `sort_order` = 1, `visible` = 1
WHERE `layout` = 'student' AND `parent_id` = @practice_menu AND `menu_name` = '练习卷';

UPDATE `sys_menu`
SET `parent_id` = @practice_menu, `route_path` = '/student/practice/free',
    `sort_order` = 2, `visible` = 1
WHERE `layout` = 'student' AND `parent_id` = @practice_menu AND `menu_name` = '自由刷题';

-- ---------- 5. 授权给学生角色（role_id=1）：分组 + 两个子菜单 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, @practice_menu FROM DUAL
WHERE @practice_menu IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = @practice_menu
  );

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`id` FROM `sys_menu` m
WHERE m.`layout` = 'student' AND m.`parent_id` = @practice_menu
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`
  );

-- ---------- 6. 结果自检（一行汇总，看「结果」页即可） ----------
-- 期望：分组已生效=1、子菜单数量=2、已授权数=3，三项都达标即为成功
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
   WHERE `id` = @practice_menu AND `route_path` IS NULL)                        AS `分组已生效`,
  (SELECT COUNT(*) FROM `sys_menu`
   WHERE `layout` = 'student' AND `parent_id` = @practice_menu)                 AS `子菜单数量`,
  (SELECT COUNT(*) FROM `sys_role_menu`
   WHERE `role_id` = 1
     AND `menu_id` IN (SELECT `id` FROM `sys_menu`
                       WHERE `id` = @practice_menu OR `parent_id` = @practice_menu)) AS `已授权数`;

-- ---------- 7. 明细核对（父节点 + 两个子菜单 + 授权状态） ----------
SELECT m.`id`, m.`parent_id`, m.`menu_name`, m.`route_path`, m.`icon`, m.`sort_order`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `学生角色`
FROM `sys_menu` m
WHERE m.`layout` = 'student' AND (m.`id` = @practice_menu OR m.`parent_id` = @practice_menu)
ORDER BY m.`parent_id`, m.`sort_order`;
