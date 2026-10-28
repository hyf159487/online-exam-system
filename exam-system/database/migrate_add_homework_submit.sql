-- =====================================================
-- 文件型作业「学生提交」：practice_record 增加提交附件与教师批改字段
--
-- 场景：教师上传作业文件（附件型作业，practice_paper.attachment_path 非空）后，
--       学生下载 → 线下作答 → 在「作业列表」点「提交」上传自己的作业文件。
--       学生的一次提交就是 practice_record 的一条记录（attachment_path 非空即视为文件型提交），
--       因此天然复用教师端「作业管理 → 批改作业」工作台。
--
-- 字段说明（practice_record）：
--   attachment_name   学生提交的附件原始文件名
--   attachment_path   学生提交的附件访问路径 /uploads/homework/yyyyMM/uuid.ext
--   attachment_size   学生提交附件字节数
--   attachment_type   附件归类：pdf/word/ppt/excel/text/compress/image
--   submit_remark     学生提交备注（选填）
--   review_opinion    教师评语（文件型作业没有逐题明细，评语直接落在记录上）
--   review_user_id    批改教师ID（sys_user.id）
--   review_time       批改时间
--
-- 约定：
--   学生提交后 review_status = 1（待批改）；教师给分后 review_status = 2（已批改）；
--   文件型作业没有题目分值，给分上限由教师端在 total_score > 0 时取 total_score，否则取 100。
--
-- 幂等，可重复执行。
-- 执行：mysql -u root -p exam_system < migrate_add_homework_submit.sql
-- =====================================================

-- ---------- 1. 学生提交附件：原始文件名 ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'attachment_name'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `attachment_name` VARCHAR(255) DEFAULT NULL COMMENT ''学生提交的作业附件原始文件名（文件型作业）'' AFTER `subjective_count`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 学生提交附件：访问路径 ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'attachment_path'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `attachment_path` VARCHAR(500) DEFAULT NULL COMMENT ''学生提交的作业附件访问路径 /uploads/homework/...'' AFTER `attachment_name`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 学生提交附件：字节数 ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'attachment_size'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `attachment_size` BIGINT DEFAULT NULL COMMENT ''学生提交附件字节数'' AFTER `attachment_path`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. 学生提交附件：类型归类 ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'attachment_type'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `attachment_type` VARCHAR(16) DEFAULT NULL COMMENT ''学生提交附件类型：pdf/word/ppt/excel/text/compress/image'' AFTER `attachment_size`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5. 学生提交备注 ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'submit_remark'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `submit_remark` VARCHAR(255) DEFAULT NULL COMMENT ''学生提交备注（选填）'' AFTER `attachment_type`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 6. 教师评语 ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'review_opinion'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `review_opinion` VARCHAR(255) DEFAULT NULL COMMENT ''教师评语（文件型作业）'' AFTER `submit_remark`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 7. 批改教师ID ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'review_user_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `review_user_id` BIGINT DEFAULT NULL COMMENT ''批改教师ID（sys_user.id）'' AFTER `review_opinion`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 8. 批改时间 ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND COLUMN_NAME = 'review_time'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `practice_record` ADD COLUMN `review_time` DATETIME DEFAULT NULL COMMENT ''批改时间'' AFTER `review_user_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 9. 索引：学生端列表取「我的提交」、教师端按作业+学生过滤 ----------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
    AND INDEX_NAME = 'idx_paper_student'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `practice_record` ADD INDEX `idx_paper_student` (`practice_paper_id`, `student_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 10. 结果自检 ----------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'practice_record'
   AND COLUMN_NAME IN ('attachment_name', 'attachment_path', 'attachment_size', 'attachment_type',
                       'submit_remark', 'review_opinion', 'review_user_id', 'review_time')
 ORDER BY ORDINAL_POSITION;
