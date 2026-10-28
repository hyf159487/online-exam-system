SET NAMES utf8mb4;

ALTER TABLE sys_course
  ADD COLUMN course_type varchar(20) DEFAULT NULL COMMENT '课程类型' AFTER course_code;
