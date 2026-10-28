-- =====================================================
-- 「待审核试题」菜单对教师角色开放
--
-- 背景：
--   1) 「我的试题」只保留审核已通过的题目；待审核 / 已驳回的题目改由「待审核试题」承载；
--   2) 教师需要在「待审核试题」中查看自己提交题目的审核进度，
--      并在「已驳回」中修改题目（保存后自动回到待审核）或直接重提。
--
-- 变化范围：
--   仅给教师角色(role_id=2)追加菜单 8（待审核试题）的可见权限；
--   「审核历史记录」(9)、导出审核历史、审核通过/驳回等写操作仍仅管理员可用
--   —— 由 AuthInterceptor 按路径控制（/api/audit 下仅 waitList、detail、logs 对教师开放），
--      且控制器内教师的数据范围强制收敛为「自己提交的题目」。
--
-- 幂等：可重复执行，不会产生重复授权。
-- =====================================================

SET @audit_menu := (SELECT `id` FROM `sys_menu`
                    WHERE `menu_name` = '待审核试题' AND `layout` = 'admin'
                    ORDER BY `id` LIMIT 1);

SET @teacher_role := (SELECT `id` FROM `sys_role` WHERE `role_key` = 'teacher' LIMIT 1);

-- ---------- 1. 教师角色追加「待审核试题」菜单 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT @teacher_role, @audit_menu
FROM DUAL
WHERE @teacher_role IS NOT NULL
  AND @audit_menu IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = @teacher_role AND rm.`menu_id` = @audit_menu);

-- ---------- 2. 结果核对：教师角色在「题库管理」下应看到 4 个子菜单 ----------
SELECT r.`id` AS role_id, r.`role_name`, m.`id` AS menu_id,
       m.`menu_name`, m.`route_path`, m.`sort_order`
FROM `sys_role_menu` rm
JOIN `sys_role` r ON r.`id` = rm.`role_id`
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE rm.`role_id` = @teacher_role AND m.`parent_id` = 10
ORDER BY m.`sort_order`;
