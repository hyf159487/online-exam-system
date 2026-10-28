-- =====================================================
-- 学生端菜单调整：「自主练习」的两个子菜单并入「课程学习」
--
--   调整前：                          调整后：
--   课程学习 (分组)                    课程学习 (分组)
--     ├── 加入课程  /student/course/join      ├── 加入课程  /student/course/join
--     └── 课程资料  /student/course/resources └── 课程资料  /student/course/resources
--   自主练习 (分组)                            ├── 练习卷    /student/practice/papers
--     ├── 练习卷    /student/practice/papers   └── 自由刷题  /student/practice/free
--     └── 自由刷题  /student/practice/free
--
-- 说明：
--   1) 仅移动菜单层级与顺序，路由路径不变，前端 /student/practice/* 页面无需改动。
--   2) 一级「自主练习」分组菜单连同其角色授权一并删除（学生端不再有该入口）。
--   3) 教师端「自主练习管理」（练习卷列表 / 练习记录查看）为 admin 布局，不受影响。
--   4) 只处理学生角色（role_id = 1）与被移动的两个子菜单，幂等，可重复执行。
--
-- 执行：mysql -u root -p exam_system < migrate_merge_practice_into_course_menu.sql
-- =====================================================

-- ---------- 1. 定位「课程学习」分组（优先学生布局，兜底任意布局） ----------
SET @course_menu := IFNULL(
  (SELECT `id` FROM `sys_menu`
   WHERE `menu_name` = '课程学习' AND `layout` = 'student' ORDER BY `id` LIMIT 1),
  (SELECT `id` FROM `sys_menu`
   WHERE `menu_name` = '课程学习' ORDER BY `id` LIMIT 1)
);

-- ---------- 2. 定位原一级「自主练习」分组 ----------
SET @practice_menu := IFNULL(
  (SELECT `id` FROM `sys_menu`
   WHERE `menu_name` = '自主练习' AND `layout` = 'student' ORDER BY `id` LIMIT 1),
  (SELECT `id` FROM `sys_menu`
   WHERE `menu_name` = '自主练习' AND `route_path` IS NULL ORDER BY `id` LIMIT 1)
);

-- ---------- 3. 把「练习卷」「自由刷题」挂到「课程学习」下（顺序 3、4，排在课程资料之后） ----------
UPDATE `sys_menu`
SET `parent_id` = @course_menu, `icon` = 'Files', `sort_order` = 3, `visible` = 1
WHERE `layout` = 'student' AND `parent_id` = @practice_menu AND `menu_name` = '练习卷'
  AND @course_menu IS NOT NULL;

UPDATE `sys_menu`
SET `parent_id` = @course_menu, `icon` = 'MagicStick', `sort_order` = 4, `visible` = 1
WHERE `layout` = 'student' AND `parent_id` = @practice_menu AND `menu_name` = '自由刷题'
  AND @course_menu IS NOT NULL;

-- 兜底：菜单树中若已存在同名子菜单但挂在别处，直接按名称/路由纠正父节点与顺序
UPDATE `sys_menu`
SET `parent_id` = @course_menu, `route_path` = '/student/practice/papers',
    `sort_order` = 3, `visible` = 1
WHERE `layout` = 'student' AND `route_path` = '/student/practice/papers'
  AND @course_menu IS NOT NULL;

UPDATE `sys_menu`
SET `parent_id` = @course_menu, `route_path` = '/student/practice/free',
    `sort_order` = 4, `visible` = 1
WHERE `layout` = 'student' AND `route_path` = '/student/practice/free'
  AND @course_menu IS NOT NULL;

-- ---------- 4. 补齐学生角色授权（两个子菜单 + 课程学习分组） ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`id` FROM `sys_menu` m
WHERE m.`layout` = 'student'
  AND (m.`id` = @course_menu OR m.`parent_id` = @course_menu)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`
  );

-- ---------- 5. 删除原「自主练习」分组的角色授权与菜单记录 ----------
DELETE FROM `sys_role_menu` WHERE `menu_id` = @practice_menu;

DELETE FROM `sys_menu`
WHERE `id` = @practice_menu
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` c WHERE c.`parent_id` = @practice_menu);

-- ---------- 6. 结果自检 ----------
-- 期望：课程学习子菜单数量 = 4；残留自主练习 = 0；未授权数 = 0
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
   WHERE `layout` = 'student' AND `parent_id` = @course_menu)                    AS `课程学习子菜单数量`,
  (SELECT COUNT(*) FROM `sys_menu`
   WHERE `layout` = 'student' AND `menu_name` = '自主练习')                      AS `残留自主练习`,
  (SELECT COUNT(*) FROM `sys_menu` m
   WHERE m.`layout` = 'student' AND m.`parent_id` = @course_menu
     AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm
                     WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`))          AS `未授权数`;

-- ---------- 7. 明细核对（课程学习 + 四个子菜单 + 授权状态） ----------
SELECT m.`id`, m.`parent_id`, m.`menu_name`, m.`route_path`, m.`icon`, m.`sort_order`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `学生角色`
FROM `sys_menu` m
WHERE m.`layout` = 'student' AND (m.`id` = @course_menu OR m.`parent_id` = @course_menu)
ORDER BY m.`parent_id`, m.`sort_order`;
