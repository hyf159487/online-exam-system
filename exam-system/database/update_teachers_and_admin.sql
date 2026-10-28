-- 更新三位老师的姓名
UPDATE sys_user SET real_name = '陈平安' WHERE username = 'teacher01';
UPDATE sys_user SET real_name = '光头强' WHERE username = 'teacher02';
UPDATE sys_user SET real_name = '管理员' WHERE username = 'admin';

-- 新增一位老师：叶枫 (teacher03)，密码复用 teacher01 的 admin123 哈希
INSERT INTO sys_user (username, password, real_name, role, status, create_time, update_time)
VALUES ('teacher03', '$2a$10$mp/uMic4Jy5pDnKCwSQ/ru6/U3.1GG67u59eISD0iEY/WHb1ut9yO', '叶枫', 1, 1, NOW(), NOW());
