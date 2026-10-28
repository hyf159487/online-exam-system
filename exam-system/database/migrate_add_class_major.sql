-- 为班级表增加专业字段
-- 已存在 major 字段时无需重复执行
ALTER TABLE `sys_class`
  ADD COLUMN `major` varchar(100) DEFAULT NULL COMMENT '专业'
  AFTER `department`;
