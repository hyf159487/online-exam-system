-- =====================================================
-- 学生端菜单调整：「加入课程」由「课程学习」子菜单提升为一级菜单
--
--   调整前：                            调整后：
--   课程学习 (分组)                      加入课程  /student/course/join      (一级)
--     ├── 加入课程  /student/course/join
--     ├── 课程资料  /student/course/resources  课程学习 (分组)
--     ├── 练习卷    /student/practice/papers     ├── 课程资料  /student/course/resources
--     └── 自由刷题  /student/practice/free       ├── 练习卷    /student/practice/papers
--                                                └── 自由刷题  /student/practice/free
--
-- 说明：
--   1) 只调整 sys_menu 的 parent_id / sort_order，菜单对应的路由与页面完全不变；
--   2) 复用原有菜单行，不新建菜单，因此不会产生重复授权（授权关系原样保留）；
--   3) 「课程学习」仍保留 课程资料 / 练习卷 / 自由刷题 三个子菜单，不会变成空分组；
--   4) 前端 StudentLayout.vue 已同时兼容「挂在课程学习下」与「已提升为一级」两种库结构，
--      本脚本未执行时菜单也照常显示为一级入口，执行本脚本只是让库结构与界面保持一致。
--
-- 执行：mysql -u root -p exam_system < migrate_promote_join_course_menu.sql
-- 幂等：可重复执行，不会产生重复菜单或重复授权。
-- =====================================================

-- ---------- 1. 定位学生端「加入课程」与「课程学习」菜单 ----------
SET @join_menu := IFNULL(
  (SELECT `id` FROM `sys_menu`
    WHERE `layout` = 'student' AND `menu_name` = '加入课程' ORDER BY `id` LIMIT 1),
  (SELECT `id` FROM `sys_menu`
    WHERE `layout` = 'student' AND `route_path` = '/student/course/join' ORDER BY `id` LIMIT 1)
);

SET @course_menu :=
  (SELECT `id` FROM `sys_menu`
    WHERE `layout` = 'student' AND `menu_name` = '课程学习' ORDER BY `id` LIMIT 1);

-- ---------- 2. 提升为一级菜单 ----------
-- sort_order = 3：学生端一级菜单排在「通知公告」之后、「课程学习」之前
UPDATE `sys_menu`
   SET `parent_id`  = 0,
       `sort_order` = 3,
       `visible`    = 1,
       `icon`       = IFNULL(NULLIF(`icon`, ''), 'Plus')
 WHERE `id` = @join_menu
   AND `layout` = 'student';

-- ---------- 3. 补齐学生角色(role_id=1)授权：已授权则跳过，不会重复 ----------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, @join_menu FROM DUAL WHERE @join_menu IS NOT NULL;

-- ---------- 4. 结果自检 ----------
-- 期望：课程学习子菜单数量 = 3；「加入课程」parent_id = 0 且已授权
SELECT
  (SELECT COUNT(*) FROM `sys_menu`
    WHERE `layout` = 'student' AND `parent_id` = @course_menu)                     AS `课程学习子菜单数量`,
  (SELECT COUNT(*) FROM `sys_menu`
    WHERE `layout` = 'student' AND `menu_name` = '加入课程' AND `parent_id` = 0)   AS `加入课程已为一级(应为1)`;

SELECT m.`id`, m.`parent_id`, m.`menu_name`, m.`route_path`, m.`icon`, m.`sort_order`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                   WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `学生角色`
  FROM `sys_menu` m
 WHERE m.`layout` = 'student'
   AND (m.`parent_id` = 0 OR m.`parent_id` = @course_menu)
 ORDER BY m.`parent_id`, m.`sort_order`, m.`id`;
