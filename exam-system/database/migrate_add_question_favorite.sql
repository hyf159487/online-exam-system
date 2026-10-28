-- =====================================================
-- 学生端「我的收藏」：试题收藏功能
--
--   1) 新建 question_favorite 表：只存 学生ID / 试题ID / 收藏时间
--      同一学生对同一道题只保留一条（uk_user_question 唯一约束 + 后端 find-then-insert 双保险）
--   2) 学生端新增一级菜单「我的收藏」 /student/favorites，排在「我的错题」之后
--   3) 授权给学生角色（role_id = 1）
--
-- 说明：
--   - 侧边栏实际显示顺序由前端 StudentLayout.vue 的 STUDENT_MENU_ORDER 决定，
--     sort_order 只用于后端菜单树排序，这里取「我的错题(sort=4) + 1」。
--   - 只新增菜单/表，不改动学生端其它菜单与教师端任何数据。
--
-- 幂等：可重复执行，不会产生重复表/菜单/授权。
-- 执行：mysql -u root -p exam_system < migrate_add_question_favorite.sql
-- =====================================================

-- ---------- 1. 收藏表 ----------
CREATE TABLE IF NOT EXISTS `question_favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`     BIGINT   NOT NULL COMMENT '学生ID（sys_user.id，role=0）',
  `question_id` BIGINT   NOT NULL COMMENT '试题ID（question.id）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试题收藏表';

-- ---------- 2. 新增一级菜单「我的收藏」（不存在时才插入） ----------
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT 0, '我的收藏', '/student/favorites', 'Star', 5, 1, 'student'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `menu_name` = '我的收藏' AND `layout` = 'student'
);

-- 位置 / 顺序 / 图标纠正（防止历史脚本误改成子菜单或改了路由）
UPDATE `sys_menu`
SET `parent_id` = 0,
    `route_path` = '/student/favorites',
    `icon` = 'Star',
    `visible` = 1
WHERE `menu_name` = '我的收藏' AND `layout` = 'student';

-- ---------- 3. 授权给学生角色（role_id = 1） ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`id` FROM `sys_menu` m
WHERE m.`layout` = 'student' AND m.`menu_name` = '我的收藏'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`
  );

-- ---------- 4. 结果自检（一行汇总，看「结果」页即可） ----------
-- 期望：表已建=1、菜单已建=1、已授权数=1，三项都达标即为成功
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_favorite')     AS `收藏表已建`,
  (SELECT COUNT(*) FROM `sys_menu`
   WHERE `layout` = 'student' AND `menu_name` = '我的收藏')                   AS `菜单已建`,
  (SELECT COUNT(*) FROM `sys_role_menu` rm
   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
   WHERE rm.`role_id` = 1 AND m.`layout` = 'student'
     AND m.`menu_name` = '我的收藏')                                          AS `已授权数`;

-- ---------- 5. 明细核对（学生端一级菜单现有顺序） ----------
SELECT m.`id`, m.`parent_id`, m.`menu_name`, m.`route_path`, m.`icon`, m.`sort_order`,
       IF(EXISTS (SELECT 1 FROM `sys_role_menu` rm
                  WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`id`), '已授权', '未授权') AS `学生角色`
FROM `sys_menu` m
WHERE m.`layout` = 'student' AND m.`parent_id` = 0
ORDER BY m.`sort_order`, m.`id`;
