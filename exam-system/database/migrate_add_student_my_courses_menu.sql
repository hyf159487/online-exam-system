-- =====================================================
-- 学生端「我的课程」菜单（个人中心子菜单）
-- 位置：个人中心 -> 个人信息(1) / 我的课程(3) / 关于系统(4)
-- 只分配给角色 role_id=1（学生）；管理员/教师看不到学生端菜单
-- 幂等：可重复执行，不会产生重复菜单或重复授权
--
-- 前端对应改动：
--   views/StudentMyCourse.vue（新增页面）、router/index.js（/student/profile/my-courses）、
--   components/CourseCard.vue（卡片视图）、views/StudentDashboard.vue（功能总览卡片）
--   后端对应接口：GET /api/student/course/my-courses
--
-- 提示：StudentLayout.vue 已做兜底注入——未执行本脚本时「个人中心」下也会显示「我的课程」；
--       执行后由数据库菜单接管（按 route_path 去重），不会出现两个入口。
-- =====================================================

SET @profile := (SELECT `id` FROM `sys_menu`
                 WHERE `menu_name` = '个人中心' AND `layout` = 'student'
                 ORDER BY `id` LIMIT 1);

-- ---------- 1. 新增菜单（不存在时才插入） ----------
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT @profile, '我的课程', '/student/profile/my-courses', 'Files', 3, 1, 'student'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `route_path` = '/student/profile/my-courses' AND `layout` = 'student'
);

-- ---------- 2. 位置与顺序修正 ----------
UPDATE `sys_menu`
SET `parent_id` = @profile, `menu_name` = '我的课程',
    `route_path` = '/student/profile/my-courses', `icon` = 'Files', `sort_order` = 3, `visible` = 1
WHERE `route_path` = '/student/profile/my-courses' AND `layout` = 'student';

-- 个人信息(1) → 我的课程(3) → 关于系统(4)
UPDATE `sys_menu` SET `sort_order` = 1 WHERE `id` = 40 AND `layout` = 'student';
UPDATE `sys_menu` SET `sort_order` = 4 WHERE `id` = 43 AND `layout` = 'student';

SET @my_course_menu := (SELECT `id` FROM `sys_menu`
                        WHERE `route_path` = '/student/profile/my-courses' AND `layout` = 'student'
                        ORDER BY `id` LIMIT 1);

-- ---------- 3. 授权给学生角色（role_id=1） ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, @my_course_menu FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = @my_course_menu
);

-- ---------- 4. 自检 ----------
SELECT m.`id`, m.`parent_id`, m.`menu_name`, m.`route_path`, m.`sort_order`, m.`visible`, m.`layout`,
       (SELECT COUNT(*) FROM `sys_role_menu` rm WHERE rm.`menu_id` = m.`id` AND rm.`role_id` = 1) AS `granted_to_student`
FROM `sys_menu` m
WHERE m.`parent_id` = @profile
ORDER BY m.`sort_order`, m.`id`;
