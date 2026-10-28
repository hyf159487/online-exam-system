-- =====================================================
-- 考试记录成绩快照：防止「考试/试卷被删除后学生成绩丢失」
--
-- 背景：学生端"我的成绩"此前通过 LEFT JOIN exam_paper 联查出
--   试卷名(paper_name)、满分(total_score)、及格分(pass_score)。
--   一旦考试/试卷被删除，联查字段即变空，学生看不到完整成绩。
--
-- 本脚本在 exam_record 冗余三列，交卷时写入快照；查询时优先读快照，
--   即使考试/试卷被删除，历史成绩依然完整展示。
--
-- 需在 MySQL 中执行本脚本。
-- =====================================================

DROP PROCEDURE IF EXISTS `add_exam_record_snapshot`;
DELIMITER $$
CREATE PROCEDURE `add_exam_record_snapshot`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'exam_record'
          AND COLUMN_NAME = 'paper_name'
    ) THEN
        ALTER TABLE `exam_record`
            ADD COLUMN `paper_name` VARCHAR(100) DEFAULT NULL
                COMMENT '试卷名快照（交卷时冗余，防删除丢成绩）' AFTER `exam_id`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'exam_record'
          AND COLUMN_NAME = 'paper_total_score'
    ) THEN
        ALTER TABLE `exam_record`
            ADD COLUMN `paper_total_score` DECIMAL(10,2) DEFAULT NULL
                COMMENT '试卷满分快照' AFTER `paper_name`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'exam_record'
          AND COLUMN_NAME = 'pass_score'
    ) THEN
        ALTER TABLE `exam_record`
            ADD COLUMN `pass_score` DECIMAL(10,2) DEFAULT NULL
                COMMENT '及格分快照' AFTER `paper_total_score`;
    END IF;
END$$
DELIMITER ;
CALL `add_exam_record_snapshot`();
DROP PROCEDURE IF EXISTS `add_exam_record_snapshot`;

-- ============ 回填存量数据 ============
-- 从仍存在的试卷联查回填（已物理删除试卷的历史记录无法恢复，属源数据本身丢失）
UPDATE `exam_record` r
LEFT JOIN `exam_paper` p ON r.`paper_id` = p.`id`
SET r.`paper_name`         = COALESCE(r.`paper_name`, p.`paper_name`),
    r.`paper_total_score`  = COALESCE(r.`paper_total_score`, p.`total_score`),
    r.`pass_score`         = COALESCE(r.`pass_score`, p.`pass_score`)
WHERE r.`paper_name` IS NULL OR r.`paper_total_score` IS NULL OR r.`pass_score` IS NULL;
