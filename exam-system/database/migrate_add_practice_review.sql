-- =====================================================
-- 「批改作业」功能：practice_answer / practice_record 增加批改相关字段
--
--   背景：练习卷（作业）里的简答题(5)、名词解释(6) 提交时不自动判分，
--         历史上这些题的得分恒为 0，教师端也没有"待批改"状态。
--         本脚本补齐字段，配合教师端「作业管理 → 批改作业」页面使用。
--
-- 字段含义：
--   practice_answer.is_objective   1客观题（提交即判分） / 0主观题（需教师批改）
--   practice_answer.review_score   教师批改得分（主观题）
--   practice_answer.review_status  0待批改 / 1已批改（客观题提交即视为已批改）
--   practice_answer.review_opinion 教师评语
--   practice_answer.review_*       批改教师与批改时间
--   practice_record.review_status  0无需批改（无主观题） / 1待批改 / 2已批改
--   practice_record.subjective_count 主观题题数
--
-- 注意：本脚本为一次性 ALTER，请勿重复执行（重复执行会报 Duplicate column name）。
--      用 数据库转存.sql 新建库后也需要执行本脚本。
--
-- 执行：mysql -u root -p exam_system < migrate_add_practice_review.sql
-- =====================================================

-- ---------- 1. practice_answer：批改字段 ----------
ALTER TABLE `practice_answer`
  ADD COLUMN `is_objective`   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否客观题：1客观题（提交即判分） 0主观题（需教师批改）' AFTER `question_type`,
  ADD COLUMN `review_score`   INT          DEFAULT NULL COMMENT '教师批改得分（主观题）' AFTER `get_score`,
  ADD COLUMN `review_status`  TINYINT      NOT NULL DEFAULT 1 COMMENT '批改状态：0待批改 1已批改' AFTER `review_score`,
  ADD COLUMN `review_opinion` VARCHAR(255) DEFAULT NULL COMMENT '教师评语' AFTER `review_status`,
  ADD COLUMN `review_user_id` BIGINT       DEFAULT NULL COMMENT '批改教师ID（sys_user.id）' AFTER `review_opinion`,
  ADD COLUMN `review_time`    DATETIME     DEFAULT NULL COMMENT '批改时间' AFTER `review_user_id`;

-- 主观题作答是"非对非错"：批改前 is_correct 允许 NULL（原为 NOT NULL DEFAULT 0）
ALTER TABLE `practice_answer`
  MODIFY COLUMN `is_correct` TINYINT NULL DEFAULT 0 COMMENT '是否答对：0错 1对（主观题批改前为 NULL）';

-- ---------- 2. practice_record：批改状态与主观题题数 ----------
ALTER TABLE `practice_record`
  ADD COLUMN `review_status`    TINYINT NOT NULL DEFAULT 0 COMMENT '批改状态：0无需批改 1待批改 2已批改' AFTER `finish_status`,
  ADD COLUMN `subjective_count` INT     NOT NULL DEFAULT 0 COMMENT '主观题题数' AFTER `review_status`;

-- ---------- 3. 历史数据回填 ----------
-- 3.1 按题型标记客观/主观：单选1 多选2 判断3 填空4 = 客观；简答5 名词解释6 = 主观
UPDATE `practice_answer` SET `is_objective` = IF(`question_type` IN (5, 6), 0, 1);

-- 3.2 客观题提交即视为已批改，主观题待批改
UPDATE `practice_answer` SET `review_status` = IF(`is_objective` = 1, 1, 0);

-- 3.3 回填记录的批改状态：含主观题的记录 = 待批改，客观题全对的记录无需批改
UPDATE `practice_record` pr
  JOIN (
    SELECT `practice_record_id`, COUNT(*) AS cnt
      FROM `practice_answer`
     WHERE `is_objective` = 0
     GROUP BY `practice_record_id`
  ) t ON t.`practice_record_id` = pr.`id`
   SET pr.`subjective_count` = t.cnt,
       pr.`review_status`    = 1;

-- ---------- 4. 结果自检 ----------
-- 期望：待批改记录数 = 含主观题的记录数；主观题明细均为 review_status = 0
SELECT
  (SELECT COUNT(*) FROM `practice_answer` WHERE `is_objective` = 0)                     AS `主观题明细数`,
  (SELECT COUNT(*) FROM `practice_answer` WHERE `is_objective` = 0 AND `review_status` = 0) AS `待批改明细数`,
  (SELECT COUNT(*) FROM `practice_record` WHERE `review_status` = 1)                    AS `待批改记录数`,
  (SELECT COUNT(*) FROM `practice_record` WHERE `review_status` = 0)                    AS `无需批改记录数`;

SHOW COLUMNS FROM `practice_answer`;
