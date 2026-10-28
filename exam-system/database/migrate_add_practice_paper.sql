-- =====================================================
-- 作业管理：作业（练习卷）+ 作答记录模块
-- practice_paper 作业主表、practice_paper_question 关联表、
-- practice_record 作答记录、practice_answer 作答明细
--
-- 注意：本脚本只建基础表，不含「批改作业」所需的批改字段。
--      执行完本脚本后必须继续执行 database/migrate_add_practice_review.sql，
--      它会给 practice_answer 增加 is_objective / review_score / review_status /
--      review_opinion / review_user_id / review_time，给 practice_record 增加
--      review_status / subjective_count（主观题标记与待批改状态）。
-- =====================================================

-- 1. practice_paper 练习卷主表
DROP TABLE IF EXISTS `practice_paper`;
CREATE TABLE `practice_paper` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `paper_name`          VARCHAR(128) NOT NULL COMMENT '练习卷名称',
  `course_id`           BIGINT       DEFAULT NULL COMMENT '所属课程ID（sys_course.id）',
  `description`         VARCHAR(255) DEFAULT NULL COMMENT '练习卷描述',
  `total_score`         INT          NOT NULL DEFAULT 0 COMMENT '总分（各题分值累计）',
  `question_count`      INT          NOT NULL DEFAULT 0 COMMENT '题目数量',
  `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已发布（学生可见） 2停用',
  `create_teacher_id`   BIGINT       NOT NULL COMMENT '创建教师ID（sys_user.id）',
  `create_teacher_name` VARCHAR(64)  DEFAULT NULL COMMENT '创建教师姓名（冗余）',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_create_teacher_id` (`create_teacher_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自主练习-练习卷主表';

-- 2. practice_paper_question 练习卷-试题关联表
DROP TABLE IF EXISTS `practice_paper_question`;
CREATE TABLE `practice_paper_question` (
  `id`               BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `practice_paper_id` BIGINT NOT NULL COMMENT '练习卷ID（practice_paper.id）',
  `question_id`      BIGINT  NOT NULL COMMENT '试题ID（question.id）',
  `sort_no`          INT     NOT NULL DEFAULT 0 COMMENT '题目排序号（从1开始）',
  `score`            INT     NOT NULL DEFAULT 0 COMMENT '单题分值',
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`practice_paper_id`, `question_id`),
  KEY `idx_practice_paper_id` (`practice_paper_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自主练习-练习卷试题关联表';

-- 3. practice_record 学生练习记录表
DROP TABLE IF EXISTS `practice_record`;
CREATE TABLE `practice_record` (
  `id`                 BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `practice_paper_id`  BIGINT   NOT NULL COMMENT '练习卷ID（practice_paper.id）',
  `student_id`         BIGINT   NOT NULL COMMENT '学生ID（sys_user.id）',
  `student_name`       VARCHAR(64) DEFAULT NULL COMMENT '学生姓名（冗余）',
  `score`              INT      NOT NULL DEFAULT 0 COMMENT '本次练习得分',
  `total_score`        INT      NOT NULL DEFAULT 0 COMMENT '练习卷总分（快照，避免改卷后历史记录漂移）',
  `correct_count`      INT      NOT NULL DEFAULT 0 COMMENT '答对题目数',
  `wrong_count`        INT      NOT NULL DEFAULT 0 COMMENT '答错题目数',
  `finish_status`      TINYINT  NOT NULL DEFAULT 0 COMMENT '完成状态：0未完成 1已完成',
  `start_time`         DATETIME DEFAULT NULL COMMENT '开始练习时间',
  `finish_time`        DATETIME DEFAULT NULL COMMENT '完成时间',
  `create_time`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_practice_paper_id` (`practice_paper_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_finish_status` (`finish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自主练习-学生练习记录表';

-- 4. practice_answer 学生练习答题明细表
DROP TABLE IF EXISTS `practice_answer`;
CREATE TABLE `practice_answer` (
  `id`                 BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `practice_record_id` BIGINT   NOT NULL COMMENT '练习记录ID（practice_record.id）',
  `practice_paper_id`  BIGINT   NOT NULL COMMENT '练习卷ID（冗余，方便查询）',
  `question_id`        BIGINT   NOT NULL COMMENT '试题ID（question.id）',
  `sort_no`            INT      NOT NULL DEFAULT 0 COMMENT '题目序号',
  `question_type`      TINYINT  DEFAULT NULL COMMENT '题型快照',
  `score`              INT      NOT NULL DEFAULT 0 COMMENT '该题满分',
  `get_score`          INT      NOT NULL DEFAULT 0 COMMENT '学生得分',
  `user_answer`        VARCHAR(512) DEFAULT NULL COMMENT '学生作答（客观题选项字母集合，逗号分隔）',
  `correct_answer`     VARCHAR(512) DEFAULT NULL COMMENT '正确答案快照',
  `is_correct`         TINYINT  NOT NULL DEFAULT 0 COMMENT '是否答对：0错 1对',
  `create_time`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_question` (`practice_record_id`, `question_id`),
  KEY `idx_practice_record_id` (`practice_record_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自主练习-学生答题明细表';
