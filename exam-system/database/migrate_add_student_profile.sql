-- 学生端「个人信息」功能迁移脚本
-- 为 sys_user 增加邮箱字段，供学生自行维护联系方式
-- 复用 sys_user 用户表与 sys_class 班级表，不新建数据表

ALTER TABLE `sys_user`
    ADD COLUMN `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱（学生个人信息维护）'
    AFTER `phone`;
