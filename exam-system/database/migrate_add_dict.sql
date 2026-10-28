-- =====================================================
-- 字典管理模块：两层结构（字典类型 + 字典子项）
--
-- 设计要点：
--   1. sys_dict_type：字典分类（如 question_type 题型、difficulty 难度）
--      - is_builtin=1 为系统内置，禁止删除
--   2. sys_dict_item：具体下拉选项
--      - item_value 为存储值（如 1/2/3），item_label 为显示文本
--      - enabled=1 启用 / 0 禁用（禁用后业务下拉框看不到，但数据保留）
--      - sort_order 控制下拉框展示顺序
--   3. 前端业务页面通过 /api/dict/items/{typeCode} 动态获取选项
-- =====================================================

-- ---------- 1. 字典类型表 ----------
DROP TABLE IF EXISTS `sys_dict_item`;
DROP TABLE IF EXISTS `sys_dict_type`;

CREATE TABLE `sys_dict_type` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
  `type_name`   VARCHAR(64)  NOT NULL COMMENT '字典类型名称（如：题型、难度）',
  `type_code`   VARCHAR(64)  NOT NULL COMMENT '字典编码（如：question_type、difficulty），前端按此编码拉选项',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_builtin`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否系统内置：1=是 0=否（内置禁止删除）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- ---------- 2. 字典子项表 ----------
CREATE TABLE `sys_dict_item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典子项ID',
  `type_id`     BIGINT       NOT NULL COMMENT '字典类型ID',
  `item_label`  VARCHAR(64)  NOT NULL COMMENT '显示标签（如下拉框显示的「单选题」）',
  `item_value`  VARCHAR(64)  NOT NULL COMMENT '存储值（如下拉框提交的 1、2、3）',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序号（升序）',
  `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type_id` (`type_id`),
  KEY `idx_type_value` (`type_id`, `item_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典子项表';

-- ---------- 3. 种子数据：系统内置字典类型 ----------

-- 题型（is_builtin=1，禁止删除）
INSERT INTO `sys_dict_type` (`id`, `type_name`, `type_code`, `remark`, `is_builtin`) VALUES
  (1, '题型', 'question_type', '题目类型：单选/多选/判断/填空/简答', 1),
  (2, '难度', 'difficulty', '题目难度：简单/中等/困难', 1),
  (3, '考试状态', 'exam_status', '考试状态：未开始/进行中/已结束', 1),
  (4, '考试题型分布', 'exam_question_types', '试卷中各题型分布配置', 0),
  (5, '组卷方式', 'paper_type', '组卷方式：1手动组卷 2随机组卷', 0);

-- 题型子项（item_value 存数字，对应 sys_question.question_type）
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`) VALUES
  (1, '单选题', '1', 1, 1),
  (1, '多选题', '2', 2, 1),
  (1, '判断题', '3', 3, 1),
  (1, '填空题', '4', 4, 1),
  (1, '简答题', '5', 5, 1);

-- 难度子项
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`) VALUES
  (2, '简单', '1', 1, 1),
  (2, '中等', '2', 2, 1),
  (2, '困难', '3', 3, 1);

-- 考试状态子项
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`) VALUES
  (3, '未开始', '0', 1, 1),
  (3, '进行中', '1', 2, 1),
  (3, '已结束', '2', 3, 1);

-- 组卷方式子项（item_value 存数字，对应 exam_paper.paper_type）
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`) VALUES
  (5, '手动组卷', '1', 1, 1),
  (5, '随机组卷', '2', 2, 1),
  (5, 'AI智能组卷', '3', 3, 1);
