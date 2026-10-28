-- =====================================================
-- 作业附件：practice_paper 增加「文件型作业」字段
--
-- 场景：新增/编辑作业时，作业内容有两种来源
--   1) 从题库选题 —— 走 practice_paper_question（原有逻辑，不改）
--   2) 上传作业文件 —— 作业就是一份文件（Word/PDF/图片等），
--      学生下载或在线查看后线下作答，不做在线判分
--
-- 字段说明：
--   attachment_name  附件原始文件名（教师端/学生端展示用）
--   attachment_path  服务器相对访问路径 /uploads/homework/yyyyMM/uuid.ext
--                    （由 WebConfig 静态映射 /uploads/** 提供预览/下载）
--   attachment_size  附件字节数
--   attachment_type  附件归类：pdf/word/ppt/excel/text/compress/image
--
-- 可重复执行（先判断字段是否存在，存在则跳过）。
-- =====================================================

-- 1. 附件原始文件名
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_paper'
    AND COLUMN_NAME = 'attachment_name'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_paper` ADD COLUMN `attachment_name` VARCHAR(255) DEFAULT NULL COMMENT ''作业附件原始文件名（文件型作业）'' AFTER `is_deleted`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 附件访问路径
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_paper'
    AND COLUMN_NAME = 'attachment_path'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_paper` ADD COLUMN `attachment_path` VARCHAR(500) DEFAULT NULL COMMENT ''作业附件访问路径 /uploads/homework/...'' AFTER `attachment_name`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 附件字节数
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_paper'
    AND COLUMN_NAME = 'attachment_size'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_paper` ADD COLUMN `attachment_size` BIGINT DEFAULT NULL COMMENT ''作业附件字节数'' AFTER `attachment_path`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 附件类型归类
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_paper'
    AND COLUMN_NAME = 'attachment_type'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_paper` ADD COLUMN `attachment_type` VARCHAR(16) DEFAULT NULL COMMENT ''作业附件类型：pdf/word/ppt/excel/text/compress/image'' AFTER `attachment_size`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
