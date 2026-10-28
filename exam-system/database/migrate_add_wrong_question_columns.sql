-- ============================================================
-- 迁移脚本：扩展错题本表 wrong_question
-- 背景：exam_system.sql 中 wrong_question 为简化版（仅 user_id/question_id/
--       record_id/wrong_count/last_wrong_time + uk_user_question 唯一约束）。
--       为支撑“按学科筛选 + 显示学生错答与正确答案 + 归集考试来源”，
--       在其基础上追加字段；全部为 ADD COLUMN，向后兼容、无数据丢失。
-- 执行方式（命令行）：
--   "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p123456 --default-character-set=utf8mb4 exam_system < database\migrate_add_wrong_question_columns.sql
-- ============================================================

ALTER TABLE wrong_question
  ADD COLUMN exam_id       BIGINT       NULL COMMENT '所属考试ID（归集来源）' AFTER question_id,
  ADD COLUMN paper_id      BIGINT       NULL COMMENT '试卷ID' AFTER exam_id,
  ADD COLUMN course_id     BIGINT       NULL COMMENT '学科ID（冗余，方便按学科筛选）' AFTER paper_id,
  ADD COLUMN question_type TINYINT      NULL COMMENT '题型：1=单选 2=多选 3=判断 4=填空 5=简答' AFTER course_id,
  ADD COLUMN user_answer   TEXT         NULL COMMENT '学生当时提交的（错误）答案' AFTER question_type,
  ADD COLUMN correct_answer TEXT        NULL COMMENT '标准答案' AFTER user_answer,
  ADD COLUMN score         DECIMAL(5,1) NULL COMMENT '该题分值' AFTER correct_answer;
