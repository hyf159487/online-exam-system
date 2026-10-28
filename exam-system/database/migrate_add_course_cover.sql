-- =====================================================
-- 课程封面：课程管理可为课程上传封面图片
--
-- 1) sys_course 增加 cover（封面图访问路径，形如 /uploads/course-cover/202609/xxx.png）
-- 2) 封面由「课程管理」新增/编辑课程的「课程封面」项上传（仅管理员），
--    也可直接填外部图片 URL；为空时学生端卡片展示默认渐变占位图。
-- 3) 学生端「加入课程」改为卡片视图展示：封面 + 课程名 + 授课教师 + 简介 + 参加人数
-- =====================================================

ALTER TABLE `sys_course`
    ADD COLUMN `cover` VARCHAR(500) NULL DEFAULT NULL
        COMMENT '课程封面图访问路径（/uploads/course-cover/... 或外部图片 URL）'
        AFTER `description`;
