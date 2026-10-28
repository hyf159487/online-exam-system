-- =====================================================
-- 操作日志表：管理员审计页面的数据源
-- 设计要点：
--   1. 只记录关键写操作（新增/修改/删除/批量/导入/导出/发布/重置密码/权限变更）
--      普通 SELECT / 详情查询不记录，防止日志表暴涨
--   2. 表本身不带更新接口（仅 INSERT/SELECT），保证审计数据不可篡改
--   3. 关键查询字段建索引：操作人、模块、状态、操作时间
-- =====================================================
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator_id`     BIGINT       DEFAULT NULL COMMENT '操作人ID（sys_user.id）',
  `operator_name`   VARCHAR(64)  DEFAULT NULL COMMENT '操作人账号/姓名',
  `module`          VARCHAR(32)  NOT NULL COMMENT '操作模块：题库管理/试卷管理/考试管理/账号管理/试题审核管理/系统管理',
  `operation_type`  VARCHAR(32)  NOT NULL COMMENT '操作类型：新增/修改/删除/批量删除/批量新增/导入/导出/发布考试/终止考试/重置密码/权限变更',
  `description`     VARCHAR(512) DEFAULT NULL COMMENT '操作描述（含业务数据摘要）',
  `business_id`     VARCHAR(64)  DEFAULT NULL COMMENT '业务ID（题目/试卷/考试ID等，多ID用逗号分隔）',
  `request_url`     VARCHAR(255) DEFAULT NULL COMMENT '请求地址',
  `request_method`  VARCHAR(10)  DEFAULT NULL COMMENT '请求方式：GET/POST/PUT/DELETE',
  `client_ip`       VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '操作状态：1=成功 0=失败',
  `error_msg`       TEXT         COMMENT '操作失败时的异常信息（成功为 NULL）',
  `cost_ms`         INT          DEFAULT NULL COMMENT '接口耗时（毫秒）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（精确到秒）',
  PRIMARY KEY (`id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_operator_name` (`operator_name`),
  KEY `idx_module` (`module`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表（审计留痕，只增不改）';
