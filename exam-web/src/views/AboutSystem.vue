<template>
  <div class="about-page">
    <el-card shadow="never" class="about-card">
      <!-- 页面标题 -->
      <div class="page-title">
        <el-icon class="title-icon"><InfoFilled /></el-icon>
        <span>关于系统</span>
      </div>

      <!-- 1. 系统基础信息 -->
      <el-descriptions :column="2" border class="info-block">
        <el-descriptions-item label="系统名称">在线考试与题库管理系统</el-descriptions-item>
        <el-descriptions-item label="系统版本">
          <el-tag type="success" effect="plain">V1.0.0</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="完成时间">2026 年</el-descriptions-item>
        <el-descriptions-item label="项目类型">课程设计项目</el-descriptions-item>
      </el-descriptions>

      <!-- 2. 系统简介 -->
      <div class="section">
        <div class="section-title">系统简介</div>
        <div class="section-content intro">
          本系统是面向校园的在线考试与题库管理平台，支持管理员、教师、学生三种角色。围绕课程与知识点建设、学习资料共享、题库维护与试题审核、手动 / 随机 / 模板 / AI 智能组卷、作业发布与批改、考试组织与实时监考、客观题自动判分与主观题在线阅卷、成绩统计与学情分析、学生自主练习（课程作业 / 自由刷题）、错题本与收藏，以及账号班级管理、角色权限、字典、系统参数、操作日志、站内消息通知等业务，形成完整的教学考核闭环；并内置 AI 智能组卷、AI 难度评估、AI 知识点匹配、AI 主观题预评分、AI 错题讲解五项能力，未配置大模型密钥时自动降级为本地规则引擎，保证功能始终可用。
        </div>
      </div>

      <!-- 3. 核心功能列表 -->
      <div class="section">
        <div class="section-title">核心功能</div>
        <div class="role-grid">
          <div class="role-card">
            <div class="role-header">
              <el-tag type="danger" effect="dark" size="small">管理员</el-tag>
              <span class="role-name">系统基础配置</span>
            </div>
            <ul class="role-list">
              <li>账号 / 班级 / 课程管理，支持账号批量导入与调班</li>
              <li>角色权限分配（RBAC）、字典与系统参数维护</li>
              <li>试题审核、操作日志审计</li>
              <li>知识点与学习资料维护、发布全局通知</li>
              <li>全校教学统计（班级学情 / 试题质量 / 报表导出）</li>
            </ul>
          </div>

          <div class="role-card">
            <div class="role-header">
              <el-tag type="warning" effect="dark" size="small">教师</el-tag>
              <span class="role-name">教学业务管理</span>
            </div>
            <ul class="role-list">
              <li>题库管理：单题录入 / 批量导入，AI 难度评估与 AI 知识点匹配，支持高频错题库复用</li>
              <li>我的课程与学生名单、学生加入申请审核；录题时关联知识点并上传学习资料</li>
              <li>试卷制作（手动 / 随机 / 模板 / AI 智能组卷）与作业发布、批改</li>
              <li>发布考试、实时监考（答题进度 / 切屏检测）与强制交卷</li>
              <li>主观题在线阅卷（AI 预评分）、学情分析与报表导出</li>
              <li>发布通知公告、查看通知记录</li>
            </ul>
          </div>

          <div class="role-card">
            <div class="role-header">
              <el-tag type="primary" effect="dark" size="small">学生</el-tag>
              <span class="role-name">在线学习考试</span>
            </div>
            <ul class="role-list">
              <li>参加考试、查看成绩与逐题作答明细</li>
              <li>课程学习：课程作业 / 自由刷题 / 资料下载</li>
              <li>错题本重做、收藏重点题与 AI 错题讲解</li>
              <li>加入课程并等待教师审核、下载课程资料</li>
              <li>个人信息维护、修改密码与通知公告</li>
            </ul>
          </div>
        </div>
      </div>

      <!-- 4. 开发技术栈 -->
      <div class="section">
        <div class="section-title">技术栈</div>
        <div class="tech-tags">
          <el-tag class="tech-tag" type="success" effect="plain">Vue 3</el-tag>
          <el-tag class="tech-tag" type="success" effect="plain">Element Plus</el-tag>
          <el-tag class="tech-tag" type="warning" effect="plain">Spring Boot 4</el-tag>
          <el-tag class="tech-tag" type="warning" effect="plain">MyBatis</el-tag>
          <el-tag class="tech-tag" type="danger" effect="plain">MySQL</el-tag>
          <el-tag class="tech-tag" type="info" effect="plain">JWT + BCrypt</el-tag>
          <el-tag class="tech-tag" type="info" effect="plain">RBAC 三层权限校验</el-tag>
          <el-tag class="tech-tag" type="info" effect="plain">Redis 缓存与降级</el-tag>
          <el-tag class="tech-tag" type="info" effect="plain">ECharts 图表</el-tag>
          <el-tag class="tech-tag" type="success" effect="plain">Apache POI</el-tag>
          <el-tag class="tech-tag" type="success" effect="plain">PDFBox</el-tag>
          <el-tag class="tech-tag" type="warning" effect="plain">AspectJ 操作日志</el-tag>
          <el-tag class="tech-tag" type="primary" effect="plain">大模型 AI（本地规则降级）</el-tag>
          <el-tag class="tech-tag" type="info" effect="plain">Electron 桌面客户端</el-tag>
        </div>
      </div>

      <!-- 5. 版权信息 -->
      <div class="copyright">
        ©2026 在线考试与题库管理系统，课程设计项目
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { InfoFilled } from '@element-plus/icons-vue'
</script>

<style scoped>
.about-page {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}
.about-card {
  width: 100%;
  max-width: 820px;
  border-radius: 8px;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
}
.title-icon {
  font-size: 20px;
  color: #409EFF;
}
.info-block {
  margin-bottom: 24px;
}
.section {
  margin-bottom: 24px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid #409EFF;
}
.section-content {
  color: #606266;
  font-size: 14px;
  line-height: 1.8;
}
.intro {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 6px;
}
.role-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.role-card {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
}
.role-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.role-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.role-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.role-list li {
  font-size: 13px;
  color: #606266;
  line-height: 1.9;
  padding-left: 14px;
  position: relative;
}
.role-list li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 11px;
  width: 4px;
  height: 4px;
  background: #c0c4cc;
  border-radius: 50%;
}
.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.tech-tag {
  font-size: 13px;
  padding: 4px 12px;
  height: 28px;
  line-height: 20px;
}
.copyright {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px dashed #e4e7ed;
  text-align: center;
  font-size: 13px;
  color: #909399;
  letter-spacing: 0.5px;
}
@media (max-width: 768px) {
  .role-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 540px) {
  .role-grid { grid-template-columns: 1fr; }
}
</style>
