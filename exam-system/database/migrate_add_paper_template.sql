-- =====================================================
-- 组卷模板表 paper_template
-- 保存自动组卷配置，教师可一键复用模板快速组卷
-- =====================================================

DROP TABLE IF EXISTS `paper_template`;
CREATE TABLE `paper_template` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_name`     VARCHAR(100) NOT NULL COMMENT '模板名称：期末测试模板',
  `course_id`         BIGINT       DEFAULT NULL COMMENT '所属课程',
  `total_score`       INT          DEFAULT NULL COMMENT '试卷总分',
  `total_question_count` INT       DEFAULT NULL COMMENT '总题目数',
  `template_json`     TEXT         COMMENT '组卷明细JSON，存储各题型配置：[{questionType,count,score,difficulty}]',
  `paper_type`        INT          NOT NULL DEFAULT 2 COMMENT '组卷方式：1手动 2随机 3AI智能',
  `ai_requirement`    VARCHAR(500) DEFAULT NULL COMMENT 'AI 智能组卷补充说明（paper_type=3 时使用）',
  `creator_id`        BIGINT       DEFAULT NULL COMMENT '创建人（教师id）',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `remark`            VARCHAR(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_creator_id` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组卷模板表';
