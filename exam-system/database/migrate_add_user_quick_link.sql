-- =====================================================
-- 首页「快捷入口」自定义配置：user_quick_link 表
--
--   1) 新建 user_quick_link 表：一行一个入口，保存 用户ID / 名称 / 路由 / 图标 / 顺序
--      配置跟账号走，同一用户换浏览器、换电脑登录后仍保留自己挑的入口
--   2) 保存策略是「先按 user_id 全删、再批量插入」，条数上限 20 条（后端已限制）
--   3) 不涉及菜单表与角色授权：入口池直接来自用户已有的菜单权限，无需新增菜单
--
-- 幂等：可重复执行，不会重复建表。
-- 执行：mysql -u root -p exam_system < migrate_add_user_quick_link.sql
-- =====================================================

-- ---------- 1. 快捷入口表 ----------
CREATE TABLE IF NOT EXISTS `user_quick_link` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID（sys_user.id）',
  `label`       VARCHAR(50)  NOT NULL COMMENT '入口名称，如 待审核试题',
  `route_path`  VARCHAR(200) NOT NULL COMMENT '路由地址，如 /audit/pending',
  `icon`        VARCHAR(50)  DEFAULT NULL COMMENT '图标名（Element Plus 图标组件名，如 Checked）',
  `sort_no`     INT          NOT NULL DEFAULT 0 COMMENT '展示顺序，越小越靠前',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自定义快捷入口表';

-- ---------- 2. 结果自检（一行汇总：表已建 / 列数 应为 1 / 8） ----------
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_quick_link')          AS `入口表已建`,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_quick_link')          AS `字段数`,
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_quick_link'
     AND INDEX_NAME = 'idx_user_id')                                            AS `用户索引已建`;

-- ---------- 3. 已保存的配置明细（刚建表时为空，配置过首页快捷入口后这里能看到） ----------
SELECT l.`id`, l.`user_id`, u.`username`, l.`label`, l.`route_path`, l.`icon`,
       l.`sort_no`, l.`update_time`
FROM `user_quick_link` l
LEFT JOIN `sys_user` u ON u.`id` = l.`user_id`
ORDER BY l.`user_id`, l.`sort_no`;
