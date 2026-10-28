import subprocess
from pypinyin import pinyin, Style

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "exam_system"

def run_sql(sql, as_script=False):
    if as_script:
        # write to temp and source
        with open("tmp_run.sql", "w", encoding="utf-8") as f:
            f.write(sql)
        cmd = [MYSQL, "--default-character-set=utf8mb4", "-uroot", "-p123456", DB, "-e", "source tmp_run.sql;"]
    else:
        cmd = [MYSQL, "--default-character-set=utf8mb4", "-uroot", "-p123456", DB, "-e", sql]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0:
        raise SystemExit("SQL ERROR: " + r.stderr)
    return r.stdout

# 1) 备份当前姓名
run_sql("CREATE TABLE IF NOT EXISTS sys_user_names_backup_20260822 AS SELECT id, username, real_name, role FROM sys_user;")
print("已创建备份表 sys_user_names_backup_20260822")

classes = ["23005801", "23005802", "23005803", "23005804"]

def pinyin_key(name):
    return "".join(p[0] for p in pinyin(name, style=Style.NORMAL))

all_sql = []
summary = []
for c in classes:
    out = run_sql(f"SELECT username, real_name FROM sys_user WHERE username LIKE '{c}%' ORDER BY CAST(username AS UNSIGNED) ASC;")
    lines = out.strip().split("\n")[1:]
    rows = []
    for ln in lines:
        parts = ln.split("\t")
        if len(parts) >= 2:
            rows.append((parts[0], parts[1]))
    usernames = [r[0] for r in rows]
    names = [r[1] for r in rows]
    sorted_names = sorted(names, key=pinyin_key)
    for username, new_name in zip(usernames, sorted_names):
        all_sql.append(f"UPDATE sys_user SET real_name = '{new_name}' WHERE username = '{username}';")
        summary.append((c, username, new_name))

# 2) 写 SQL 文件
sql_text = "\n".join(all_sql) + "\n"
with open(r"D:\Java project\exam-system\database\resort_names_by_pinyin.sql", "w", encoding="utf-8") as f:
    f.write(sql_text)

# 3) 打印对照（每班前几个 + 总条数）
print(f"共生成 {len(all_sql)} 条 UPDATE")
for c in classes:
    cls_rows = [s for s in summary if s[0] == c]
    print(f"\n=== 班级 {c} (共{len(cls_rows)}人) ===")
    for _, un, nm in cls_rows:
        print(f"  {un} -> {nm}")

# 4) 执行
run_sql("\n".join(all_sql))
print("\n执行完成。")
