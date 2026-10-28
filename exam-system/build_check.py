# -*- coding: utf-8 -*-
import subprocess, sys, os

# 路径硬编码，避免命令行中文乱码
base = r"e:\学习\大三下\基于SpringBoot+Vue的在线考试与题库管理系统设计与实现\程序\第二版\在线考试系统-项目备份-20260822\exam-system"
os.chdir(base)
print("cwd:", os.getcwd())

# 优先用 mvn（若已安装），否则 mvnw.cmd
cmd = ["mvn", "compile", "-q", "-DskipTests"]
try:
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
except FileNotFoundError:
    cmd = [os.path.join(base, "mvnw.cmd"), "compile", "-q", "-DskipTests"]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")

print("exit:", r.returncode)
if r.stdout:
    print("--- stdout ---")
    print(r.stdout[-4000:])
if r.stderr:
    print("--- stderr ---")
    print(r.stderr[-4000:])
sys.exit(r.returncode)
