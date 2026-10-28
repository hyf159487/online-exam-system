-- ============================================================
-- 班级管理优化：班级绑定任课教师
-- sys_class 增加 teacher_id 列（关联 sys_user.id，role=1 教师）
-- 执行方式：直接在 MySQL 中运行本脚本
-- ============================================================

ALTER TABLE `sys_class`
  ADD COLUMN `teacher_id` bigint DEFAULT NULL COMMENT '任课教师ID（sys_user.id，role=1 教师）' AFTER `department`;

-- 可选：加索引方便按教师查班级
ALTER TABLE `sys_class` ADD INDEX `idx_teacher_id` (`teacher_id`);
