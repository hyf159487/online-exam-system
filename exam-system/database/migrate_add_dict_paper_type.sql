-- =====================================================
-- 新增字典「组卷方式」（type_code = paper_type）
-- 对应 exam_paper.paper_type / 后端 ExamPaper.paperType（Integer）
--   1 = 手动组卷，2 = 随机组卷
--
-- 说明：
--   试卷新增弹窗（普通创建 / 批量创建）、编辑弹窗、试卷列表「组卷方式」列、
--   查看弹窗的组卷方式文案，统一改为读取本字典，
--   对应前端 exam-web/src/composables/usePaperTypeOptions.js；
--   字典未配置（或子项被全部禁用）、接口异常时，前端自动回退到内置的 1 / 2，
--   因此本脚本不执行也不影响业务功能，执行后管理员即可在「字典管理」中维护显示文案。
--
-- 本脚本可重复执行（幂等）。
-- =====================================================

-- 1. 字典类型：组卷方式
INSERT INTO `sys_dict_type` (`type_name`, `type_code`, `remark`, `is_builtin`)
SELECT '组卷方式', 'paper_type', '组卷方式：1手动组卷 2随机组卷', 0
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_dict_type` WHERE `type_code` = 'paper_type'
);

-- 2. 字典子项：手动组卷（item_value=1，对应 exam_paper.paper_type=1）
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`)
SELECT t.`id`, '手动组卷', '1', 1, 1
FROM `sys_dict_type` t
WHERE t.`type_code` = 'paper_type'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` d
    WHERE d.`type_id` = t.`id` AND d.`item_value` = '1'
  );

-- 3. 字典子项：随机组卷（item_value=2，对应 exam_paper.paper_type=2）
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`)
SELECT t.`id`, '随机组卷', '2', 2, 1
FROM `sys_dict_type` t
WHERE t.`type_code` = 'paper_type'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` d
    WHERE d.`type_id` = t.`id` AND d.`item_value` = '2'
  );

-- 4. 字典子项：AI 智能组卷（item_value=3，对应 exam_paper.paper_type=3）
--    注意：paper_type 仅作组卷方式的标记，3 不会触发任何自动抽题；
--    真正的随机抽题 / AI 组卷入口在组卷页（/papers/{id}/compose）。
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`)
SELECT t.`id`, 'AI智能组卷', '3', 3, 1
FROM `sys_dict_type` t
WHERE t.`type_code` = 'paper_type'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` d
    WHERE d.`type_id` = t.`id` AND d.`item_value` = '3'
  );
