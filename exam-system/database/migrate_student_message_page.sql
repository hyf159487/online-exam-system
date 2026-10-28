-- 学生端「消息通知」独立菜单页面迁移脚本
-- 复用 sys_notice / sys_user_notice 通知体系（与右上角铃铛同源数据），不新建 sys_message 表
-- 1) sys_user_note 增加逻辑删除字段，学生删消息时仅置 1，后台仍可追溯
-- 2) sys_menu id=42「消息通知」从「个人中心」子菜单提升为顶级独立菜单，路径改为 /student/messages

ALTER TABLE `sys_user_notice`
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1学生已删除（逻辑删除）'
    AFTER `is_read`;

-- 把「消息通知」菜单从个人中心(parent_id=39)子菜单提升为顶级独立菜单
-- routePath 由 /student/profile/notifications 改为 /student/messages
-- sort_order 设为 5（排在「我的错题」4 之后、「个人中心」之前）
UPDATE `sys_menu`
   SET `parent_id` = 0,
       `route_path` = '/student/messages',
       `sort_order` = 5
 WHERE `id` = 42;
