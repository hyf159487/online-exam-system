-- =====================================================
-- 班级学情分析：创建知识点表（如果不存在）
-- 用于存储题目关联的知识点信息
-- =====================================================

CREATE TABLE IF NOT EXISTS `knowledge` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `knowledge_name` VARCHAR(100) NOT NULL COMMENT '知识点名称',
  `course_id` BIGINT DEFAULT NULL COMMENT '所属课程ID',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '知识点描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点表';

-- 给 question 表添加外键关联（如果 knowledge_id 列不存在）
-- ALTER TABLE `question` ADD COLUMN `knowledge_id` BIGINT DEFAULT NULL COMMENT '关联知识点';
