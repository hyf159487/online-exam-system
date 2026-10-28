-- =====================================================
-- 管理员端下线「考试管理」「成绩管理」模块（仅保留教师端入口）
--
--   调整前：                                调整后：
--   教师端 / 管理员端                        教师端
--     考试管理 (id=16)                         考试管理
--       ├── 我的考试   /exams                    ├── 我的考试   /exams
--       └── 考试监控   /exams/monitor            └── 考试监控   /exams/monitor
--     成绩管理 (id=19)                         成绩管理
--       ├── 考试成绩   /grades                   ├── 考试成绩   /grades
--       └── 主观题阅卷 /grades/review            └── 主观题阅卷 /grades/review
--                                           管理员端不再显示这两个模块
--
-- 说明：
--   1) 安排考试、发布考试、监控考生、主观题阅卷与成绩复核都属于教师业务，
--      管理员不再操作考试场次与成绩数据，故回收管理员端入口。
--   2) 菜单本身保留（教师继续使用），仅回收「管理员」角色（role_id = 3）的菜单授权；
--      不要改 sys_menu.visible，否则教师端会一起消失。
--   3) 登录返回的菜单来自 sys_role_menu 关联查询（无超管放行逻辑），
--      回收授权后管理员端侧边栏立即不再显示这两个模块。
--   4) 接口权限有意保持不变：/api/exam 的 GET、/api/grade 仍对「教师 + 管理员」可读，
--      管理员的「课程信息 ▸ 课程考试」Tab 仍需要按 courseId 只读查看课程内考试清单；
--      管理员的考试 / 成绩写操作入口由前端路由守卫拦截
--      （meta.group = 'exam' / 'grade' 提示后重定向首页）。
--   5) 幂等，可重复执行。
--
-- 执行：mysql -u root -p exam_system < migrate_remove_admin_exam_grade_menu.sql
-- =====================================================

-- ---------- 1. 定位「考试管理」「成绩管理」父菜单 ----------
SET @exam_mgmt := (
  SELECT `id` FROM `sys_menu`
  WHERE `menu_name` = '考试管理' AND `layout` = 'admin'
  ORDER BY `id` LIMIT 1
);
SET @grade_mgmt := (
  SELECT `id` FROM `sys_menu`
  WHERE `menu_name` = '成绩管理' AND `layout` = 'admin'
  ORDER BY `id` LIMIT 1
);

-- ---------- 2. 回收管理员角色的授权（父菜单 + 子菜单 + 已知路由三重定位） ----------
DELETE rm FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE rm.`role_id` = 3
  AND (
    m.`id` IN (@exam_mgmt, @grade_mgmt)
    OR m.`parent_id` IN (@exam_mgmt, @grade_mgmt)
    OR m.`route_path` IN ('/exams', '/exams/monitor', '/grades', '/grades/review')
  );

-- ---------- 3. 结果自检 ----------
-- 期望：管理员剩余授权 = 0；教师授权 = 6（两个父菜单 + 四个子菜单）
SELECT
  (SELECT COUNT(*) FROM `sys_role_menu` rm
   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
   WHERE rm.`role_id` = 3
     AND (m.`id` IN (@exam_mgmt, @grade_mgmt)
          OR m.`parent_id` IN (@exam_mgmt, @grade_mgmt)
          OR m.`route_path` IN ('/exams', '/exams/monitor', '/grades', '/grades/review')))   AS `管理员剩余授权`,
  (SELECT COUNT(*) FROM `sys_role_menu` rm
   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
   WHERE rm.`role_id` = 2
     AND (m.`id` IN (@exam_mgmt, @grade_mgmt)
          OR m.`parent_id` IN (@exam_mgmt, @grade_mgmt)
          OR m.`route_path` IN ('/exams', '/exams/monitor', '/grades', '/grades/review')))   AS `教师授权`;

-- ---------- 4. 明细核对 ----------
SELECT m.`id`, m.`menu_name`, m.`route_path`, m.`layout`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 2 AND rm.`menu_id` = m.`id`), '有', '无') AS `教师授权`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 3 AND rm.`menu_id` = m.`id`), '有', '无') AS `管理员授权`
FROM `sys_menu` m
WHERE m.`id` IN (@exam_mgmt, @grade_mgmt) OR m.`parent_id` IN (@exam_mgmt, @grade_mgmt)
   OR m.`route_path` IN ('/exams', '/exams/monitor', '/grades', '/grades/review')
ORDER BY m.`parent_id`, m.`sort_order`;
