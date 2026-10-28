-- =============================================================
-- 学生端消息「置顶」功能迁移脚本
-- 1) sys_user_notice 增加置顶标记字段
--    - is_top：0 普通 / 1 置顶（学生把重要消息置顶，置顶消息排在列表最前）
-- 2) 「标记未读」复用已有 is_read 字段，无需新增
-- 执行前提：migrate_notice_confirm.sql 已执行
-- =============================================================

ALTER TABLE `sys_user_notice`
    ADD COLUMN `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '0普通 1学生置顶' AFTER `confirm_time`;
