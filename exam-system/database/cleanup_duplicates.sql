SET NAMES utf8mb4;
-- === 清理：删除所有重复的新学科 + 新班级，然后重新插入干净的一份 ===
DELETE FROM question        WHERE course_id IN (SELECT id FROM sys_course WHERE course_name IN ('高等数学','线性代数','概率论'));
DELETE FROM knowledge_point WHERE course_id IN (SELECT id FROM sys_course WHERE course_name IN ('高等数学','线性代数','概率论'));
DELETE FROM sys_course      WHERE course_name IN ('高等数学','线性代数','概率论');
DELETE FROM sys_class       WHERE class_name IN ('23005801','23005802','23005803','23005804');
-- === 重新插入 3 个学科 ===
INSERT INTO sys_course (course_name, course_code, description) VALUES
  ('高等数学', 'MATH101', '高等数学：函数、极限、导数、积分等基础理论'),
  ('线性代数', 'LA101',   '线性代数：矩阵、行列式、向量空间等'),
  ('概率论',   'PRO101',  '概率论：随机事件、随机变量、分布、期望等');
-- === 重新插入 4 个班级 ===
INSERT INTO sys_class (class_name, grade, department) VALUES
  ('23005801', '2023级', '信息管理与信息系统'),
  ('23005802', '2023级', '信息管理与信息系统'),
  ('23005803', '2023级', '信息管理与信息系统'),
  ('23005804', '2023级', '信息管理与信息系统');
-- === 把已有的 6 位学生 (2300580201-06) 绑定到 23005802 班 ===
UPDATE sys_user SET class_id = (SELECT id FROM sys_class WHERE class_name='23005802' LIMIT 1)
  WHERE role = 0 AND username REGEXP '^23005802[0-9]{2}$';
