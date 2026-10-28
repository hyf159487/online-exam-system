-- =====================================================
-- 主观题阅卷：exam_answer 表增加阅卷相关字段
-- =====================================================

ALTER TABLE `exam_answer`
  ADD COLUMN `is_objective` TINYINT DEFAULT 1 COMMENT '0主观题 1客观题' AFTER `get_score`,
  ADD COLUMN `review_status` TINYINT DEFAULT 0 COMMENT '阅卷状态：0待阅卷 1已阅卷' AFTER `is_objective`,
  ADD COLUMN `review_score` DECIMAL(10,2) DEFAULT NULL COMMENT '教师给的主观题得分' AFTER `review_status`,
  ADD COLUMN `review_opinion` VARCHAR(500) DEFAULT NULL COMMENT '阅卷评语' AFTER `review_score`,
  ADD COLUMN `review_user_id` BIGINT DEFAULT NULL COMMENT '阅卷教师ID' AFTER `review_opinion`,
  ADD COLUMN `review_time` DATETIME DEFAULT NULL COMMENT '阅卷时间' AFTER `review_user_id`;

-- 已有数据：客观题默认已阅卷，主观题默认待阅卷（通过 question_type 区分）
UPDATE exam_answer ea
JOIN question q ON ea.question_id = q.id
SET ea.is_objective = CASE WHEN q.question_type IN (1, 2, 3) THEN 1 ELSE 0 END,
    ea.review_status = CASE WHEN q.question_type IN (1, 2, 3) THEN 1 ELSE 0 END;
