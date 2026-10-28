
# 在线考试与题库管理系统
> 课程设计项目｜前后端分离在线考试平台

## ✨ 项目简介
针对传统考试试题分散、组卷耗时、阅卷与成绩统计效率低的问题，搭建覆盖题库管理、组卷、在线考试、自动判分、成绩分析全流程的在线考试与题库管理系统。

## 🛠 技术栈
- 后端：Spring Boot + MyBatis + MySQL
- 前端：Vue3 + Element Plus
- 缓存：Redis
- 权限：JWT + RBAC权限模型
- 打包：Electron桌面端打包

## 📦 项目结构
online-exam-system
├── exam-system      # SpringBoot 后端
├── exam-web         # Vue3 前端
└── .gitignore       # Git 忽略配置


## 🚀 核心功能
- 题库管理：试题录入、批量导入
- 组卷功能：手动组卷、随机组卷、AI智能组卷
- 在线考试、客观题自动判分、教师主观题阅卷
- 成绩统计分析、错题本、成绩报表导出

## 📋 运行说明
1. 导入数据库SQL脚本
2. 修改后端application.yml数据库配置
3. 启动后端SpringBoot服务
4. 前端安装依赖 `npm install`，`npm run dev` 启动

## 📄 备注
课程设计项目，用于学习前后端分离开发，包含模拟学生、试题测试数据。
