-- =====================================================
-- 新增题型「名词解释」（question_type = 6）
-- 名词解释 = 主观题（与简答 5 等价），不自动判分，统一由教师阅卷。
-- 入口：题目录入/编辑弹窗题型下拉；内容存在 question.answer（参考答案）。
--
-- 只需给题型字典 sys_dict_item（type_code=question_type）补一条即可，
-- 前端题录表单的题型下拉会按字典动态加载；其余页面 typeMap 已一并支持 6。
--
-- 本脚本可重复执行（幂等）。
-- =====================================================

-- 1. 题型字典增加「名词解释」（item_value=6，排序在简答之后）
INSERT INTO `sys_dict_item` (`type_id`, `item_label`, `item_value`, `sort_order`, `enabled`)
SELECT t.`id`, '名词解释', '6', 6, 1
FROM `sys_dict_type` t
WHERE t.`type_code` = 'question_type'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_dict_item` d
    WHERE d.`type_id` = t.`id` AND d.`item_value` = '6'
  );

-- 2. 若简答的 sort_order 需要保持在其前，可把名词解释排在 6（已在上方写出），无需其他调整。
