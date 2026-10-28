-- =====================================================
-- 学生端「课程详情页」：课程扩展字段 + 课程公告
--
-- 背景：学生从「加入课程」点击课程卡片进入课程详情页
--      （课程介绍 / 课程资料 / 课程公告 / 课程作业 / 课程考试 五个 Tab）
--
-- 1) sys_course 增加 4 个详情页字段（管理端「课程管理」表单可编辑）：
--    intro               课程详细介绍（详情页「课程介绍」正文，与卡片简短描述 description 区分）
--    learning_objectives 学习目标
--    target_audience     适用人群
--    syllabus            课程大纲（每行一个章节，详情页按行渲染）
-- 2) 新建 course_notice 课程公告表：任课教师在课程内发布公告，
--    学生端「课程详情 → 课程公告」Tab 查看（仅已加入课程的学生可读）
--
-- ⚠️ 本脚本为一次性迁移，只需执行一次：
--    若报 1060 - Duplicate column name 'intro'（或 course_notice 表已存在），
--    说明该库结构已经是最新的，直接忽略即可，不用再执行。
--    校验是否已生效：
--      SELECT column_name FROM information_schema.columns
--       WHERE table_schema = DATABASE() AND table_name = 'sys_course'
--         AND column_name IN ('intro','learning_objectives','target_audience','syllabus');
--      SHOW TABLES LIKE 'course_notice';
-- =====================================================

ALTER TABLE `sys_course`
    ADD COLUMN `intro` TEXT NULL COMMENT '课程详细介绍（学生端课程详情页展示）' AFTER `description`,
    ADD COLUMN `learning_objectives` VARCHAR(500) NULL DEFAULT NULL COMMENT '学习目标' AFTER `intro`,
    ADD COLUMN `target_audience` VARCHAR(500) NULL DEFAULT NULL COMMENT '适用人群' AFTER `learning_objectives`,
    ADD COLUMN `syllabus` TEXT NULL COMMENT '课程大纲（每行一个章节）' AFTER `target_audience`;

CREATE TABLE IF NOT EXISTS `course_notice`
(
    `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `course_id`    bigint(20)   NOT NULL COMMENT '所属课程ID（sys_course.id）',
    `title`        varchar(128) NOT NULL COMMENT '公告标题',
    `content`      text         NOT NULL COMMENT '公告正文',
    `teacher_id`   bigint(20)   NULL DEFAULT NULL COMMENT '发布教师ID（sys_user.id）',
    `teacher_name` varchar(64)  NULL DEFAULT NULL COMMENT '发布教师姓名（冗余，列表直接展示）',
    `create_time`  datetime     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `update_time`  datetime     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`   tinyint(4)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_course_time` (`course_id`, `create_time`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程公告表' ROW_FORMAT = Dynamic;
