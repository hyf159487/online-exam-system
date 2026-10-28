-- =====================================================
-- 课程学习（加入课程 / 课程资料）功能
-- 学生加入课程 + 任课教师审核 + 消息通知
--
-- 需在 MySQL 中执行本脚本。
-- 表：
--   course_apply    学生申请加入课程记录
--   course_member   学生已加入的课程（审核通过后写入）
-- 菜单：
--   学生端：课程学习(父) -> 加入课程 / 课程资料
--   教师端：课程申请审核
-- =====================================================

-- ---------- 1. 申请加入课程记录表 ----------
DROP TABLE IF EXISTS `course_apply`;
CREATE TABLE `course_apply` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id`       BIGINT      NOT NULL COMMENT '申请学生ID（sys_user.id，role=0）',
  `course_id`        BIGINT      NOT NULL COMMENT '申请课程ID（sys_course.id）',
  `status`           TINYINT     NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已通过 2已驳回',
  `reject_reason`    VARCHAR(255) DEFAULT NULL COMMENT '驳回理由',
  `apply_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `audit_teacher_id` BIGINT      DEFAULT NULL COMMENT '审核教师ID',
  `audit_time`       DATETIME    DEFAULT NULL COMMENT '审核时间',
  `is_deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生加入课程申请记录表';

-- ---------- 2. 学生已加入课程表（审核通过后写入） ----------
DROP TABLE IF EXISTS `course_member`;
CREATE TABLE `course_member` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id`  BIGINT   NOT NULL COMMENT '学生ID（sys_user.id，role=0）',
  `course_id`   BIGINT   NOT NULL COMMENT '课程ID（sys_course.id）',
  `join_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_course` (`student_id`, `course_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生已加入课程表';

-- ---------- 3. 学生端菜单：课程学习 -> 加入课程 / 课程资料 ----------
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
VALUES (0, '课程学习', NULL, 'Reading', 4, 1, 'student');
SET @course_menu := LAST_INSERT_ID();

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
VALUES
  (@course_menu, '加入课程', '/student/course/join',      'Plus',        1, 1, 'student'),
  (@course_menu, '课程资料', '/student/course/resources', 'FolderOpened', 2, 1, 'student');

-- 分配给学生角色（role_id=1）：课程学习及其全部子菜单
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu`
WHERE `layout` = 'student' AND (`parent_id` = @course_menu OR `id` = @course_menu);

-- ---------- 4. 教师端菜单：课程申请审核（挂在"课程管理"菜单下） ----------
SET @course_mgmt_menu := (SELECT `id` FROM `sys_menu`
                          WHERE `menu_name` = '课程管理' AND `layout` = 'admin'
                          ORDER BY `id` LIMIT 1);

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
VALUES
  (@course_mgmt_menu, '课程申请审核', '/course/applies', 'Checked',
   (SELECT IFNULL(MAX(`sort_order`), 0) + 1 FROM `sys_menu` WHERE `parent_id` = @course_mgmt_menu),
   1, 'admin');
SET @apply_menu := LAST_INSERT_ID();

-- 只分配给教师（role_id=2）；管理员不做课程申请审核，不分配
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2, @apply_menu);

-- ===== 修正：若此前已按"独立顶级菜单"插入过"课程申请审核"，将其移入"课程管理"下（幂等，可单独执行） =====
UPDATE `sys_menu` m
JOIN `sys_menu` p ON p.`menu_name` = '课程管理' AND p.`layout` = 'admin'
SET m.`parent_id` = p.`id`
WHERE m.`menu_name` = '课程申请审核' AND m.`layout` = 'admin' AND m.`parent_id` <> p.`id`;

SET @course_mgmt := (SELECT `id` FROM `sys_menu`
                     WHERE `menu_name` = '课程管理' AND `layout` = 'admin'
                     ORDER BY `id` LIMIT 1);
SET @max_sort := (SELECT IFNULL(MAX(`sort_order`), 0) FROM `sys_menu` WHERE `parent_id` = @course_mgmt);
UPDATE `sys_menu`
SET `sort_order` = @max_sort + 1
WHERE `menu_name` = '课程申请审核' AND `layout` = 'admin';

-- ===== 修正：若此前已把"课程申请审核"分配给管理员（role_id=3），移除管理员入口（幂等） =====
DELETE `srm` FROM `sys_role_menu` srm
JOIN `sys_menu` m ON m.`id` = srm.`menu_id`
WHERE m.`menu_name` = '课程申请审核' AND m.`layout` = 'admin' AND srm.`role_id` = 3;
