-- =====================================================
-- 菜单合并：「试题审核管理」并入「题库管理」
--
-- 调整前：题库管理(我的试题 / 高频错题库) + 试题审核管理(待审核试题 / 审核历史记录) 两个顶级菜单
-- 调整后：题库管理(我的试题 → 高频错题库 → 待审核试题 → 审核历史记录)
--
-- 只动菜单展示，不动任何接口权限：
--   /api/audit/** 仍由 AuthInterceptor 限定「仅管理员」，教师端本就看不到这两个子菜单。
--
-- 幂等：可重复执行，不会产生重复菜单或重复授权。
-- =====================================================

SET @question_bank := (SELECT `id` FROM `sys_menu`
                       WHERE `menu_name` = '题库管理' AND `layout` = 'admin'
                       ORDER BY `id` LIMIT 1);

SET @audit_menu := (SELECT `id` FROM `sys_menu`
                    WHERE `menu_name` = '试题审核管理' AND `layout` = 'admin'
                    ORDER BY `id` LIMIT 1);

-- ---------- 1. 两个审核子菜单挂到「题库管理」下 ----------
UPDATE `sys_menu`
SET `parent_id` = @question_bank, `sort_order` = 3, `visible` = 1
WHERE `menu_name` = '待审核试题' AND `layout` = 'admin' AND @question_bank IS NOT NULL;

UPDATE `sys_menu`
SET `parent_id` = @question_bank, `sort_order` = 4, `visible` = 1
WHERE `menu_name` = '审核历史记录' AND `layout` = 'admin' AND @question_bank IS NOT NULL;

-- ---------- 2. 原有子菜单顺序固定为 1 / 2 ----------
UPDATE `sys_menu`
SET `parent_id` = @question_bank, `sort_order` = 1
WHERE `menu_name` = '我的试题' AND `layout` = 'admin' AND @question_bank IS NOT NULL;

UPDATE `sys_menu`
SET `parent_id` = @question_bank, `sort_order` = 2
WHERE `menu_name` = '高频错题库' AND `layout` = 'admin' AND @question_bank IS NOT NULL;

-- ---------- 3. 删除原来的顶级父菜单（先清授权关系，再删菜单本身） ----------
DELETE FROM `sys_role_menu`
WHERE `menu_id` = @audit_menu;

DELETE FROM `sys_menu`
WHERE `id` = @audit_menu;

-- ---------- 4. 结果核对：应为 4 行，顺序 1~4 ----------
SELECT `id`, `parent_id`, `menu_name`, `route_path`, `sort_order`
FROM `sys_menu`
WHERE `parent_id` = @question_bank
ORDER BY `sort_order`;
