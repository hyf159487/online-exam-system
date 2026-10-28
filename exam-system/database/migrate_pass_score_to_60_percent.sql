-- ============================================================================
-- 统一及格分口径：及格分 = 满分 × 60%
--
-- 【为什么要跑】
-- 系统里原本并存两套及格标准：
--   1) 学生成绩单 / 交卷结果（exam_record.is_pass）按 exam_paper.pass_score 判定，
--      而这个值是教师在「新增试卷 / 编辑试卷」时手填的（默认 60）；
--   2) 班级学情统计 / 报表按「试卷满分 × 60%」动态计算及格线（StatsClassServiceImpl）。
-- 二者对非 100 分试卷会互相矛盾，例如 50 分的 AI 智能组卷：
--   统计认为 ≥30 及格，成绩单却按 ≥60 判定 → 全班都显示「不及格」。
--
-- 【改动后的行为】
-- 及格分不再由教师填写：
--   * 新增 / 编辑试卷：前端不再传 passScore，后端忽略该字段；
--   * 任何改动总分的时机（保存组卷 / 随机组卷 / AI 智能组卷 / 错题加入试卷）
--     都会自动把 pass_score 写成 ROUND(满分 × 60%, 1)；
--   * 发布考试时从试卷快照 pass_score，口径天然一致。
--
-- 【本脚本做什么】
-- 仅校正历史数据的 exam_paper.pass_score 与 exam.pass_score，使其等于 满分 × 60%。
-- 幂等，可重复执行。执行前建议先备份（mysqldump）。
--
-- 【顺便说明】
-- exam_record 里已判分的历史成绩（is_pass / pass_score）是当时的快照，本脚本
-- 【不会】修改，避免追溯改变已发布成绩。若确实需要把历史成绩也按新口径重算，
-- 见文末可选语句（默认注释，请确认后再单独执行）。
-- ============================================================================

-- 1) 试卷：及格分对齐 满分 × 60%
UPDATE exam_paper
SET pass_score = ROUND(total_score * 0.6, 1)
WHERE total_score IS NOT NULL
  AND total_score > 0
  AND (pass_score IS NULL OR pass_score <> ROUND(total_score * 0.6, 1));

-- 2) 考试：及格分对齐 满分 × 60%（发布时从试卷快照，历史考试需同步）
UPDATE exam
SET pass_score = ROUND(total_score * 0.6, 1)
WHERE total_score IS NOT NULL
  AND total_score > 0
  AND (pass_score IS NULL OR pass_score <> ROUND(total_score * 0.6, 1));

-- 3) 校验：以下查询应返回 0 行
SELECT 'exam_paper' AS tbl, id, total_score, pass_score
FROM exam_paper
WHERE total_score > 0 AND (pass_score IS NULL OR pass_score <> ROUND(total_score * 0.6, 1))
UNION ALL
SELECT 'exam' AS tbl, id, total_score, pass_score
FROM exam
WHERE total_score > 0 AND (pass_score IS NULL OR pass_score <> ROUND(total_score * 0.6, 1));

-- 4) 可选：把历史成绩记录也重算为「满分 × 60%」口径（默认注释，确认影响后再执行）
--    仅推荐在测试环境或确认不需要保留旧判定结果时使用。
-- UPDATE exam_record r
--   JOIN exam_paper p ON p.id = r.paper_id
-- SET r.pass_score = ROUND(p.total_score * 0.6, 1),
--     r.is_pass    = IF(r.total_score >= ROUND(p.total_score * 0.6, 1), 1, 0)
-- WHERE p.total_score > 0;
