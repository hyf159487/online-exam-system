-- =====================================================
-- 加入课程页面优化：课程表增加「开课时间」「报名截止时间」
--
-- 1) sys_course 增加 start_time（开课时间）、enroll_deadline（报名截止时间）
--    报名截止说明：enroll_deadline 为空表示长期开放报名；
--    非空且在截止时间之后，学生端按钮显示【报名截止】并禁用。
-- =====================================================

ALTER TABLE `sys_course`
    ADD COLUMN `start_time` DATETIME DEFAULT NULL COMMENT '开课时间（学生端展示课程何时开始）'
        AFTER `description`,
    ADD COLUMN `enroll_deadline` DATETIME DEFAULT NULL COMMENT '报名截止时间（为空=长期开放报名）'
        AFTER `start_time`;

-- ---------- 可选：为演示数据补充开课时间 / 报名截止时间 ----------
-- 已开课：开课时间早于当前，报名截止时间已过（学生端显示【报名截止】）
UPDATE `sys_course`
   SET `start_time`      = '2026-08-25 08:00:00',
       `enroll_deadline` = '2026-09-05 23:59:59'
 WHERE `start_time` IS NULL
   AND `course_code` IN ('JAVA101', 'PY101', 'DB101');
