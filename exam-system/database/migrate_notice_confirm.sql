-- =============================================================
-- 学生消息「确认收到」功能迁移脚本
-- 1) sys_user_notice 增加确认状态与确认时间字段
--    - confirm_status：0 未确认 / 1 已确认收到
--    - confirm_time ：学生点击确认的时间
-- 2) 考试自动通知的关联考试复用 sys_notice.target_exam_id，无需新增字段
-- 执行前提：migrate_student_message_page.sql 已执行
-- =============================================================

ALTER TABLE `sys_user_notice`
    ADD COLUMN `confirm_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0未确认 1已确认收到' AFTER `is_read`,
    ADD COLUMN `confirm_time` DATETIME DEFAULT NULL COMMENT '确认收到时间' AFTER `confirm_status`;
