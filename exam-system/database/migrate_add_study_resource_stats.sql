-- =====================================================
-- 学习资料：浏览/下载次数统计
-- 为 study_resource 增加 view_count（浏览次数）与
-- download_count（下载次数）两个字段，用于管理端列表展示。
--
-- 说明：
--   - 浏览：教师/管理员在线预览(PDF)、学生在线预览时各 +1；
--   - 下载：教师/管理员、学生下载资料时各 +1；
--   - 字段默认 0，采用"每次操作原子自增"（UPDATE ... = count + 1）。
--
-- 可重复执行（先判断字段是否存在，存在则改为新增列）。
-- =====================================================

-- 浏览次数
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_resource'
    AND COLUMN_NAME = 'view_count'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `study_resource` ADD COLUMN `view_count` INT NOT NULL DEFAULT 0 COMMENT ''浏览次数'' AFTER `description`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 下载次数
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'study_resource'
    AND COLUMN_NAME = 'download_count'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `study_resource` ADD COLUMN `download_count` INT NOT NULL DEFAULT 0 COMMENT ''下载次数'' AFTER `view_count`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
