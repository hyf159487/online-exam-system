-- =====================================================
-- 系统参数配置表：键值对存储，全系统生效
--
-- 设计要点：
--   1. 参数由初始化脚本预置，只允许修改，不允许新增/删除
--   2. config_type 区分参数类型：number=数字输入框 / boolean=开关 / string=文本
--   3. config_key 唯一，后端业务代码按 key 读取
--   4. 后端用内存缓存（ConcurrentHashMap）避免每次读数据库
-- =====================================================

DROP TABLE IF EXISTS `sys_config`;

CREATE TABLE `sys_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_name` VARCHAR(64)  NOT NULL COMMENT '参数名称（中文，页面展示用）',
  `config_key`  VARCHAR(64)  NOT NULL COMMENT '参数编码 key（业务代码读取用，唯一）',
  `config_value` VARCHAR(255) NOT NULL COMMENT '参数值（数字存字符串如\"24\"，开关存 \"true\"/\"false\"）',
  `config_type` VARCHAR(16)  NOT NULL DEFAULT 'number' COMMENT '参数类型：number=数字 / boolean=开关 / string=文本',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '参数说明',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数配置表';

-- 种子数据：4 项系统参数
INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `remark`) VALUES
  ('Token有效期（小时）',     'token_expiry_hours',    '24',  'number',  '用户登录令牌过期时间，超时需重新登录'),
  ('考试超时自动交卷（分钟）', 'exam_timeout_minutes',  '60',  'number',  '学生考试长时间无操作自动提交试卷的超时时间'),
  ('防作弊开关',              'anti_cheat_enabled',    'true','boolean','开启后启用切屏检测，切屏超过次数强制交卷'),
  ('默认分页条数',            'default_page_size',     '10',  'number',  '各个列表页面默认每页展示的数据条数');
