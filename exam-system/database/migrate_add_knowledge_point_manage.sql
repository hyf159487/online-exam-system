-- =====================================================
-- 知识点管理模块：knowledge_point 扩列 + 「知识点管理」菜单
-- 知识点归属课程；管理员统一维护（教师端不开放菜单，仅在录入试题时
-- 按课程加载知识点下拉——只读），供题目绑定 question.knowledge_id，
-- 供班级学情分析「知识点正确率」/ 试题质量分析 使用。
--
-- 菜单结构调整（幂等，与 migrate_add_study_resource.sql 执行顺序无关）：
--   课程管理（一级分组）
--     ├── 课程列表         /courses            （管理员）
--     ├── 知识点管理       /knowledge-points   （管理员，本脚本新增）
--     └── 学习资料管理     /study-resources    （管理员 + 教师）
--
-- 本脚本可重复执行：只做「补列/补菜单/补授权」，不清数据、不删行。
-- =====================================================

-- 1. knowledge_point 扩列（MySQL 8 原生不支持 ADD COLUMN IF NOT EXISTS，
--    这里用 information_schema 判断列是否存在 + 动态 SQL 逐列添加，保证可重复执行）

-- 1.1 知识点编码 knowledge_code
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_point' AND COLUMN_NAME = 'knowledge_code');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `knowledge_point` ADD COLUMN `knowledge_code` VARCHAR(64) DEFAULT NULL COMMENT ''知识点编码'' AFTER `point_name`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 备注 remark
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_point' AND COLUMN_NAME = 'remark');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `knowledge_point` ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT ''备注'' AFTER `sort_order`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 创建时间 create_time
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_point' AND COLUMN_NAME = 'create_time');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE `knowledge_point` ADD COLUMN `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'' AFTER `remark`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------
-- 2. 菜单：定位「课程管理」顶级菜单
-- -----------------------------------------------------
SET @course_menu_id := (
  SELECT `id` FROM `sys_menu`
  WHERE `parent_id` = 0 AND `layout` = 'admin'
    AND (`route_path` = '/courses' OR `menu_name` = '课程管理')
  ORDER BY `id` LIMIT 1
);

-- 2.1 课程管理改成分组（若之前未分组，即 route 还是 /courses）
UPDATE `sys_menu`
SET `route_path` = NULL
WHERE `id` = @course_menu_id AND `route_path` = '/courses';

-- 2.2 课程列表子菜单（若无则插入，管理员原有入口 /courses 不丢）
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT @course_menu_id, '课程列表', '/courses', 'Reading', 1, 1, 'admin'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` x WHERE x.parent_id = @course_menu_id AND x.route_path = '/courses'
);

-- 2.3 知识点管理子菜单（本脚本核心新增）
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT @course_menu_id, '知识点管理', '/knowledge-points', 'Collection', 2, 1, 'admin'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` x WHERE x.parent_id = @course_menu_id AND x.route_path = '/knowledge-points'
);

-- 2.4 学习资料管理子菜单（若无则插入；有则把排序挪到知识点后面）
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`)
SELECT @course_menu_id, '学习资料管理', '/study-resources', 'FolderOpened', 3, 1, 'admin'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` x WHERE x.parent_id = @course_menu_id AND x.route_path = '/study-resources'
);
UPDATE `sys_menu`
SET `sort_order` = 3
WHERE `parent_id` = @course_menu_id AND `route_path` = '/study-resources' AND `sort_order` < 3;

-- 清理旧版可能残留的顶级「学习资料管理」（历史版本曾建过顶级菜单）
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `id` FROM `sys_menu` WHERE `route_path` = '/study-resources' AND `parent_id` = 0);
DELETE FROM `sys_menu` WHERE `route_path` = '/study-resources' AND `parent_id` = 0;

-- -----------------------------------------------------
-- 3. 角色授权
-- -----------------------------------------------------
-- 管理员(role_id=3)：课程管理分组下全部子菜单（课程列表 / 知识点管理 / 学习资料管理）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 3, `id` FROM `sys_menu`
WHERE `parent_id` = @course_menu_id
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm WHERE rm.role_id = 3 AND rm.menu_id = `sys_menu`.`id`
  );

-- 教师(role_id=2)：授「课程管理」分组（保证子菜单不冒顶成顶级）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `id` FROM `sys_menu`
WHERE `id` = @course_menu_id
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm WHERE rm.role_id = 2 AND rm.menu_id = @course_menu_id
  );
-- 教师只授「学习资料管理」：知识点管理不授（教师无知识点管理菜单，仅录入题时只读加载）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `id` FROM `sys_menu`
WHERE `parent_id` = @course_menu_id AND `route_path` = '/study-resources'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` rm WHERE rm.role_id = 2 AND rm.menu_id = `sys_menu`.`id`
  );
