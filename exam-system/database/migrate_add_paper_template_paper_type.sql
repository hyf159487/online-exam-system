-- =============================================================
-- 组卷模板支持「组卷方式」+ AI 补充说明
--
-- 1) paper_type：模板的组卷方式，取值与 exam_paper.paper_type 一致
--    - 1 手动组卷：模板只保存试卷基本信息，题目到组卷页逐题挑选
--    - 2 随机组卷：按 template_json 里的组卷明细自动抽题（原有行为）
--    - 3 AI 智能组卷：template_json 作为 AI 组卷的题型/分值参考，
--      另可用 ai_requirement 写一句自然语言要求
--    默认 2，历史模板行为不变
-- 2) ai_requirement：AI 智能组卷的补充说明，仅 paper_type = 3 时有意义
--
-- 执行前提：migrate_add_paper_template.sql 已执行
-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，
--       若本脚本已执行过，重复执行会报 1060（Duplicate column name），忽略即可
-- =============================================================

ALTER TABLE `paper_template`
    ADD COLUMN `paper_type` INT NOT NULL DEFAULT 2
        COMMENT '组卷方式：1手动 2随机 3AI智能' AFTER `total_question_count`,
    ADD COLUMN `ai_requirement` VARCHAR(500) DEFAULT NULL
        COMMENT 'AI 智能组卷补充说明（paper_type=3 时使用）' AFTER `paper_type`;

-- 历史模板统一视为随机组卷（模板里本来存的就是随机抽题明细）
UPDATE `paper_template` SET `paper_type` = 2 WHERE `paper_type` IS NULL;
