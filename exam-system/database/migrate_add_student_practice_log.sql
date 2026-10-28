-- ============================================================
-- 迁移脚本：新增「学生作答记录表 student_practice_log」
-- 每次单题作答（自由刷题单题 / 练习卷整套）都记录一条，用于统计
-- 「某学科已练习题数（去重）」及后续学习诊断。
--
-- 执行方式（命令行，替换成你的数据库密码）：
--   "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p123456 --default-character-set=utf8mb4 exam_system < database\migrate_add_student_practice_log.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS student_practice_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT       NOT NULL COMMENT '学生用户ID',
  question_id   BIGINT       NOT NULL COMMENT '题目ID（关联 question 表）',
  course_id     BIGINT       NULL     COMMENT '所属学科ID（冗余，便于按学科统计）',
  question_type INT          NULL     COMMENT '题型：1单选 2多选 3判断 4填空 5简答',
  source        TINYINT      NOT NULL DEFAULT 1 COMMENT '来源：1自由刷题单题 2练习卷整套',
  user_answer   VARCHAR(2000) NULL   COMMENT '学生作答',
  is_correct    TINYINT      NULL     COMMENT '是否答对：1对 0错（主观题不判分为NULL）',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作答时间',
  PRIMARY KEY (id),
  KEY idx_user_course (user_id, course_id),
  KEY idx_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生作答记录（按学科已练习题数/学习诊断用）';
