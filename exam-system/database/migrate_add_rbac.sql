-- =====================================================
-- RBAC 角色与菜单权限：3 张核心表 + sys_user 关联字段
--
-- 设计要点：
--   1. 角色固定 3 种：1=学生 student / 2=教师 teacher / 3=管理员 admin
--      与现有 sys_user.role (0/1/2) 形成 1:1 映射：role_id = role + 1
--      保留 sys_user.role 字段不动，避免破坏 AuthInterceptor/Layout 现有逻辑
--   2. 菜单按 parent_id 自关联形成树，route_path 为前端路由地址
--   3. layout 字段区分管理员/教师端 Layout 与学生端 StudentLayout：
--      admin=在 Layout.vue 渲染，student=在 StudentLayout.vue 渲染
--   4. 角色-菜单多对多中间表 sys_role_menu
--   5. 种子数据：管理员分配全部菜单、教师分配教学业务菜单（不含系统/审核/账号/班级/课程）、
--      学生分配学生端全部菜单
-- =====================================================

-- ---------- 1. 角色表 sys_role ----------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name`   VARCHAR(64)  NOT NULL COMMENT '角色名称：学生/教师/管理员',
  `role_key`    VARCHAR(64)  NOT NULL COMMENT '角色标识：student/teacher/admin',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '角色备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 固定 3 个角色：id 与 sys_user.role 形成 role+1 映射
INSERT INTO `sys_role` (`id`, `role_name`, `role_key`, `remark`) VALUES
  (1, '学生',   'student', '学生角色，仅访问学生端菜单'),
  (2, '教师',   'teacher', '教师角色，访问教学业务菜单，不含系统/审核/账号管理'),
  (3, '管理员', 'admin',   '超级管理员，拥有全部菜单权限，禁止删除');

-- ---------- 2. 菜单权限表 sys_menu ----------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0=顶级菜单',
  `menu_name`   VARCHAR(64)  NOT NULL COMMENT '菜单名称',
  `route_path`  VARCHAR(128) DEFAULT NULL COMMENT '前端路由地址（父菜单可空）',
  `icon`        VARCHAR(64)  DEFAULT NULL COMMENT 'Element Plus 图标名',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '同级排序号（升序）',
  `visible`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示：1=是 0=否',
  `layout`      VARCHAR(16)  NOT NULL DEFAULT 'admin' COMMENT '所属布局：admin=教师端Layout / student=学生端StudentLayout',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_layout` (`layout`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- 菜单数据（与前端 router/index.js 完全对应）
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `layout`) VALUES
-- ===== 管理员/教师端 Layout.vue 菜单 =====
(1,  0, '首页',         '/dashboard', 'HomeFilled', 1, 'admin'),

-- 系统管理（管理员专属）
(2,  0, '系统管理',     NULL,         'Setting',     2, 'admin'),
(3,  2, '操作日志',     '/system/logs',          'List',         1, 'admin'),
(4,  2, '角色与权限配置', '/system/roles',        'UserFilled',   2, 'admin'),
(5,  2, '字典管理',     '/system/dictionaries', 'Collection',   3, 'admin'),
(6,  2, '系统参数配置', '/system/config',        'Tools',        4, 'admin'),

-- 试题审核管理（管理员专属）
(7,  0, '试题审核管理', NULL,         'Checked',     3, 'admin'),
(8,  7, '待审核试题',   '/audit/pending',        'Document',     1, 'admin'),
(9,  7, '审核历史记录', '/audit/history',        'Clock',        2, 'admin'),

-- 题库管理
(10, 0, '题库管理',     NULL,         'Document',    4, 'admin'),
(11, 10, '我的试题',    '/questions',             'Files',        1, 'admin'),
(12, 10, '高频错题库',  '/questions/hot-wrong',   'Warning',      2, 'admin'),

-- 试卷管理
(13, 0, '试卷管理',     NULL,         'Notebook',    5, 'admin'),
(14, 13, '我的试卷',    '/papers',                'Files',        1, 'admin'),
(15, 13, '组卷模板',    '/paper-templates',       'DocumentCopy', 2, 'admin'),

-- 考试管理
(16, 0, '考试管理',     NULL,         'Timer',       6, 'admin'),
(17, 16, '我的考试',    '/exams',                 'Files',        1, 'admin'),
(18, 16, '考试监控',    '/exams/monitor',         'View',         2, 'admin'),

-- 成绩管理
(19, 0, '成绩管理',     NULL,         'DataAnalysis', 7, 'admin'),
(20, 19, '考试成绩',    '/grades',                'Files',        1, 'admin'),
(21, 19, '主观题阅卷',  '/grades/review',         'EditPen',      2, 'admin'),

-- 账号/班级/课程管理（管理员专属）
(22, 0, '账号管理',     '/students',   'User',        8, 'admin'),
(23, 0, '班级管理',     '/classes',    'School',      9, 'admin'),
(24, 0, '课程管理',     '/courses',    'Reading',    10, 'admin'),

-- 教学统计分析（教师专属）
(25, 0, '教学统计分析', NULL,         'TrendCharts', 11, 'admin'),
(26, 25, '班级学情分析', '/stats/class',           'DataLine',     1, 'admin'),
(27, 25, '试题质量分析', '/stats/question',        'Histogram',    2, 'admin'),
(28, 25, '学情报表导出', '/stats/export',          'Download',     3, 'admin'),

-- 消息通知（教师专属）
(29, 0, '消息通知',     NULL,         'Bell',        12, 'admin'),
(30, 29, '发布通知',    '/notifications/send',    'Promotion',    1, 'admin'),
(31, 29, '通知记录',    '/notifications/history', 'List',         2, 'admin'),

-- 作业管理（教师专属，原「自主练习管理」→「发布作业」）
(32, 0, '作业管理',     NULL,         'EditPen',     13, 'admin'),
(33, 32, '发布作业',    '/practice/papers',       'Files',        1, 'admin'),
-- 批改作业：批改学生提交作业里的主观题（简答题/名词解释）
(56, 32, '批改作业',    '/practice/review',       'Checked',      2, 'admin'),
(34, 32, '作业记录',    '/practice/records',      'View',         3, 'admin'),

-- ===== 学生端 StudentLayout.vue 菜单 =====
(35, 0, '我的考试',     '/student/exams',           'Notebook',     1, 'student'),
(36, 0, '我的成绩',     '/student/grades',          'DataAnalysis', 2, 'student'),
(37, 0, '自主练习',     '/student/practice',        'EditPen',      3, 'student'),
(38, 0, '我的错题',     '/student/wrong-questions', 'Warning',      4, 'student'),
(39, 0, '个人中心',     NULL,                       'UserFilled',   5, 'student'),
(40, 39, '个人信息',    '/student/profile/info',           'Postcard',     1, 'student'),
-- 我的课程：集中查看我已加入的课程信息（个人中心子菜单），页面 StudentMyCourse.vue
(44, 39, '我的课程',    '/student/profile/my-courses',     'Files',        3, 'student'),
(43, 39, '关于系统',    '/student/profile/about',           'InfoFilled',   4, 'student');
-- 注：1) 原 id=41「修改密码」子菜单已下线（功能合并进「个人信息」页面底部），
--        老库升级请执行 database/migrate_merge_change_password_into_profile.sql
--     2) 原 id=42「消息通知」子菜单已下线：它与学生端侧边栏顶部固定的「通知公告」
--        入口指向同一页面（/student/messages），属重复入口；「通知公告」由
--        StudentLayout.vue 固定渲染，无需数据库菜单。
--        老库升级请执行 database/migrate_remove_duplicate_student_notice_menu.sql
--     3) id=44「我的课程」为新增子菜单，老库升级请执行
--        database/migrate_add_student_my_courses_menu.sql（幂等）

-- ---------- 3. 角色菜单关联表 sys_role_menu ----------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- ---------- 4. 默认角色-菜单权限分配 ----------

-- 管理员(role_id=3)：全部 admin 端菜单（1~34）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 3, `id` FROM `sys_menu` WHERE `layout` = 'admin';

-- 教师(role_id=2)：首页 + 题库/试卷/考试/成绩 全部子菜单 + 教学统计/消息通知/自主练习
--   不含：系统管理(2-6)、试题审核管理(7-9)、账号/班级/课程管理(22-24)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
  (2, 1),           -- 首页
  (2, 10), (2, 11), (2, 12),    -- 题库管理 + 我的试题 + 高频错题库
  (2, 13), (2, 14), (2, 15),    -- 试卷管理 + 我的试卷 + 组卷模板
  (2, 16), (2, 17), (2, 18),    -- 考试管理 + 我的考试 + 考试监控
  (2, 19), (2, 20), (2, 21),    -- 成绩管理 + 考试成绩 + 主观题阅卷
  (2, 25), (2, 26), (2, 27), (2, 28),    -- 教学统计分析
  (2, 29), (2, 30), (2, 31),            -- 消息通知
  (2, 32), (2, 33), (2, 34), (2, 56);   -- 作业管理 + 发布作业 + 作业记录 + 批改作业

-- 学生(role_id=1)：学生端全部菜单（35~43，不含已下线的 41 修改密码 / 42 消息通知）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `layout` = 'student';

-- ---------- 5. sys_user 加 role_id 字段（与 role 字段并存，平滑迁移） ----------
ALTER TABLE `sys_user` ADD COLUMN `role_id` BIGINT DEFAULT NULL COMMENT '角色ID（关联 sys_role.id）' AFTER `role`;

-- 用现有 role 字段回填 role_id：0学生→1，1教师→2，2管理员→3
UPDATE `sys_user` SET `role_id` = `role` + 1 WHERE `role` IS NOT NULL;
