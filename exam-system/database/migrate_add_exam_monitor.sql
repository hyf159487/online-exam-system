-- =====================================================
-- 考试监控：exam_student 表 + exam_record 扩展
-- 用于教师/管理员实时监控进行中考试的学生状态
--
-- ⚠️  重要：监控功能依赖此表，必须先在 MySQL 执行本脚本！
--     管理员/教师端【考试监控】菜单看不到正在答题的学生，
--     通常就是因为 exam_student 表没创建 —— 执行即可。
-- =====================================================

-- 1. 创建 exam_student 表：实时监控数据
CREATE TABLE IF NOT EXISTS `exam_student` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `exam_id`             BIGINT       NOT NULL COMMENT '考试ID',
  `student_id`          BIGINT       NOT NULL COMMENT '学生ID（sys_user.id）',
  `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0未开始 1考试中 2已交卷 3强制交卷',
  `answered_count`      INT          DEFAULT 0 COMMENT '已作答题目数',
  `total_count`         INT          DEFAULT 0 COMMENT '试卷总题目数',
  `remaining_time`      INT          DEFAULT 0 COMMENT '剩余时间（秒）',
  `screen_switch_count` INT          DEFAULT 0 COMMENT '切屏次数',
  `enter_time`          DATETIME     DEFAULT NULL COMMENT '进入考试时间',
  `leave_time`          DATETIME     DEFAULT NULL COMMENT '离开/交卷时间',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
  KEY `idx_exam_id` (`exam_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试监控-学生实时状态表';

-- 2. 给 exam_record 加 exam_id 列，关联回具体考试（用于成绩统计/监控查询）
--    （MySQL 原生不支持 ADD COLUMN IF NOT EXISTS，用存储过程模拟）
DROP PROCEDURE IF EXISTS `add_exam_record_exam_id`;
DELIMITER $$
CREATE PROCEDURE `add_exam_record_exam_id`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'exam_record'
          AND COLUMN_NAME = 'exam_id'
    ) THEN
        ALTER TABLE `exam_record`
            ADD COLUMN `exam_id` BIGINT DEFAULT NULL COMMENT '考试ID（关联 exam 表）' AFTER `paper_id`,
            ADD KEY `idx_exam_id` (`exam_id`);
    END IF;
END$$
DELIMITER ;
CALL `add_exam_record_exam_id`();
DROP PROCEDURE IF EXISTS `add_exam_record_exam_id`;
