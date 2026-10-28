-- ============================================================
-- 试卷模块 v2 改造（评审方案落地）：
--   1. 共享人员由 VARCHAR 字符串 → 标准多对多中间表
--   2. 试卷增加逻辑删除字段 is_deleted
--   3. 试卷状态枚举澄清：0草稿 / 1已发布 / 2已归档
--      （原"已结束"是 exam 考试的状态，不再属于试卷）
--   4. 数据迁移：把旧 visible_user_ids 字符串拆分到中间表
-- ============================================================

-- 1) 试卷表增加逻辑删除字段
ALTER TABLE exam_paper
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0
        COMMENT '逻辑删除：0正常 1已删除（保留历史考试成绩数据）' AFTER status;

-- 2) 共享人员中间表（多对多，标准设计）
CREATE TABLE IF NOT EXISTS exam_paper_visible_user (
    id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    paper_id  BIGINT       NOT NULL COMMENT '试卷ID',
    user_id   BIGINT       NOT NULL COMMENT '被共享教师ID',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '共享时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_user (paper_id, user_id),
    KEY idx_paper (paper_id),
    KEY idx_user  (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷可见教师中间表';

-- 3) 迁移历史数据：把旧 visible_user_ids 逗号字符串拆到中间表
--     （MySQL 8+ 可用 JSON_TABLE / 递归 CTE；这里用 numbers 表兜底法兼容 5.7+）
INSERT INTO exam_paper_visible_user(paper_id, user_id)
SELECT p.id AS paper_id, TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p.visible_user_ids, ',', n.idx), ',', -1)) AS user_id
FROM exam_paper p
JOIN (
    SELECT 1 AS idx UNION ALL SELECT 2  UNION ALL SELECT 3  UNION ALL SELECT 4  UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8  UNION ALL SELECT 9  UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) n
ON n.idx <= LENGTH(p.visible_user_ids) - LENGTH(REPLACE(p.visible_user_ids, ',', '')) + 1
WHERE p.visible_user_ids IS NOT NULL
  AND p.visible_user_ids != ''
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p.visible_user_ids, ',', n.idx), ',', -1)) REGEXP '^[0-9]+$';
-- 注：上限 20 个共享人，超出可自行扩展 numbers 表

-- 4) 数据迁移完毕，删除旧字符串字段
ALTER TABLE exam_paper DROP COLUMN visible_user_ids;

-- 5) 状态枚举澄清（无需 DDL，仅注释说明，便于代码/文档对齐）：
--    exam_paper.status：
--       0 = 草稿
--       1 = 已发布（可用来发布考试）
--       2 = 已归档（考试全部结束后归档；不可再撤回/重新组卷/发布新考试）
--    exam.status（考试表，互不混淆）：
--       1 = 已发布  0 = 已撤回
--       runtimeStatus（运行时推导）：0未开始 / 1进行中 / 2已结束 / 3已撤回
