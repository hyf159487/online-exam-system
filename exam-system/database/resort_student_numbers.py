# -*- coding: utf-8 -*-
"""
按姓名拼音重排各班学号：
  - 每班（23005801~04）内部按 real_name 拼音 a-z 排序
  - 新学号 = 班级前缀(8位) + 两位序号(01~30)
  - 两阶段更新避免学号唯一约束冲突（先改成临时号，再改最终号）
"""
import pymysql
from pypinyin import lazy_pinyin

conn = pymysql.connect(host='127.0.0.1', user='root', password='123456',
                       database='exam_system', charset='utf8mb4')
cur = conn.cursor()

# 1. 取全部学生（排除教师/管理员）
cur.execute("SELECT id, username, real_name FROM sys_user WHERE role=0 ORDER BY username")
students = cur.fetchall()
assert len(students) == 120, f"学生数不对: {len(students)}"

# 2. 按班级前缀分组
groups = {}
for sid, username, real_name in students:
    prefix = username[:8]
    groups.setdefault(prefix, []).append((sid, username, real_name))

def pinyin_key(item):
    return tuple(lazy_pinyin(item[2]))

mappings = []  # (id, new_username)
for prefix in sorted(groups):
    roster = sorted(groups[prefix], key=pinyin_key)
    print(f"\n===== {prefix} 班 =====")
    for i, (sid, old_username, name) in enumerate(roster, start=1):
        new_username = f"{prefix}{i:02d}"
        mappings.append((sid, new_username))
        marker = '' if old_username == new_username else '  *'
        print(f"{i:2d}  {name}\t{old_username} -> {new_username}{marker}")

# 3. 两阶段更新：先临时号，再最终号
cur.executemany("UPDATE sys_user SET username=CONCAT('TMP', id) WHERE id=%s",
                [(sid,) for sid, _ in mappings])
cur.executemany("UPDATE sys_user SET username=%s WHERE id=%s",
                [(nu, sid) for sid, nu in mappings])
conn.commit()

# 4. 验证
cur.execute("SELECT COUNT(*) FROM sys_user WHERE role=0 AND (username NOT REGEXP '^2300580[1-4][0-9]{2}$' OR LENGTH(username)<>10)")
bad = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM sys_user WHERE role=0 AND username LIKE 'TMP%'")
tmp_left = cur.fetchone()[0]
print(f"\n验证：格式异常 {bad} 条，临时号残留 {tmp_left} 条")
print("完成！")
conn.close()
