-- ===================================================
-- 学生信息同步脚本（学生改名 + 学号重排）
-- 来源：同事库 exam_system.sys_user（120 个学生）
-- 目标：本机 exam_system
-- 匹配方式：按 id（user_id）匹配，只改 username 和 real_name
-- 前置条件：两边 user_id 一致（2~7、10~123 为学生；8、9 为 teacher01/teacher02）
--
-- 【执行前请先单独跑这两条校验，结果必须都是 120，否则不要执行本文件】
--   SELECT COUNT(*) FROM sys_user WHERE role=0;
--   SELECT COUNT(*) FROM sys_user WHERE role=0 AND id BETWEEN 2 AND 123;
--
-- 【本次相对原始脚本的修改】
--   1. START TRANSACTION 后新增 tmp_ 临时改名语句：
--      原脚本第一条 UPDATE 就会报 1062 Duplicate entry '2300580230' for key 'uk_username'，
--      因为新学号集合 == 旧学号集合（只是重新分配），逐条改时新值仍被别的行占用。
--   2. COMMIT 前同步 practice_record.student_name 冗余姓名字段。
-- ===================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

-- ---------- 一、先把全部学生学号临时腾空（关键，不能删） ----------
UPDATE sys_user SET username = CONCAT('tmp_', id) WHERE role = 0;

-- ---------- 二、逐条写入新学号 + 新姓名 ----------
UPDATE sys_user SET username = '2300580230', real_name = '钟吾' WHERE id = 2;
UPDATE sys_user SET username = '2300580201', real_name = '顾清寒' WHERE id = 3;
UPDATE sys_user SET username = '2300580202', real_name = '哈迪' WHERE id = 4;
UPDATE sys_user SET username = '2300580203', real_name = '胡桃' WHERE id = 5;
UPDATE sys_user SET username = '2300580204', real_name = '胡为' WHERE id = 6;
UPDATE sys_user SET username = '2300580207', real_name = '迦南' WHERE id = 7;
UPDATE sys_user SET username = '2300580101', real_name = '白浩芝' WHERE id = 10;
UPDATE sys_user SET username = '2300580102', real_name = '陈巧倩' WHERE id = 11;
UPDATE sys_user SET username = '2300580110', real_name = '柳眉' WHERE id = 12;
UPDATE sys_user SET username = '2300580105', real_name = '韩立' WHERE id = 13;
UPDATE sys_user SET username = '2300580106', real_name = '菡云芝' WHERE id = 14;
UPDATE sys_user SET username = '2300580104', real_name = '谷双蒲' WHERE id = 15;
UPDATE sys_user SET username = '2300580107', real_name = '厉飞雨' WHERE id = 16;
UPDATE sys_user SET username = '2300580108', real_name = '李化元' WHERE id = 17;
UPDATE sys_user SET username = '2300580129', real_name = '云天啸' WHERE id = 18;
UPDATE sys_user SET username = '2300580109', real_name = '凌玉灵' WHERE id = 19;
UPDATE sys_user SET username = '2300580111', real_name = '柳玉' WHERE id = 20;
UPDATE sys_user SET username = '2300580112', real_name = '梅凝' WHERE id = 21;
UPDATE sys_user SET username = '2300580113', real_name = '墨彩环' WHERE id = 22;
UPDATE sys_user SET username = '2300580114', real_name = '墨居仁' WHERE id = 23;
UPDATE sys_user SET username = '2300580115', real_name = '慕沛灵' WHERE id = 24;
UPDATE sys_user SET username = '2300580116', real_name = '南宫婉' WHERE id = 25;
UPDATE sys_user SET username = '2300580103', real_name = '程天坤' WHERE id = 26;
UPDATE sys_user SET username = '2300580117', real_name = '宋蒙' WHERE id = 27;
UPDATE sys_user SET username = '2300580118', real_name = '宋玉' WHERE id = 28;
UPDATE sys_user SET username = '2300580119', real_name = '孙火' WHERE id = 29;
UPDATE sys_user SET username = '2300580121', real_name = '王蝉' WHERE id = 30;
UPDATE sys_user SET username = '2300580120', real_name = '万天明' WHERE id = 31;
UPDATE sys_user SET username = '2300580122', real_name = '文思月' WHERE id = 32;
UPDATE sys_user SET username = '2300580123', real_name = '温天仁' WHERE id = 33;
UPDATE sys_user SET username = '2300580124', real_name = '向之礼' WHERE id = 34;
UPDATE sys_user SET username = '2300580125', real_name = '萧诧' WHERE id = 35;
UPDATE sys_user SET username = '2300580126', real_name = '妍丽' WHERE id = 36;
UPDATE sys_user SET username = '2300580127', real_name = '银月' WHERE id = 37;
UPDATE sys_user SET username = '2300580128', real_name = '元瑶' WHERE id = 38;
UPDATE sys_user SET username = '2300580130', real_name = '张铁' WHERE id = 39;
UPDATE sys_user SET username = '2300580205', real_name = '季沧海' WHERE id = 40;
UPDATE sys_user SET username = '2300580206', real_name = '季莹莹' WHERE id = 41;
UPDATE sys_user SET username = '2300580209', real_name = '蓝梦' WHERE id = 42;
UPDATE sys_user SET username = '2300580210', real_name = '刘炼' WHERE id = 43;
UPDATE sys_user SET username = '2300580211', real_name = '宁红夜' WHERE id = 44;
UPDATE sys_user SET username = '2300580212', real_name = '齐铁嘴' WHERE id = 45;
UPDATE sys_user SET username = '2300580213', real_name = '沈妙' WHERE id = 46;
UPDATE sys_user SET username = '2300580214', real_name = '特木尔' WHERE id = 47;
UPDATE sys_user SET username = '2300580215', real_name = '天海' WHERE id = 48;
UPDATE sys_user SET username = '2300580216', real_name = '魏轻' WHERE id = 49;
UPDATE sys_user SET username = '2300580218', real_name = '无尘' WHERE id = 50;
UPDATE sys_user SET username = '2300580217', real_name = '魏无涯' WHERE id = 51;
UPDATE sys_user SET username = '2300580219', real_name = '吴三省' WHERE id = 52;
UPDATE sys_user SET username = '2300580220', real_name = '武田信忠' WHERE id = 53;
UPDATE sys_user SET username = '2300580221', real_name = '吴邪' WHERE id = 54;
UPDATE sys_user SET username = '2300580208', real_name = '金青' WHERE id = 55;
UPDATE sys_user SET username = '2300580222', real_name = '席拉' WHERE id = 56;
UPDATE sys_user SET username = '2300580223', real_name = '妖刀姬' WHERE id = 57;
UPDATE sys_user SET username = '2300580224', real_name = '殷紫萍' WHERE id = 58;
UPDATE sys_user SET username = '2300580226', real_name = '岳山' WHERE id = 59;
UPDATE sys_user SET username = '2300580225', real_name = '玉玲珑' WHERE id = 60;
UPDATE sys_user SET username = '2300580227', real_name = '张起灵' WHERE id = 61;
UPDATE sys_user SET username = '2300580228', real_name = '张启山' WHERE id = 62;
UPDATE sys_user SET username = '2300580229', real_name = '张日山' WHERE id = 63;
UPDATE sys_user SET username = '2300580325', real_name = '汪凝' WHERE id = 64;
UPDATE sys_user SET username = '2300580306', real_name = '公孙杏' WHERE id = 65;
UPDATE sys_user SET username = '2300580303', real_name = '范静梅' WHERE id = 66;
UPDATE sys_user SET username = '2300580301', real_name = '长夜月' WHERE id = 67;
UPDATE sys_user SET username = '2300580302', real_name = '丹恒' WHERE id = 68;
UPDATE sys_user SET username = '2300580317', real_name = '齐云霄' WHERE id = 69;
UPDATE sys_user SET username = '2300580304', real_name = '符华' WHERE id = 70;
UPDATE sys_user SET username = '2300580305', real_name = '符玄' WHERE id = 71;
UPDATE sys_user SET username = '2300580307', real_name = '花火' WHERE id = 72;
UPDATE sys_user SET username = '2300580308', real_name = '黄泉' WHERE id = 73;
UPDATE sys_user SET username = '2300580309', real_name = '藿藿' WHERE id = 74;
UPDATE sys_user SET username = '2300580316', real_name = '马知墨' WHERE id = 75;
UPDATE sys_user SET username = '2300580310', real_name = '镜流' WHERE id = 76;
UPDATE sys_user SET username = '2300580311', real_name = '景元' WHERE id = 77;
UPDATE sys_user SET username = '2300580312', real_name = '卡芙卡' WHERE id = 78;
UPDATE sys_user SET username = '2300580314', real_name = '流萤' WHERE id = 79;
UPDATE sys_user SET username = '2300580313', real_name = '李信' WHERE id = 80;
UPDATE sys_user SET username = '2300580315', real_name = '罗刹' WHERE id = 81;
UPDATE sys_user SET username = '2300580318', real_name = '千冶刃' WHERE id = 82;
UPDATE sys_user SET username = '2300580319', real_name = '青雀' WHERE id = 83;
UPDATE sys_user SET username = '2300580320', real_name = '三月七' WHERE id = 84;
UPDATE sys_user SET username = '2300580321', real_name = '藤荒' WHERE id = 85;
UPDATE sys_user SET username = '2300580322', real_name = '停云' WHERE id = 86;
UPDATE sys_user SET username = '2300580323', real_name = '托帕' WHERE id = 87;
UPDATE sys_user SET username = '2300580324', real_name = '瓦尔特' WHERE id = 88;
UPDATE sys_user SET username = '2300580326', real_name = '彦卿' WHERE id = 89;
UPDATE sys_user SET username = '2300580327', real_name = '爻光' WHERE id = 90;
UPDATE sys_user SET username = '2300580328', real_name = '银狼' WHERE id = 91;
UPDATE sys_user SET username = '2300580329', real_name = '真珠' WHERE id = 92;
UPDATE sys_user SET username = '2300580330', real_name = '知更鸟' WHERE id = 93;
UPDATE sys_user SET username = '2300580401', real_name = '白厄' WHERE id = 94;
UPDATE sys_user SET username = '2300580402', real_name = '飞霄' WHERE id = 95;
UPDATE sys_user SET username = '2300580403', real_name = '风堇' WHERE id = 96;
UPDATE sys_user SET username = '2300580404', real_name = '归寂' WHERE id = 97;
UPDATE sys_user SET username = '2300580405', real_name = '桂乃芬' WHERE id = 98;
UPDATE sys_user SET username = '2300580406', real_name = '黑塔' WHERE id = 99;
UPDATE sys_user SET username = '2300580407', real_name = '幻太子' WHERE id = 100;
UPDATE sys_user SET username = '2300580408', real_name = '火花' WHERE id = 101;
UPDATE sys_user SET username = '2300580409', real_name = '加拉赫' WHERE id = 102;
UPDATE sys_user SET username = '2300580411', real_name = '玲可' WHERE id = 103;
UPDATE sys_user SET username = '2300580413', real_name = '乱破' WHERE id = 104;
UPDATE sys_user SET username = '2300580414', real_name = '米沙' WHERE id = 105;
UPDATE sys_user SET username = '2300580415', real_name = '貊泽' WHERE id = 106;
UPDATE sys_user SET username = '2300580416', real_name = '那刻夏' WHERE id = 107;
UPDATE sys_user SET username = '2300580417', real_name = '纳努克' WHERE id = 108;
UPDATE sys_user SET username = '2300580418', real_name = '阮梅' WHERE id = 109;
UPDATE sys_user SET username = '2300580419', real_name = '萨姆' WHERE id = 110;
UPDATE sys_user SET username = '2300580420', real_name = '桑博' WHERE id = 111;
UPDATE sys_user SET username = '2300580421', real_name = '砂金' WHERE id = 112;
UPDATE sys_user SET username = '2300580422', real_name = '缇宝' WHERE id = 113;
UPDATE sys_user SET username = '2300580412', real_name = '凌啸风' WHERE id = 114;
UPDATE sys_user SET username = '2300580423', real_name = '万敌' WHERE id = 115;
UPDATE sys_user SET username = '2300580426', real_name = '遐蝶' WHERE id = 116;
UPDATE sys_user SET username = '2300580424', real_name = '昔涟' WHERE id = 117;
UPDATE sys_user SET username = '2300580425', real_name = '希露瓦' WHERE id = 118;
UPDATE sys_user SET username = '2300580410', real_name = '雷万鹤' WHERE id = 119;
UPDATE sys_user SET username = '2300580428', real_name = '雪衣' WHERE id = 120;
UPDATE sys_user SET username = '2300580427', real_name = '虚照' WHERE id = 121;
UPDATE sys_user SET username = '2300580429', real_name = '银枝' WHERE id = 122;
UPDATE sys_user SET username = '2300580430', real_name = '远坂凛' WHERE id = 123;

-- ---------- 三、同步冗余姓名（作业记录里存了学生姓名快照） ----------
UPDATE practice_record pr
JOIN sys_user u ON pr.student_id = u.id
SET pr.student_name = u.real_name
WHERE u.role = 0;

-- ---------- 四、提交 ----------
COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------- 五、执行后验证 ----------
SELECT '学号升序前5条：' AS check_item;
SELECT id, username, real_name, class_id FROM sys_user WHERE role = 0 ORDER BY username LIMIT 5;

SELECT '柳姓学生：' AS check_item;
SELECT id, username, real_name FROM sys_user WHERE role = 0 AND real_name LIKE '柳%';

SELECT '学生总数（应为120）：' AS check_item;
SELECT COUNT(*) AS total FROM sys_user WHERE role = 0;

SELECT '学号重复检查（应为空）：' AS check_item;
SELECT username, COUNT(*) AS c FROM sys_user GROUP BY username HAVING c > 1;

SELECT '残留 tmp_ 检查（应为空）：' AS check_item;
SELECT id, username FROM sys_user WHERE username LIKE 'tmp_%';
