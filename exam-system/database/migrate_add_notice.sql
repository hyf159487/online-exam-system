-- =====================================================
-- 消息通知模块：sys_notice（通知主表）+ sys_user_notice（用户接收表）
-- 教师/管理员给学生发布站内消息，学生端右上角铃铛查看未读消息
-- =====================================================

-- 1. 创建通知主表
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`               VARCHAR(128) NOT NULL COMMENT '通知标题',
  `content`             TEXT         NOT NULL COMMENT '通知正文内容',
  `receive_type`        TINYINT      NOT NULL COMMENT '接收对象：1全部学生，2指定班级，3指定考试的考生',
  `target_class_ids`    VARCHAR(512) DEFAULT NULL COMMENT '选中班级id集合，逗号分隔（receive_type=2时使用）',
  `target_exam_id`      BIGINT       DEFAULT NULL COMMENT '指定考试ID（receive_type=3时使用）',
  `send_user_id`        BIGINT       NOT NULL COMMENT '发送人ID（教师/管理员，sys_user.id）',
  `send_user_name`      VARCHAR(64)  DEFAULT NULL COMMENT '发送人姓名（冗余）',
  `send_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `is_deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_send_user_id` (`send_user_id`),
  KEY `idx_send_time` (`send_time`),
  KEY `idx_receive_type` (`receive_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知主表';

-- 2. 创建用户通知接收表：每个学生一条记录，记录已读/未读
DROP TABLE IF EXISTS `sys_user_notice`;
CREATE TABLE `sys_user_notice` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `notice_id`           BIGINT       NOT NULL COMMENT '关联sys_notice主键',
  `user_id`             BIGINT       NOT NULL COMMENT '接收学生ID（sys_user.id）',
  `is_read`             TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读，1已读',
  `read_time`           DATETIME     DEFAULT NULL COMMENT '阅读时间',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知接收记录表';
