-- =====================================================
-- 学习资料管理模块：study_resource 表 + 菜单（挂在「课程管理」下）
-- 学习资料归属课程，管理员/教师上传（PDF/Word/PPT/Excel 等），
-- 学生在「自主练习」页查看并下载对应课程资料。
--
-- 菜单结构调整（与方案一致）：
--   课程管理（原叶子 /courses，改成分组）
--     ├── 课程列表         /courses           （原课程管理入口，管理员可见）
--     └── 学习资料管理     /study-resources   （管理员 + 教师）
--
-- 权限说明：
--   - 管理员(role_id=3)：课程管理分组 + 课程列表 + 学习资料管理
--   - 教师  (role_id=2)：课程管理分组 + 学习资料管理（不授课程列表，
--     因此教师侧边栏只出现「课程管理 ▸ 学习资料管理」）
--   - 学生  (role_id=1)：无该菜单，学生在自主练习页查看资料
-- 本脚本可重复执行（先清理再重建）。
-- =====================================================

-- 1. study_resource 学习资料表
CREATE TABLE IF NOT EXISTS `study_resource` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id`           BIGINT       NOT NULL COMMENT '所属课程ID（sys_course.id）',
  `resource_name`       VARCHAR(255) NOT NULL COMMENT '资料名称',
  `file_type`           VARCHAR(20)  DEFAULT NULL COMMENT '文件类型：pdf/word/ppt/excel/text/compress/other',
  `file_path`           VARCHAR(500) NOT NULL COMMENT '文件存储相对路径（/uploads/study-resource/...，可直接/API访问）',
  `file_size`           BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_size_text`      VARCHAR(50)  DEFAULT NULL COMMENT '文件大小显示文本（如 1.2 MB）',
  `upload_teacher_id`   BIGINT       DEFAULT NULL COMMENT '上传人ID（sys_user.id）',
  `upload_teacher_name` VARCHAR(64)  DEFAULT NULL COMMENT '上传人姓名（冗余，列表直接展示）',
  `description`         VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `is_deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_upload_teacher` (`upload_teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习资料表';

-- -----------------------------------------------------
-- 2. 菜单：把「课程管理」从叶子改成分组，下面挂 课程列表 / 学习资料管理
-- -----------------------------------------------------

-- 2.1 定位「课程管理」顶级菜单（按 parent_id=0 + layout=admin + route=/courses 找，
--     避免硬编码 id，兼容不同环境）
SET @course_menu_id := (
  SELECT `id` FROM `sys_menu`
  WHERE `parent_id` = 0 AND `layout` = 'admin' AND `route_path` = '/courses'
  ORDER BY `id` LIMIT 1
);

-- 2.2 清理旧的「学习资料管理」（可能是旧版顶级菜单，也可能是本脚本残留）及旧授权
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (
  SELECT `id` FROM `sys_menu`
  WHERE `route_path` = '/study-resources'
     OR (`route_path` = '/courses' AND `parent_id` = @course_menu_id)
);
DELETE FROM `sys_menu`
WHERE `route_path` = '/study-resources'
   OR (`route_path` = '/courses' AND `parent_id` = @course_menu_id);

-- 2.3 课程管理改为分组（route_path 置空，仍保留图标/排序，仅作为下拉分组）
UPDATE `sys_menu`
SET `route_path` = NULL
WHERE `id` = @course_menu_id;

-- 2.4 插入子菜单：课程列表（沿用原 /courses 页面）+ 学习资料管理
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `route_path`, `icon`, `sort_order`, `visible`, `layout`) VALUES
(@course_menu_id, '课程列表',     '/courses',          'Reading',      1, 1, 'admin'),
(@course_menu_id, '学习资料管理', '/study-resources',  'FolderOpened', 2, 1, 'admin');

-- -----------------------------------------------------
-- 3. 角色授权
-- -----------------------------------------------------

-- 管理员(role_id=3)：课程管理分组下全部子菜单（课程列表 + 学习资料管理）
--   分组父菜单 24 管理员本来就有，无需重复插入
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 3, `id` FROM `sys_menu` WHERE `parent_id` = @course_menu_id;

-- 教师(role_id=2)：授「课程管理」分组 + 仅「学习资料管理」子菜单
--   分组：课程管理（父菜单进树，子菜单才不会冒顶成顶级菜单）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `id` FROM `sys_menu`
WHERE `id` = @course_menu_id
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` = @course_menu_id);
--   子菜单：只给学习资料管理（课程列表仍是管理员专属）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `id` FROM `sys_menu`
WHERE `parent_id` = @course_menu_id AND `route_path` = '/study-resources';
