-- =============================================================
-- 试题审核流水表迁移脚本
-- 目的：多次提交多次审核全程可追溯（旧历史记录保留，不可篡改）
-- 说明：此前审核历史直接依赖 question 表的 audit_status 字段，
--       教师驳回后重新提交会覆盖旧审核信息；引入流水表后，
--       每次审核通过/驳回/重新提交都会新增一条流水记录。
-- 执行方式：可重复执行（NOT EXISTS 防止存量数据重复迁移）
-- =============================================================

-- 1. 审核流水表（只插入，不更新不删除，保证审核留痕）
CREATE TABLE IF NOT EXISTS `question_audit_log` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `question_id`      BIGINT       NOT NULL COMMENT '题目ID',
  `action`           TINYINT      NOT NULL COMMENT '动作：1=审核通过 2=审核驳回 3=重新提交',
  `opinion`          VARCHAR(500) DEFAULT NULL COMMENT '驳回原因（仅驳回动作填写）',
  `content_snapshot` TEXT         COMMENT '操作时题干快照（教师重提可能修改题干，快照还原当时内容）',
  `submitter_id`     BIGINT       DEFAULT NULL COMMENT '提交教师ID',
  `auditor_id`       BIGINT       DEFAULT NULL COMMENT '审核操作人ID（重新提交流水为空，操作人即提交教师）',
  `audit_time`       DATETIME     NOT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_qal_question` (`question_id`),
  KEY `idx_qal_action` (`action`),
  KEY `idx_qal_time` (`audit_time`),
  KEY `idx_qal_submitter` (`submitter_id`),
  KEY `idx_qal_auditor` (`auditor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试题审核流水表（留痕记录，禁止修改删除）';

-- 2. 存量历史数据迁移：把 question 表中已有审核结论的记录补录进流水表
--    注意：部分存量数据的 audit_time 可能为空（如直接导入的测试数据），
--    此时依次用 update_time / create_time 兜底作为流水时间
INSERT INTO question_audit_log
  (question_id, action, opinion, content_snapshot, submitter_id, auditor_id, audit_time)
SELECT q.id, q.audit_status, q.audit_opinion, q.content, q.creator_id, q.audit_user_id,
       COALESCE(q.audit_time, q.update_time, q.create_time)
FROM question q
WHERE q.audit_status IN (1, 2)
  AND NOT EXISTS (
    SELECT 1 FROM question_audit_log l
    WHERE l.question_id = q.id
      AND l.action = q.audit_status
      AND l.audit_time = COALESCE(q.audit_time, q.update_time, q.create_time)
  );
