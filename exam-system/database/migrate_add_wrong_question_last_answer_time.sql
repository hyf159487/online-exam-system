-- =============================================================================
-- 错题本增强（学生端「我的错题」页面优化）
--   1) wrong_question 新增 last_answer_time：最近一次作答时间，和「最近错误」区分开
--      （考试、自主练习、错题重做都会刷新；老数据用 last_wrong_time 兜底）
--   2) 明确 correct_count 语义为「连续答对次数」：做对 +1，一旦答错清零
--   3) sys_user.wrong_remove_threshold 语义：连续答对几次自动移出，范围 1~10，默认 3
-- 执行方式：mysql -u root -p exam_system < migrate_add_wrong_question_last_answer_time.sql
-- 脚本幂等，可重复执行。
-- =============================================================================

DROP PROCEDURE IF EXISTS add_col_last_answer_time;
DELIMITER $$
CREATE PROCEDURE add_col_last_answer_time()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wrong_question'
                     AND COLUMN_NAME = 'last_answer_time') THEN
        ALTER TABLE wrong_question
            ADD COLUMN last_answer_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP
            COMMENT '最近一次作答时间（考试/练习/重做都会刷新）' AFTER last_wrong_time;
    END IF;
END $$
DELIMITER ;
CALL add_col_last_answer_time();
DROP PROCEDURE add_col_last_answer_time;

-- 历史数据回填：老记录没有作答时间，先用最近答错时间兜底
UPDATE wrong_question SET last_answer_time = last_wrong_time WHERE last_answer_time IS NULL;

-- 同步列注释，说明「连续答对次数」语义
ALTER TABLE wrong_question MODIFY COLUMN correct_count INT DEFAULT 0
    COMMENT '连续答对次数（做对 +1，答错清零；达到策略阈值后自动移出错题本）';
