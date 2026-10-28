-- ============================================================
-- 迁移脚本：支撑「自主练习」+「错题本答对自动移除」策略
-- 1) wrong_question 增加 correct_count：重做做对次数（达到阈值后自动移出错题本）
-- 2) sys_user 增加错题自动移除策略：wrong_auto_remove / wrong_remove_threshold
-- 全部为 ADD COLUMN，向后兼容、无数据丢失。
--
-- 执行方式（命令行）：
--   "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p123456 --default-character-set=utf8mb4 exam_system < database\add_practice_features.sql
-- ============================================================

-- 1) 错题本：增加「做对次数」字段
ALTER TABLE wrong_question
  ADD COLUMN correct_count INT NOT NULL DEFAULT 0 COMMENT '重做做对次数（达到阈值后自动移出错题本）' AFTER wrong_count;

-- 2) 学生：错题自动移除策略
ALTER TABLE sys_user
  ADD COLUMN wrong_auto_remove    TINYINT NOT NULL DEFAULT 0 COMMENT '错题本自动移除开关：0关 1开' AFTER status,
  ADD COLUMN wrong_remove_threshold INT    NOT NULL DEFAULT 3 COMMENT '做对几次后移出错题本（1 或 3）' AFTER wrong_auto_remove;
