-- =============================================
-- 试题审核管理模块：给 question 表增加审核字段
-- =============================================

ALTER TABLE `question`
  ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核，1审核通过，2审核驳回',
  ADD COLUMN `audit_opinion` VARCHAR(500) DEFAULT NULL COMMENT '审核驳回意见',
  ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  ADD COLUMN `audit_user_id` BIGINT DEFAULT NULL COMMENT '审核人id（管理员）';

-- 历史题目（以前创建的）默认设为"审核通过"，不影响已有数据使用
UPDATE `question` SET `audit_status` = 1 WHERE `audit_status` = 0;
