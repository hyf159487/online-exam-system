-- =====================================================
-- 课程学习 - 退出课程（含教师审核）功能
--
-- 需在 MySQL 中执行本脚本（幂等，可重复执行）。
-- 说明：
--   course_apply 表新增 apply_type 字段用于区分申请类型：
--     0 = 加入课程申请（默认）
--     1 = 退出课程申请（学生已加入后提交，经教师审核通过后移除课程成员）
--   其余字段复用；status 语义不变：0待审核 1已通过 2已驳回。
-- =====================================================

-- 1. 新增 apply_type 字段（0加入 1退出），默认 0（历史数据均为加入申请）
SET @has_apply_type := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'course_apply'
    AND COLUMN_NAME = 'apply_type'
);
SET @ddl := IF(@has_apply_type = 0,
  'ALTER TABLE `course_apply` ADD COLUMN `apply_type` TINYINT NOT NULL DEFAULT 0 COMMENT ''申请类型：0加入 1退出'' AFTER `course_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 增加联合索引，便于按学生+课程+类型+状态查询
ALTER TABLE `course_apply` ADD KEY `idx_student_course_type` (`student_id`, `course_id`, `apply_type`);
