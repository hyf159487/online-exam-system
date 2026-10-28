-- ============================================================
-- 调整学科 + 班级 + 题库（2026-08-21 晚）
-- 1. 电子商务概论 -> 电子商务
-- 2. 删除软件工程及其题目/知识点
-- 3. 新增 高等数学 / 线性代数 / 概率论 + 知识点 + 题目
-- 4. 建立 4 个班级 23005801~23005804
-- ============================================================
SET NAMES utf8mb4;
START TRANSACTION;

-- ① 改名：电子商务概论 -> 电子商务
UPDATE sys_course SET course_name = '电子商务' WHERE course_name = '电子商务概论';

-- ② 删除软件工程：题目 -> 知识点 -> 课程（无外键约束，顺序安全）
DELETE FROM question        WHERE course_id = 8;
DELETE FROM knowledge_point WHERE course_id = 8;
DELETE FROM sys_course      WHERE id = 8;

-- ③ 新增 3 个学科
INSERT INTO sys_course (course_name, course_code, description) VALUES
  ('高等数学', 'MATH101', '高等数学：函数、极限、导数、积分等基础理论'),
  ('线性代数', 'LA101',   '线性代数：矩阵、行列式、向量空间等'),
  ('概率论',   'PRO101',  '概率论：随机事件、随机变量、分布、期望等');

-- ④ 建立 4 个班级 (23005801 ~ 23005804)
INSERT IGNORE INTO sys_class (class_name, grade, department) VALUES
  ('23005801', '2023级', '信息管理与信息系统'),
  ('23005802', '2023级', '信息管理与信息系统'),
  ('23005803', '2023级', '信息管理与信息系统'),
  ('23005804', '2023级', '信息管理与信息系统');

-- ⑤ 新增知识点 + 题目
-- == 高等数学 (id=12) ==
INSERT INTO knowledge_point (course_id, point_name) VALUES
  (12, '函数的概念及常见函数'),
  (12, '极限、连续概念'),
  (12, '导数');
INSERT INTO question (course_id, question_type, content, options, answer, analysis, difficulty, knowledge_id, score, creator_id) VALUES
  (12, 3, '函数 f(x) 在 x=a 处连续且存在导数，那么 f(x) 在 x=a 处一定可导。', '对|错', 'F', '连续不可推存在导数。', 1, (SELECT id FROM knowledge_point WHERE course_id=12 AND point_name='极限、连续概念' LIMIT 1), 2.0, 1),
  (12, 4, '求 f(x) = x^3 + 2x 在 x=1 处的导数值。', NULL, '5', 'f'(x)=3x^2+2, f\'(1)=5。', 1, (SELECT id FROM knowledge_point WHERE course_id=12 AND point_name='导数' LIMIT 1), 2.0, 1),
  (12, 5, '简述函数在一点处可导与连续的关系，并举一例说明。', NULL, '可导必连续，反之不成立。例如f(x)=|x|在x=0不可导但连续。', '可导函数在该点必连续，连续函数在该点不一定可导。', 2, (SELECT id FROM knowledge_point WHERE course_id=12 AND point_name='极限、连续概念' LIMIT 1), 5.0, 1);

-- == 线性代数 (id=13) ==
INSERT INTO knowledge_point (course_id, point_name) VALUES
  (13, '矩阵及其运算'),
  (13, '行列式'),
  (13, '向量的内积');
INSERT INTO question (course_id, question_type, content, options, answer, analysis, difficulty, knowledge_id, score, creator_id) VALUES
  (13, 3, '两个 3 阶矩阵相乘结果的秩一定等于其中一个矩阵的秩。', '对|错', 'F', '矩阵相乘秩不固定。', 1, (SELECT id FROM knowledge_point WHERE course_id=13 AND point_name='矩阵及其运算' LIMIT 1), 2.0, 1),
  (13, 4, '已知 2 阶行列式 |a b; c d| = 5，求 |2a 2b; 2c 2d| 的值。', NULL, '20', '行列式提出 2 后为 2^2 * 5 = 20。', 1, (SELECT id FROM knowledge_point WHERE course_id=13 AND point_name='行列式' LIMIT 1), 2.0, 1),
  (13, 5, '解释 n 维向量的点积（内积）的几何意义。', NULL, '点积等于两个向量模的乘积乘以夹角余弦。', 'a·b=|a||b|cos(theta)。', 2, (SELECT id FROM knowledge_point WHERE course_id=13 AND point_name='向量的内积' LIMIT 1), 5.0, 1);

-- == 概率论 (id=14) ==
INSERT INTO knowledge_point (course_id, point_name) VALUES
  (14, '随机事件与概率'),
  (14, '随机变量及分布'),
  (14, '期望与方差');
INSERT INTO question (course_id, question_type, content, options, answer, analysis, difficulty, knowledge_id, score, creator_id) VALUES
  (14, 3, '互斥事件一定相互独立。', '对|错', 'F', '互斥事件 P(AB)=0，不满足独立。', 1, (SELECT id FROM knowledge_point WHERE course_id=14 AND point_name='随机事件与概率' LIMIT 1), 2.0, 1),
  (14, 4, '泊松分布的参数 lambda 的概率解释是什么？', NULL, '单位时间(或单位面积)内事件发生的平均次数', '泊松分布刻画单位区间事件发生的平均次数。', 1, (SELECT id FROM knowledge_point WHERE course_id=14 AND point_name='随机变量及分布' LIMIT 1), 2.0, 1),
  (14, 5, '解释条件概率 P(B|A) 的定义，并说明其适用条件。', NULL, 'P(B|A)=P(AB)/P(A), 需 P(A)>0。', '条件概率定义为联事件除以给定事件概率。', 2, (SELECT id FROM knowledge_point WHERE course_id=14 AND point_name='随机事件与概率' LIMIT 1), 5.0, 1);

COMMIT;
