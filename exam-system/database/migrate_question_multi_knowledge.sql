-- ============================================================
-- 题目-知识点 多对多关联表
-- 用途：支持一道题绑定 1~3 个知识点
-- 说明：question.knowledge_id 保留为「主知识点」，始终与关联表首条一致，
--       供组卷/练习/统计等既有单知识点功能兼容使用。
-- ============================================================

CREATE TABLE IF NOT EXISTS `question_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识点ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_knowledge` (`question_id`, `knowledge_id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目-知识点关联表（一道题可关联多个知识点）';

-- 回填：把已有 question.knowledge_id 迁入关联表
-- 先清空再插入，保证脚本可重复执行（幂等）
DELETE FROM `question_knowledge`;

INSERT INTO `question_knowledge` (`question_id`, `knowledge_id`)
SELECT `id`, `knowledge_id` FROM `question` WHERE `knowledge_id` IS NOT NULL;
