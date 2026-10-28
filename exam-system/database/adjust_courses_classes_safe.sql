SET NAMES utf8mb4;
-- ① 改名：电子商务概论 -> 电子商务
UPDATE sys_course SET course_name = '电子商务' WHERE course_name = '电子商务概论';
-- ② 删除软件工程（无外键约束，题目 -> 知识点 -> 课程 顺序安全）
DELETE FROM question        WHERE course_id = 8;
DELETE FROM knowledge_point WHERE course_id = 8;
DELETE FROM sys_course      WHERE id = 8;
-- ③ 新增 3 个学科
INSERT INTO sys_course (course_name, course_code, description) VALUES
  ('高等数学', 'MATH101', '高等数学：函数、极限、导数、积分等基础理论'),
  ('线性代数', 'LA101',   '线性代数：矩阵、行列式、向量空间等'),
  ('概率论',   'PRO101',  '概率论：随机事件、随机变量、分布、期望等');
-- ④ 建立 4 个班级 23005801~23005804
INSERT IGNORE INTO sys_class (class_name, grade, department) VALUES
  ('23005801', '2023级', '信息管理与信息系统'),
  ('23005802', '2023级', '信息管理与信息系统'),
  ('23005803', '2023级', '信息管理与信息系统'),
  ('23005804', '2023级', '信息管理与信息系统');
