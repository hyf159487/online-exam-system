-- ============================================================
-- 课程管理优化：课程-任课教师 多对多关系
-- 一门课程可绑定多名授课教师，一名教师可授多门课程
-- 执行方式：直接在 MySQL 中运行本脚本
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_course_teacher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` bigint NOT NULL COMMENT '课程ID（sys_course.id）',
  `teacher_id` bigint NOT NULL COMMENT '教师ID（sys_user.id，role=1）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_teacher` (`course_id`, `teacher_id`),
  KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程-任课教师关系表';
