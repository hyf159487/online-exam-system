-- ============================================================
-- 迁移脚本：创建考试发布表 exam
-- 执行方式（命令行）：
--   "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p123456 --default-character-set=utf8mb4 exam_system < database\migrate_add_exam.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS exam (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '考试ID',
  exam_name   VARCHAR(100) NOT NULL COMMENT '考试名称',
  paper_id    BIGINT       NOT NULL COMMENT '关联试卷 exam_paper.id',
  course_id   BIGINT       NULL     COMMENT '学科（冗余，方便筛选）',
  start_time  DATETIME     NOT NULL COMMENT '开始时间',
  end_time    DATETIME     NOT NULL COMMENT '结束时间',
  duration    INT          NOT NULL DEFAULT 60 COMMENT '考试时长（分钟）',
  total_score DECIMAL(6,1) NULL     DEFAULT 0 COMMENT '总分（冗余自试卷）',
  pass_score  DECIMAL(6,1) NULL     DEFAULT 60 COMMENT '及格分（冗余自试卷）',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已发布 0=已撤回',
  creator_id  BIGINT       NULL     COMMENT '发布人（sys_user.id）',
  create_time DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_paper (paper_id),
  KEY idx_time (start_time, end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试发布表';
