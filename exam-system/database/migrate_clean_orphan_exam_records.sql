-- =====================================================
-- 清理"已删除考试"遗留的孤儿成绩数据
--
-- 背景：此前删除考试只删了 exam 表本身，没有级联删除，
--   导致学生端"我的成绩"仍能看到已删除考试的成绩
--   （试卷名/满分/及格分为空，因为关联的试卷/考试已不存在）。
--
-- 本次改动后，删除考试会自动级联删除：
--   exam_answer(答题明细) → exam_record(成绩记录) → exam_student(监控) → exam
--
-- 本脚本只需执行一次，用于清理【历史】遗留的孤儿数据。
-- 判据：exam_record.exam_id 有值，但 exam_id 指向的考试已不存在。
--   若 exam_id 为 NULL（旧数据未关联考试），无法确认归属，脚本不处理以免误删。
-- =====================================================

-- 1. 清理孤儿答题明细（该成绩记录所属考试已删除）
DELETE `ea` FROM `exam_answer` `ea`
INNER JOIN `exam_record` `r` ON `ea`.`record_id` = `r`.`id`
LEFT JOIN `exam` `e` ON `e`.`id` = `r`.`exam_id`
WHERE `r`.`exam_id` IS NOT NULL
  AND `e`.`id` IS NULL;

-- 2. 清理孤儿成绩记录（考试已删除，学生"我的成绩"不再显示）
DELETE `r` FROM `exam_record` `r`
LEFT JOIN `exam` `e` ON `e`.`id` = `r`.`exam_id`
WHERE `r`.`exam_id` IS NOT NULL
  AND `e`.`id` IS NULL;

-- 3. 清理孤儿考试监控记录（考试已删除）
DELETE `es` FROM `exam_student` `es`
LEFT JOIN `exam` `e` ON `e`.`id` = `es`.`exam_id`
WHERE `e`.`id` IS NULL;

-- 查看还剩多少无归属（exam_id 为 NULL）的成绩记录，供人工判断：
-- SELECT COUNT(*) AS orphan_no_exam_id FROM `exam_record` WHERE `exam_id` IS NULL;
