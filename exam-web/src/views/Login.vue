<template>
  <!-- ============ 整页登录 ============ -->
  <div id="top" class="login-page" @wheel="handlePageWheel">
    <section class="login-hero">
    <!-- 装饰：三个浮动光斑（纯 CSS，呼吸动画） -->
    <div class="blob blob-1" aria-hidden="true"></div>
    <div class="blob blob-2" aria-hidden="true"></div>
    <div class="blob blob-3" aria-hidden="true"></div>

    <!-- 装饰：几何艺术图案 -->
    <div class="deco deco-ring-1" aria-hidden="true"></div>
    <div class="deco deco-ring-2" aria-hidden="true"></div>
    <div class="deco deco-square-1" aria-hidden="true"></div>
    <div class="deco deco-square-2" aria-hidden="true"></div>
    <div class="deco deco-triangle" aria-hidden="true"></div>
    <div class="deco deco-cross" aria-hidden="true"></div>
    <div class="deco deco-cross deco-cross-2" aria-hidden="true"></div>
    <div class="deco deco-dots" aria-hidden="true"></div>
    <div class="deco deco-dots deco-dots-2" aria-hidden="true"></div>

    <!-- 装饰：底部波浪 -->
    <svg class="deco-wave" aria-hidden="true" viewBox="0 0 1440 180" preserveAspectRatio="none">
      <path d="M0,120 C240,80 480,60 720,90 C960,120 1200,80 1440,110 L1440,180 L0,180 Z"
            fill="rgba(102, 126, 234, 0.10)"/>
      <path d="M0,150 C300,110 600,140 900,110 C1100,90 1300,130 1440,140 L1440,180 L0,180 Z"
            fill="rgba(118, 75, 162, 0.12)"/>
    </svg>

    <!-- 背景宣传词（8 个大字浮动标签） -->
    <div class="slogans" aria-hidden="true">
      <div class="slogan s-1"><el-icon><Collection /></el-icon><span>题库与知识点 · 一站沉淀</span></div>
      <div class="slogan s-2"><el-icon><MagicStick /></el-icon><span>AI 智能组卷 · 目标分可控</span></div>
      <div class="slogan s-3"><el-icon><DataAnalysis /></el-icon><span>学情分析 · 数据驱动</span></div>
      <div class="slogan s-4"><el-icon><Warning /></el-icon><span>错题本 · AI 智能讲解</span></div>
      <div class="slogan s-5"><el-icon><Connection /></el-icon><span>多角色协同 · 权限分明</span></div>
      <div class="slogan s-6"><el-icon><Clock /></el-icon><span>限时作答 · 自动交卷</span></div>
      <div class="slogan s-7"><el-icon><Document /></el-icon><span>客观题秒判 · 主观题 AI 预评</span></div>
      <div class="slogan s-8"><el-icon><TrendCharts /></el-icon><span>成绩可视化 · 班级对比</span></div>
    </div>

    <div class="learning-floaters" aria-hidden="true">
      <div class="learning-orbit orbit-book"><el-icon><Reading /></el-icon><span>知识点库</span></div>
      <div class="learning-orbit orbit-pencil"><el-icon><EditPen /></el-icon><span>在线阅卷</span></div>
      <div class="learning-orbit orbit-chart"><el-icon><TrendCharts /></el-icon><span>成绩统计</span></div>
      <div class="learning-bot">
        <div class="bot-antenna"></div>
        <div class="bot-head"><i></i><i></i></div>
        <div class="bot-body"><el-icon><Connection /></el-icon></div>
        <span>AI 智能讲解</span>
      </div>
    </div>

    <!-- 顶部小品牌条 -->
    <div class="top-brand">
      <div class="brand-logo">
        <el-icon :size="22" color="#fff"><Reading /></el-icon>
      </div>
      <span class="brand-name">在线考试与题库管理系统</span>
    </div>

    <!-- 居中登录卡片 -->
    <div class="login-wrapper">
      <div class="login-card">
        <div class="card-head">
          <div class="card-logo">
            <el-icon :size="28" color="#fff"><Reading /></el-icon>
          </div>
          <h2 class="card-title">欢迎登录</h2>
          <p class="card-subtitle">一站式教学考核平台</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="0"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              show-password
              :prefix-icon="Lock"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="options-row">
            <el-checkbox v-model="rememberPwd" size="default">记住密码</el-checkbox>
            <el-checkbox v-model="autoLogin" size="default">自动登录</el-checkbox>
          </div>

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form>

        <div class="card-foot">
          <p class="hint-title">温馨提示</p>
          <p class="hint-detail">「学生用户，用户名为学号，初始密码为 123456」</p>
        </div>
      </div>

      <p class="bottom-tip">©2026 在线考试与题库管理系统 · 课程设计项目</p>
      <a class="scroll-hint" href="#system-intro" aria-label="向下浏览系统介绍">
        <span>向下浏览系统介绍</span>
        <el-icon><ArrowDown /></el-icon>
      </a>
    </div>
    </section>

    <nav class="page-nav" aria-label="登录页内容导航">
      <a href="#top" @click.prevent="scrollToSection('top')" aria-label="登录首页"><span>登录</span></a>
      <a href="#system-intro" aria-label="系统介绍"><span>系统</span></a>
      <a href="#roles-intro" aria-label="角色能力"><span>角色</span></a>
      <a href="#technology-intro" aria-label="技术说明"><span>技术</span></a>
    </nav>

    <section id="system-intro" class="product-intro intro-page">
      <div class="section-tech-grid" aria-hidden="true"></div>
      <div class="learning-floaters page-floaters" aria-hidden="true">
        <div class="learning-orbit orbit-book"><el-icon><Reading /></el-icon><span>知识沉淀</span></div>
        <div class="learning-orbit orbit-pencil"><el-icon><Document /></el-icon><span>课程资料</span></div>
        <div class="learning-orbit orbit-chart"><el-icon><DataAnalysis /></el-icon><span>学习分析</span></div>
        <div class="person-float teacher-float"><div class="person-head teacher-head"></div><div class="person-body"><i></i><i></i></div><span>教师指导</span></div>
        <div class="person-float student-float"><div class="person-head student-head"></div><div class="person-body"><i></i><i></i></div><span>学生成长</span></div>
        <div class="learning-bot"><div class="bot-antenna"></div><div class="bot-head"><i></i><i></i></div><div class="bot-body"><el-icon><Connection /></el-icon></div><span>AI 难度评估</span></div>
      </div>
      <div class="page-inner system-page-layout">
        <div class="role-page-copy"><span class="eyebrow"><i class="eyebrow-dot"></i>SYSTEM INTRODUCTION</span><h2>多角色协同<br><em>智能教学考核平台</em></h2><p>平台面向管理员、教师、学生三类用户，贯通「课程与知识点建设 → 学习资料共享 → 录题入库与试题审核 → 手动 / 随机 / 模板 / AI 智能组卷 → 作业发布与批改 → 考试发布与实时监考 → 客观题自动判分、主观题在线阅卷 → 成绩统计与学情分析」的完整教学考核闭环，并覆盖账号与班级管理、学生自主练习（课程作业 / 自由刷题）、错题本与收藏、消息通知，以及字典 / 角色权限 / 操作日志 / 系统参数等运维能力。平台内置以下 AI 能力，未配置大模型密钥时自动降级为本地规则引擎，功能始终可用：</p><div class="ai-chips" aria-label="AI 能力"><span>AI 智能组卷</span><span>AI 难度评估</span><span>AI 知识点匹配</span><span>AI 主观题预评分</span><span>AI 错题讲解</span></div><div class="system-status"><span><i></i>系统运行稳定</span><span><i class="dot-alt"></i>数据驱动学情洞察</span></div><a class="back-login" href="#top" @click.prevent="scrollToSection('top')">返回登录 <el-icon><ArrowUp /></el-icon></a></div>
        <div class="feature-grid system-grid"><article class="feature-card feature-card-main"><div class="feature-icon"><el-icon><Collection /></el-icon></div><div><h3>课程与题库</h3><p>课程知识点体系与学习资料共享，单题录入、批量导入与试题审核。</p></div></article><article class="feature-card"><div class="feature-icon"><el-icon><MagicStick /></el-icon></div><div><h3>组卷与考试</h3><p>手动 / 随机 / 模板 / AI 组卷，考试发布、限时作答与实时监考。</p></div></article><article class="feature-card"><div class="feature-icon"><el-icon><EditPen /></el-icon></div><div><h3>阅卷与成绩</h3><p>客观题自动判分，主观题 AI 预评分辅助在线阅卷。</p></div></article><article class="feature-card"><div class="feature-icon"><el-icon><TrendCharts /></el-icon></div><div><h3>学情与分析</h3><p>班级学情、试题质量分析与报表导出，数据驱动教学。</p></div></article><article class="feature-card"><div class="feature-icon"><el-icon><Notebook /></el-icon></div><div><h3>作业与练习</h3><p>教师发布 / 批改作业并跟踪记录；学生课程作业、自由刷题与错题本。</p></div></article><article class="feature-card"><div class="feature-icon"><el-icon><Promotion /></el-icon></div><div><h3>通知与运维</h3><p>站内通知公告，账号 / 班级 / 课程维护，角色权限与操作日志审计。</p></div></article></div>
      </div>
    </section>

    <section id="roles-intro" class="product-intro intro-page roles-page">
      <div class="section-tech-grid" aria-hidden="true"></div>
      <div class="learning-floaters page-floaters" aria-hidden="true">
        <div class="learning-orbit orbit-book"><el-icon><Management /></el-icon><span>权限协同</span></div>
        <div class="learning-orbit orbit-pencil"><el-icon><EditPen /></el-icon><span>教师端</span></div>
        <div class="learning-orbit orbit-chart"><el-icon><UserFilled /></el-icon><span>学生端</span></div>
        <div class="person-float teacher-float"><div class="person-head teacher-head"></div><div class="person-body"><i></i><i></i></div><span>老师备课</span></div>
        <div class="person-float student-float"><div class="person-head student-head"></div><div class="person-body"><i></i><i></i></div><span>学生学习</span></div>
        <div class="learning-bot"><div class="bot-antenna"></div><div class="bot-head"><i></i><i></i></div><div class="bot-body"><el-icon><Connection /></el-icon></div><span>AI 错题讲解</span></div>
      </div>
      <div class="page-inner">
        <div class="section-heading"><span class="eyebrow"><i class="eyebrow-dot"></i>ROLE CAPABILITIES</span><h2>三端权限分工</h2><p>不同角色各司其职，共同完成教学考核闭环。</p></div>
        <div class="role-grid role-grid-three"><article class="role-card"><div class="role-number">01</div><div class="role-icon"><el-icon><Management /></el-icon></div><h3>管理员</h3><p>负责平台基础配置和全局运行管理。</p><ul><li>账号 / 班级 / 课程基础数据维护，支持批量导入与调班</li><li>知识点与学习资料维护、试题审核与全局通知</li><li>RBAC 角色权限、字典、系统参数与操作日志审计</li><li>全校教学统计分析（班级学情 / 试题质量 / 报表导出）</li></ul></article><article class="role-card role-card-accent"><div class="role-number">02</div><div class="role-icon"><el-icon><EditPen /></el-icon></div><h3>教师</h3><p>负责教学内容建设、作业与考试组织实施。</p><ul><li>题库维护：单题录入 / 批量导入 + AI 难度评估、AI 知识点匹配，支持高频错题库复用</li><li>组卷出卷（手动 / 随机 / 模板 / AI 智能组卷），试卷共享与组卷模板复用</li><li>作业发布与批改；考试发布、实时监考（答题进度 / 切屏检测）与强制交卷</li><li>主观题在线阅卷（AI 预评分）、学情分析与报表导出</li><li>我的课程与课程申请审核、学习资料上传、发布通知</li></ul></article><article class="role-card"><div class="role-number">03</div><div class="role-icon"><el-icon><UserFilled /></el-icon></div><h3>学生</h3><p>负责参与学习、考试与结果反馈。</p><ul><li>参加考试、查看成绩与逐题作答明细</li><li>课程学习：课程作业 / 自由刷题 / 资料下载</li><li>错题本重做、收藏重点题与 AI 错题讲解</li><li>加入课程、接收通知公告与维护个人信息</li></ul></article></div>
        <div class="page-actions"><a class="back-login" href="#top" @click.prevent="scrollToSection('top')">返回登录 <el-icon><ArrowUp /></el-icon></a><a class="page-next-inline" href="#technology-intro">继续浏览 <el-icon><ArrowDown /></el-icon></a></div>
      </div>
    </section>

    <section id="technology-intro" class="product-intro intro-page technology-page">
      <div class="section-tech-grid" aria-hidden="true"></div>
      <div class="tech-particle particle-one" aria-hidden="true"></div>
      <div class="tech-particle particle-two" aria-hidden="true"></div>
      <div class="tech-particle particle-three" aria-hidden="true"></div>
      <div class="learning-floaters page-floaters" aria-hidden="true">
        <div class="learning-orbit orbit-book"><el-icon><Connection /></el-icon><span>云端架构</span></div>
        <div class="learning-orbit orbit-pencil"><el-icon><MagicStick /></el-icon><span>智能引擎</span></div>
        <div class="learning-orbit orbit-chart"><el-icon><TrendCharts /></el-icon><span>数据驱动</span></div>
        <div class="person-float teacher-float"><div class="person-head teacher-head"></div><div class="person-body"><i></i><i></i></div><span>教师洞察</span></div>
        <div class="person-float student-float"><div class="person-head student-head"></div><div class="person-body"><i></i><i></i></div><span>学习成长</span></div>
        <div class="learning-bot"><div class="bot-antenna"></div><div class="bot-head"><i></i><i></i></div><div class="bot-body"><el-icon><Connection /></el-icon></div><span>AI 双通道</span></div>
      </div>
      <div class="page-inner technology-layout"><div class="technology-copy"><span class="eyebrow"><i class="eyebrow-dot"></i>TECHNOLOGY &amp; NOTES</span><h2>技术实现</h2><p>采用 Spring Boot 4 + Vue 3 前后端分离架构：MySQL 存储业务数据，Redis 提供缓存并支持故障降级；JWT + BCrypt 保障登录安全，RBAC 实现「登录拦截 → 路径规则 → 方法内校验」三层权限控制；Apache POI / PDFBox 支撑批量导入与报表导出，ECharts 呈现学情图表，AspectJ 切面统一记录操作日志。AI 能力采用大模型与本地规则双通道，未配置密钥时自动降级、功能依然可用；Web 端已实现完整业务，并提供 Electron 桌面客户端，可打包为免安装 exe 直接使用。</p><div class="tech-tags"><span>Spring Boot 4</span><span>Java 17</span><span>Vue 3</span><span>Element Plus</span><span>MyBatis</span><span>MySQL</span><span>Redis 降级</span><span>JWT + BCrypt</span><span>RBAC 三层校验</span><span>Apache POI</span><span>PDFBox</span><span>ECharts</span><span>AspectJ 日志</span><span>大模型 AI</span><span>本地规则降级</span><span>Electron 桌面端</span></div><a class="back-login" href="#top" @click.prevent="scrollToSection('top')">回到顶部 <el-icon><ArrowUp /></el-icon></a></div><div class="technology-panel"><div class="tech-orbit orbit-one"></div><div class="tech-orbit orbit-two"></div><div class="tech-core"><el-icon><Connection /></el-icon><b>稳定 · 高效 · 可拓展</b></div></div></div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  User, Lock, Reading,
  Collection, MagicStick, DataAnalysis, Warning, Connection,
  Clock, Document, TrendCharts, ArrowDown, ArrowUp,
  Management, EditPen, UserFilled, Notebook, Promotion
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 「记住密码」/「自动登录」开关（仅勾选时才生效，关闭时下一次必须重新登录）
const rememberPwd = ref(false)
const autoLogin = ref(false)

const SAVED_USER_KEY = 'exam_saved_username'
const SAVED_PWD_KEY = 'exam_saved_password'
const AUTO_LOGIN_KEY = 'exam_auto_login_flag'

function updateActivePage() {
  const container = document.querySelector('.login-page')
  if (!container) return

  const sections = [
    { id: 'top', element: container.querySelector('.login-hero') },
    { id: 'system-intro', element: document.getElementById('system-intro') },
    { id: 'roles-intro', element: document.getElementById('roles-intro') },
    { id: 'technology-intro', element: document.getElementById('technology-intro') }
  ].filter(({ element }) => element)
  const scrollTop = container.scrollTop
  const current = sections.reduce((active, section) => {
    const distance = Math.abs(section.element.offsetTop - scrollTop)
    return distance < active.distance ? { id: section.id, distance } : active
  }, { id: sections[0]?.id || 'top', distance: Infinity }).id

  container.querySelectorAll('.page-nav a').forEach((link) => {
    link.classList.toggle('is-active', link.getAttribute('href') === `#${current}`)
  })
}

// 进入页面时，如果之前勾过「记住密码」就回填账号密码；都勾过则自动登录
onMounted(() => {
  const savedUser = localStorage.getItem(SAVED_USER_KEY) || ''
  const savedPwd = localStorage.getItem(SAVED_PWD_KEY) || ''
  const wantAuto = localStorage.getItem(AUTO_LOGIN_KEY) === '1'

  if (savedUser) {
    form.username = savedUser
    rememberPwd.value = true
  }
  if (savedPwd) {
    form.password = savedPwd
    rememberPwd.value = true
  }
  if (savedUser && savedPwd && wantAuto) {
    autoLogin.value = true
    // 下一帧触发登录，避免与表单挂载冲突
    setTimeout(() => handleLogin(), 50)
  }
  updateActivePage()
  const loginPage = document.querySelector('.login-page')
  loginPage?.addEventListener('scroll', updateActivePage, { passive: true })
  loginPage?.addEventListener('wheel', updateActivePage, { passive: true })
  loginPage?.querySelectorAll('a[href^="#"]').forEach((link) => {
    link.addEventListener('click', () => window.setTimeout(updateActivePage, 120))
  })
  window.addEventListener('resize', updateActivePage)
})

onBeforeUnmount(() => {
  const loginPage = document.querySelector('.login-page')
  loginPage?.removeEventListener('scroll', updateActivePage)
  loginPage?.removeEventListener('wheel', updateActivePage)
  window.removeEventListener('resize', updateActivePage)
})

let wheelLocked = false

function handlePageWheel(event) {
  if (wheelLocked || Math.abs(event.deltaY) < 10) return
  event.preventDefault()
  const sections = [...document.querySelectorAll('.login-hero, .intro-page')]
  const currentIndex = sections.reduce((index, section, itemIndex) => {
    return Math.abs(section.getBoundingClientRect().top) < Math.abs(sections[index].getBoundingClientRect().top) ? itemIndex : index
  }, 0)
  const nextIndex = event.deltaY > 0 ? Math.min(currentIndex + 1, sections.length - 1) : Math.max(currentIndex - 1, 0)
  if (nextIndex === currentIndex) return
  wheelLocked = true
  sections[nextIndex].scrollIntoView({ behavior: 'smooth', block: 'start' })
  window.setTimeout(() => { wheelLocked = false }, 700)
}

function scrollToSection(id) {
  const container = document.querySelector('.login-page')
  const section = id === 'top' ? container?.querySelector('.login-hero') : document.getElementById(id)
  if (!container || !section) return

  container.scrollTo({
    top: section.offsetTop,
    behavior: 'smooth'
  })
  window.setTimeout(updateActivePage, 120)
}

function scrollToTop() {
  scrollToSection('top')
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await login(form)
      const { token, userId, username, realName, role, roleId, menus } = res.data
      authStore.setAuth(token, { userId, username, realName, role, roleId }, menus || [])

      // 登录成功后再处理「记住密码 / 自动登录」开关
      if (rememberPwd.value) {
        localStorage.setItem(SAVED_USER_KEY, form.username)
        localStorage.setItem(SAVED_PWD_KEY, form.password)
      } else {
        localStorage.removeItem(SAVED_USER_KEY)
        localStorage.removeItem(SAVED_PWD_KEY)
        localStorage.removeItem(AUTO_LOGIN_KEY)
      }
      localStorage.setItem(AUTO_LOGIN_KEY, autoLogin.value ? '1' : '0')

      ElMessage.success('登录成功')
      router.push(role === 0 ? '/student/dashboard' : '/')
    } catch (err) {
      console.error('登录失败:', err)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
/* ============================================================
 * 整页布局：渐变背景 + 浮动光斑 + 居中卡片
 * 抛弃之前 40/60 分栏设计，整体更现代、视觉重心明确
 * ============================================================ */
.login-page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  overflow: hidden;
  background:
    radial-gradient(ellipse at top left,  rgba(124, 110, 246, 0.32) 0%, rgba(124, 110, 246, 0) 55%),
    radial-gradient(ellipse at bottom right, rgba(96, 165, 250, 0.30) 0%, rgba(96, 165, 250, 0) 55%),
    linear-gradient(135deg, #f6f7fb 0%, #eef1fb 50%, #f4eefb 100%);
  display: flex;
  flex-direction: column;
}

/* ===== 浮动光斑（纯 CSS，呼吸 + 漂移）===== */
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.55;
  pointer-events: none;
  will-change: transform;
}
.blob-1 {
  width: 360px; height: 360px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.55) 0%, rgba(139, 92, 246, 0) 70%);
  top: -120px; left: -100px;
  animation: float1 14s ease-in-out infinite alternate;
}
.blob-2 {
  width: 420px; height: 420px;
  background: radial-gradient(circle, rgba(96, 165, 250, 0.50) 0%, rgba(96, 165, 250, 0) 70%);
  bottom: -150px; right: -120px;
  animation: float2 18s ease-in-out infinite alternate;
}
.blob-3 {
  width: 260px; height: 260px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.40) 0%, rgba(236, 72, 153, 0) 70%);
  top: 40%; right: 20%;
  animation: float3 22s ease-in-out infinite alternate;
}
@keyframes float1 {
  0%   { transform: translate(0, 0) scale(1); }
  100% { transform: translate(60px, 40px) scale(1.1); }
}
@keyframes float2 {
  0%   { transform: translate(0, 0) scale(1); }
  100% { transform: translate(-50px, -60px) scale(1.12); }
}
@keyframes float3 {
  0%   { transform: translate(0, 0) scale(0.95); }
  100% { transform: translate(-40px, 50px) scale(1.08); }
}

/* ===== 几何艺术图案（圆环 / 方块 / 三角 / 十字 / 点阵）===== */
.deco {
  position: absolute;
  pointer-events: none;
  z-index: 0;
}

/* 空心圆环 */
.deco-ring-1 {
  width: 180px; height: 180px;
  border: 3px solid rgba(102, 126, 234, 0.25);
  border-radius: 50%;
  top: 18%; right: 12%;
  animation: spin 30s linear infinite;
}
.deco-ring-2 {
  width: 240px; height: 240px;
  border: 2px dashed rgba(118, 75, 162, 0.22);
  border-radius: 50%;
  bottom: 8%; left: 6%;
  animation: spin 45s linear infinite reverse;
}

/* 方块（旋转 45°） */
.deco-square-1 {
  width: 60px; height: 60px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.18), rgba(118, 75, 162, 0.15));
  border: 2px solid rgba(102, 126, 234, 0.35);
  border-radius: 10px;
  transform: rotate(15deg);
  top: 38%; left: 14%;
  animation: float1 12s ease-in-out infinite alternate;
}
.deco-square-2 {
  width: 40px; height: 40px;
  background: linear-gradient(135deg, rgba(236, 72, 153, 0.15), rgba(244, 114, 182, 0.12));
  border: 2px solid rgba(236, 72, 153, 0.30);
  border-radius: 8px;
  transform: rotate(20deg);
  bottom: 22%; right: 16%;
  animation: float2 14s ease-in-out infinite alternate;
}

/* 三角形（SVG clip-path） */
.deco-triangle {
  width: 0; height: 0;
  border-left: 50px solid transparent;
  border-right: 50px solid transparent;
  border-bottom: 86px solid rgba(102, 126, 234, 0.12);
  top: 12%; left: 30%;
  transform: rotate(-12deg);
  animation: float3 16s ease-in-out infinite alternate;
}

/* 十字线 */
.deco-cross {
  width: 60px; height: 60px;
  top: 32%; right: 28%;
}
.deco-cross::before,
.deco-cross::after {
  content: '';
  position: absolute;
  background: rgba(118, 75, 162, 0.20);
  border-radius: 2px;
}
.deco-cross::before {
  width: 2px; height: 60px; left: 50%; top: 0; transform: translateX(-50%);
}
.deco-cross::after {
  width: 60px; height: 2px; top: 50%; left: 0; transform: translateY(-50%);
}
.deco-cross-2 {
  width: 40px; height: 40px;
  bottom: 32%; left: 32%;
}
.deco-cross-2::before { width: 2px; height: 40px; }
.deco-cross-2::after { width: 40px; height: 2px; }

/* 点阵网格（3x3 点） */
.deco-dots {
  width: 70px; height: 70px;
  background-image:
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(102, 126, 234, 0.45) 3px, transparent 3px);
  background-size: 20px 20px;
  background-position: 0 0, 25px 0, 50px 0, 0 25px, 25px 25px, 50px 25px, 0 50px, 25px 50px, 50px 50px;
  background-repeat: no-repeat;
  top: 62%; left: 22%;
  animation: float1 10s ease-in-out infinite alternate;
}
.deco-dots-2 {
  background-image:
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px),
    radial-gradient(circle 3px, rgba(236, 72, 153, 0.40) 3px, transparent 3px);
  top: 8%; right: 30%;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 底部波浪 */
.deco-wave {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 180px;
  z-index: 0;
  pointer-events: none;
}

/* ===== 背景宣传词（8 个大字浮动标签）===== */
.slogans {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}
.slogan {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 14px 22px;
  font-size: 18px;
  font-weight: 600;
  color: #3d2b63;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 999px;
  box-shadow: 0 8px 28px rgba(102, 126, 234, 0.18);
  white-space: nowrap;
  letter-spacing: 1.5px;
}
.slogan .el-icon {
  font-size: 22px;
  color: #764ba2;
}
.s-1 { top: 10%;  left: 4%;  animation: drift1 9s  ease-in-out infinite alternate; }
.s-2 { top: 20%;  right: 3%; animation: drift2 11s ease-in-out infinite alternate; }
.s-3 { top: 42%;  left: 3%;  animation: drift3 13s ease-in-out infinite alternate; }
.s-4 { top: 62%;  right: 4%; animation: drift1 10s ease-in-out infinite alternate-reverse; }
.s-5 { bottom: 14%; left: 6%; animation: drift2 12s ease-in-out infinite alternate; }
.s-6 { top: 30%;  left: 18%; animation: drift3 10s ease-in-out infinite alternate; }
.s-7 { bottom: 26%; right: 14%; animation: drift1 14s ease-in-out infinite alternate-reverse; }
.s-8 { top: 76%;  left: 28%; animation: drift2 11s ease-in-out infinite alternate; }
@keyframes drift1 { 0% { transform: translateY(0); } 100% { transform: translateY(-12px); } }
@keyframes drift2 { 0% { transform: translateX(0); } 100% { transform: translateX(10px); } }
@keyframes drift3 { 0% { transform: translate(0, 0); } 100% { transform: translate(8px, -10px); } }

/* ===== 顶部小品牌条 ===== */
.learning-floaters { position: absolute; inset: 0; z-index: 1; overflow: hidden; pointer-events: none; }
.page-floaters { z-index: 0; opacity: .72; }
.person-float { position: absolute; display: flex; flex-direction: column; align-items: center; color: #6950bd; font-size: 11px; animation: personFloat 6s ease-in-out infinite; }
.teacher-float { top: 20%; right: 18%; animation-delay: 1s; }
.student-float { bottom: 16%; left: 8%; animation-delay: 2.6s; }
.person-head { position: relative; width: 27px; height: 27px; border-radius: 50%; background: #ffd9bd; box-shadow: 0 4px 10px rgba(105, 80, 189, .14); }
.teacher-head { background: #ffd2b3; }
.teacher-head::before { content: ''; position: absolute; top: -4px; left: -2px; width: 31px; height: 14px; border-radius: 18px 18px 5px 5px; background: #5b4b92; }
.student-head { background: #f4c5a3; }
.student-head::before { content: ''; position: absolute; top: -3px; left: 3px; width: 21px; height: 10px; border-radius: 14px 14px 3px 3px; background: #2b4277; }
.person-body { position: relative; width: 42px; height: 32px; margin-top: -1px; border-radius: 16px 16px 8px 8px; background: linear-gradient(135deg, #667eea, #764ba2); box-shadow: 0 8px 15px rgba(105, 80, 189, .2); }
.student-float .person-body { background: linear-gradient(135deg, #27b9c4, #477fe5); }
.person-body::before { content: ''; position: absolute; top: 10px; left: 50%; width: 14px; height: 3px; border-radius: 3px; background: rgba(255, 255, 255, .8); transform: translateX(-50%); }
.person-body i { position: absolute; top: 7px; width: 4px; height: 4px; border-radius: 50%; background: #fff; }
.person-body i:first-child { left: 11px; }.person-body i:last-child { right: 11px; }
.person-float span { margin-top: 7px; padding: 4px 8px; border: 1px solid rgba(105, 80, 189, .1); border-radius: 99px; background: rgba(255, 255, 255, .72); }
@keyframes personFloat { 0%, 100% { transform: translateY(0) rotate(-2deg); } 50% { transform: translateY(-15px) rotate(2deg); } }
.page-floaters .orbit-book { top: 16%; left: 5%; }
.page-floaters .orbit-pencil { top: auto; right: 6%; bottom: 23%; }
.page-floaters .orbit-chart { right: 9%; bottom: 12%; left: auto; }
.page-floaters .learning-bot { right: 4%; bottom: 8%; transform: scale(.8); }
.page-floaters .learning-orbit { animation-duration: 9s; }
.page-floaters .learning-bot { animation-duration: 6s; }
.learning-orbit { position: absolute; display: flex; align-items: center; gap: 8px; padding: 10px 14px; border: 1px solid rgba(105, 80, 189, .16); border-radius: 14px; color: #6950bd; background: rgba(255, 255, 255, .62); box-shadow: 0 12px 28px rgba(82, 67, 145, .1); backdrop-filter: blur(8px); font-size: 11px; animation: floatLearning 7s ease-in-out infinite; }
.learning-orbit .el-icon { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 8px; color: #fff; background: linear-gradient(135deg, #667eea, #764ba2); font-size: 14px; }
.orbit-book { top: 19%; left: 12%; }
.orbit-pencil { top: 32%; right: 12%; animation-delay: 1.8s; }
.orbit-chart { bottom: 23%; left: 15%; animation-delay: 3.4s; }
.learning-bot { position: absolute; right: 8%; bottom: 14%; display: flex; flex-direction: column; align-items: center; color: #6950bd; font-size: 11px; animation: botFloat 5s ease-in-out infinite; }
.bot-head { position: relative; display: flex; align-items: center; justify-content: center; gap: 12px; width: 54px; height: 42px; border: 2px solid #8578d8; border-radius: 18px 18px 14px 14px; background: linear-gradient(145deg, #fff, #e7e5ff); box-shadow: 0 10px 22px rgba(105, 80, 189, .2); }
.bot-head i { width: 7px; height: 7px; border-radius: 50%; background: #20c997; box-shadow: 0 0 9px #20c997; animation: botBlink 3s infinite; }
.bot-body { display: grid; place-items: center; width: 42px; height: 31px; margin-top: -2px; border-radius: 8px 8px 14px 14px; color: #fff; background: linear-gradient(135deg, #667eea, #764ba2); box-shadow: 0 8px 16px rgba(105, 80, 189, .22); font-size: 17px; }
.bot-antenna { width: 2px; height: 12px; background: #8578d8; }
.bot-antenna::before { content: ''; display: block; width: 6px; height: 6px; margin: -4px 0 0 -2px; border-radius: 50%; background: #20c997; box-shadow: 0 0 10px #20c997; }
.learning-bot span { margin-top: 8px; padding: 5px 9px; border-radius: 99px; background: rgba(255, 255, 255, .72); }
@keyframes floatLearning { 0%, 100% { transform: translate3d(0, 0, 0) rotate(-2deg); } 50% { transform: translate3d(10px, -16px, 0) rotate(2deg); } }
@keyframes botFloat { 50% { transform: translate3d(-8px, -14px, 0); } }
@keyframes botBlink { 0%, 46%, 52%, 100% { opacity: 1; } 49% { opacity: .15; } }
.top-brand {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 22px 32px;
}
.brand-logo {
  width: 36px; height: 36px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 6px 18px rgba(118, 75, 162, 0.35);
}
.brand-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0.5px;
}

/* ===== 居中卡片区 ===== */
.login-wrapper {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-card {
  width: 420px;
  max-width: 100%;
  padding: 24px 36px 18px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(14px) saturate(140%);
  -webkit-backdrop-filter: blur(14px) saturate(140%);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  box-shadow:
    0 20px 60px -10px rgba(60, 50, 120, 0.18),
    0 8px 20px -8px rgba(60, 50, 120, 0.10);
  animation: cardRise 0.7s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}
@keyframes cardRise {
  from { opacity: 0; transform: translateY(24px) scale(0.98); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

/* 卡片头部：圆角图标 + 标题 + 副标题 */
.card-head {
  text-align: center;
  margin-bottom: 16px;
}
.card-logo {
  width: 44px; height: 44px;
  margin: 0 auto 8px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 8px 18px rgba(118, 75, 162, 0.35);
}
.card-logo .el-icon {
  font-size: 22px !important;
}
.card-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 2px;
  letter-spacing: 0.5px;
}
.card-subtitle {
  font-size: 12px;
  color: #6b7280;
  margin: 0;
  letter-spacing: 1px;
}

/* ===== 表单控件精修（scoped 穿透 Element Plus）===== */
.login-card :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #f7f8fc;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  transition: box-shadow .2s ease, background .2s ease;
  padding: 4px 12px;
}
.login-card :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c7c9d4 inset;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow:
    0 0 0 1px #764ba2 inset,
    0 0 0 4px rgba(118, 75, 162, 0.12) !important;
}
.login-card :deep(.el-input__inner) {
  height: 36px;
  font-size: 14px;
}
.login-card :deep(.el-form-item) {
  margin-bottom: 12px;
}

/* 「记住密码 / 自动登录」勾选项行 */
.options-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 14px;
  padding: 0 2px;
}
.options-row :deep(.el-checkbox) {
  color: #6b7280;
  font-size: 13px;
  --el-checkbox-input-height: 16px;
  --el-checkbox-input-width: 16px;
}
.options-row :deep(.el-checkbox__label) {
  padding-left: 6px;
}

/* 登录按钮：渐变 + hover 上浮光晕 */
.login-card :deep(.el-button.login-btn) {
  width: 100%;
  height: 40px;
  font-size: 15px;
  letter-spacing: 6px;
  font-weight: 500;
  background-image: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important;
  border-radius: 10px !important;
  transition: transform .2s ease, box-shadow .2s ease, filter .2s ease !important;
  box-shadow: 0 8px 20px rgba(118, 75, 162, 0.30) !important;
}
.login-card :deep(.el-button.login-btn:hover) {
  transform: translateY(-1px) !important;
  box-shadow: 0 12px 26px rgba(118, 75, 162, 0.40) !important;
  filter: brightness(1.05);
}
.login-card :deep(.el-button.login-btn:active) {
  transform: translateY(0) !important;
  box-shadow: 0 4px 12px rgba(118, 75, 162, 0.32) !important;
}

/* 底部温馨提示（两行：标题 + 详情） */
.card-foot {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px dashed #e5e7eb;
  text-align: center;
}
.hint-title {
  margin: 0 0 3px;
  font-size: 12.5px;
  font-weight: 600;
  color: #764ba2;
  letter-spacing: 1px;
}
.hint-detail {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
  letter-spacing: 0.3px;
}

.bottom-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #9ca3af;
  letter-spacing: 0.5px;
}

/* ===== 多页面滚动介绍区 ===== */
.system-page-layout,
.technology-layout { position: relative; z-index: 1; display: grid; grid-template-columns: minmax(300px, .78fr) minmax(580px, 1.22fr); align-items: center; gap: 58px; }
.system-page-layout::after, .technology-layout::after { content: '01 / 04'; position: absolute; right: 0; bottom: -34px; color: rgba(105, 80, 189, .28); font-family: monospace; font-size: 11px; letter-spacing: 3px; }
.technology-layout::after { content: '04 / 04'; }
.system-page-layout .role-page-copy { text-align: left; }
.system-page-layout .role-page-copy > p { max-width: 440px; }
.system-page-layout .back-login { margin-top: 30px; }
.system-grid { display: grid; grid-template-columns: repeat(2, minmax(250px, 1fr)); gap: 20px; margin: 0; }
.system-grid .feature-card { min-height: 166px; padding: 28px 24px; align-items: flex-start; }
.system-grid .feature-card h3 { font-size: 17px; }
.system-grid .feature-card p { max-width: 150px; font-size: 13px; line-height: 1.9; }
.role-grid-three { width: min(1040px, 100%); margin: 54px auto 0; }
.page-actions { display: flex; justify-content: center; align-items: center; gap: 24px; margin-top: 46px; }
.page-next-inline { display: inline-flex; align-items: center; gap: 6px; color: #6950bd; font-size: 13px; text-decoration: none; }
.technology-page { background: linear-gradient(135deg, #f3f1ff 0%, #fbfcff 48%, #edf4ff 100%); }
.technology-copy { text-align: left; }
.technology-copy h2 { margin: 14px 0; color: #20243a; font-size: clamp(30px, 4vw, 48px); }
.technology-copy p { max-width: 520px; color: #7b8198; font-size: 16px; line-height: 1.9; }
.tech-tags { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 26px; }
.tech-tags span { padding: 9px 15px; border: 1px solid rgba(118, 75, 162, .14); border-radius: 999px; color: #6950bd; background: rgba(255, 255, 255, .7); font-size: 12px; }
.technology-copy .back-login { margin-top: 34px; }
.section-tech-grid { position: absolute; inset: 0; opacity: .6; background-image: linear-gradient(rgba(105, 80, 189, .085) 1px, transparent 1px), linear-gradient(90deg, rgba(105, 80, 189, .085) 1px, transparent 1px); background-size: 48px 48px; mask-image: linear-gradient(to bottom, transparent, #000 12%, #000 88%, transparent); pointer-events: none; }
.product-intro::before, .product-intro::after { content: ''; position: absolute; z-index: 0; border-radius: 50%; pointer-events: none; filter: blur(2px); }
.product-intro::before { top: -18%; left: -8%; width: 430px; height: 430px; background: radial-gradient(circle, rgba(120, 102, 235, .2), rgba(120, 102, 235, 0) 68%); animation: ambientFloat 12s ease-in-out infinite alternate; }
.product-intro::after { right: -10%; bottom: -24%; width: 520px; height: 520px; background: radial-gradient(circle, rgba(64, 190, 209, .16), rgba(64, 190, 209, 0) 68%); animation: ambientFloat 15s ease-in-out infinite alternate-reverse; }
.system-page-layout::before, .technology-layout::before { content: ''; position: absolute; top: 11%; right: 4%; width: 150px; height: 150px; border: 1px solid rgba(105, 80, 189, .12); border-radius: 50%; box-shadow: 0 0 0 18px rgba(105, 80, 189, .025), 0 0 0 38px rgba(105, 80, 189, .018); animation: radarPulse 5s ease-in-out infinite; pointer-events: none; }
@keyframes ambientFloat { to { transform: translate(38px, 28px) scale(1.14); } }
@keyframes radarPulse { 50% { opacity: .35; transform: scale(.82); } }

.eyebrow-dot { display: inline-block; width: 6px; height: 6px; margin-right: 8px; border-radius: 50%; background: #1cc7ba; box-shadow: 0 0 12px #1cc7ba; vertical-align: middle; }
.role-page-copy h2 em { color: #6950bd; font-style: normal; text-shadow: 0 0 24px rgba(105, 80, 189, .18); }
.system-status { display: flex; flex-wrap: wrap; gap: 18px; margin-top: 24px; color: #777c96; font-size: 12px; }
.system-status span { display: inline-flex; align-items: center; gap: 7px; }
.system-status i { width: 7px; height: 7px; border-radius: 50%; background: #20c997; box-shadow: 0 0 10px rgba(32, 201, 151, .75); }
.system-status i.dot-alt { background: #6950bd; box-shadow: 0 0 10px rgba(105, 80, 189, .75); }
.ai-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 20px; }
.ai-chips span { padding: 6px 13px; border: 1px solid rgba(118, 75, 162, .16); border-radius: 999px; color: #6950bd; background: rgba(255, 255, 255, .78); font-size: 12px; letter-spacing: .3px; }
.feature-card, .role-card { position: relative; overflow: hidden; }
.feature-card::before, .role-card::before { content: ''; position: absolute; top: 0; left: -120%; width: 80%; height: 1px; background: linear-gradient(90deg, transparent, #7b6be0, transparent); animation: scanLine 4.8s ease-in-out infinite; }
.feature-card:nth-child(2)::before, .role-card:nth-child(2)::before { animation-delay: 1.2s; }
.feature-card:nth-child(3)::before, .role-card:nth-child(3)::before { animation-delay: 2.4s; }
.feature-card:nth-child(4)::before { animation-delay: 3.6s; }
@keyframes scanLine { 0%, 45% { left: -120%; opacity: 0; } 55% { opacity: 1; } 100% { left: 140%; opacity: 0; } }
.technology-panel { position: relative; width: min(360px, 100%); aspect-ratio: 1; margin: 0 auto; border-radius: 50%; background: radial-gradient(circle, rgba(255, 255, 255, .9) 0 24%, rgba(221, 216, 250, .55) 25% 26%, transparent 27%); filter: drop-shadow(0 20px 35px rgba(105, 80, 189, .12)); }
.tech-particle { position: absolute; z-index: 0; width: 8px; height: 8px; border-radius: 50%; background: #4fd8d0; box-shadow: 0 0 18px #4fd8d0; animation: particleFloat 5s ease-in-out infinite; }
.particle-one { top: 24%; right: 24%; }
.particle-two { bottom: 28%; left: 20%; width: 5px; height: 5px; animation-delay: 1.6s; background: #8b7ae0; box-shadow: 0 0 15px #8b7ae0; }
.particle-three { top: 62%; right: 10%; width: 4px; height: 4px; animation-delay: 3s; }
@keyframes particleFloat { 50% { transform: translate(10px, -14px); opacity: .55; } }
.technology-panel::before { content: 'AI  DATA  CLOUD  WEB'; position: absolute; inset: 7%; display: grid; place-items: center; border: 1px solid rgba(105, 80, 189, .12); border-radius: 50%; color: rgba(105, 80, 189, .3); font-size: 10px; letter-spacing: 7px; transform: rotate(-22deg); }
.tech-core { z-index: 1; }
.tech-orbit { position: absolute; inset: 11%; border: 1px dashed rgba(105, 80, 189, .25); border-radius: 50%; animation: spin 28s linear infinite; }
.orbit-two { inset: 23%; border-style: solid; animation-direction: reverse; animation-duration: 20s; }
.tech-core { position: absolute; inset: 35%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; border-radius: 50%; color: #fff; background: linear-gradient(135deg, #667eea, #764ba2); box-shadow: 0 16px 35px rgba(118, 75, 162, .28); text-align: center; }
.tech-core .el-icon { font-size: 30px; }
.tech-core b { font-size: 11px; font-weight: 500; }
.technology-copy .eyebrow, .section-heading .eyebrow { text-shadow: 0 0 16px rgba(105, 80, 189, .28); }
.page-nav a.is-active::after { transform: scale(1.35); background: #6950bd; box-shadow: 0 0 14px rgba(105, 80, 189, .55); }

.page-nav {
  position: fixed;
  top: 50%;
  right: 22px;
  z-index: 10;
  display: grid;
  gap: 12px;
  transform: translateY(-50%);
}
.page-nav a {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 18px;
  height: 18px;
  color: transparent;
  text-decoration: none;
}
.page-nav a::after {
  content: '';
  display: block;
  width: 9px;
  height: 9px;
  border: 2px solid rgba(105, 80, 189, .30);
  border-radius: 50%;
  background: rgba(255, 255, 255, .75);
  transition: transform .2s ease, background .2s ease;
}
.page-nav a:hover::after {
  transform: scale(1.45);
  background: #6950bd;
}
.page-nav a span { position: absolute; right: 24px; opacity: 0; white-space: nowrap; color: #6950bd; font-size: 12px; pointer-events: none; transition: opacity .2s ease; }
.page-nav a:hover span { opacity: 1; }


.login-page {
  height: 100vh;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  scroll-behavior: smooth;
  overscroll-behavior-y: contain;
}
.login-hero,
.intro-page {
  scroll-snap-stop: always;
}
.login-hero,
.intro-page {
  position: relative;
  min-height: 100vh;
  box-sizing: border-box;
  scroll-snap-align: start;
}
.login-hero {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    radial-gradient(ellipse at top left, rgba(124, 110, 246, .32) 0%, rgba(124, 110, 246, 0) 55%),
    radial-gradient(ellipse at bottom right, rgba(96, 165, 250, .30) 0%, rgba(96, 165, 250, 0) 55%),
    linear-gradient(135deg, #f6f7fb 0%, #eef1fb 50%, #f4eefb 100%);
}
.login-hero .login-wrapper { min-height: 0; }
.product-intro {
  display: flex;
  align-items: center;
  padding: 84px 32px;
  background: linear-gradient(145deg, #f8f8ff 0%, #fff 50%, #f4f0ff 100%);
}
.roles-page { position: relative; background: linear-gradient(135deg, #f8f7ff 0%, #fefeff 42%, #eef9fb 100%); }
.roles-page .role-card:nth-child(1) { transform: translateY(14px); }
.roles-page .role-card:nth-child(3) { transform: translateY(-14px); }
.roles-page .role-card:nth-child(1):hover, .roles-page .role-card:nth-child(3):hover { transform: translateY(-3px); }
.intro-overview { background: linear-gradient(135deg, #f1efff 0%, #fbfcff 52%, #eeeaff 100%); }
.intro-role { background: linear-gradient(135deg, #faf9ff 0%, #fff 50%, #f2f0ff 100%); }
.page-inner { width: min(1120px, 100%); margin: 0 auto; }
.overview-layout { display: grid; grid-template-columns: 1.05fr .95fr; align-items: center; gap: 84px; }
.intro-copy { text-align: left; }
.intro-copy h2 { max-width: 620px; }
.intro-copy p { max-width: 580px; }
.overview-stats { display: flex; align-items: baseline; gap: 10px; margin-top: 34px; color: #7b8198; font-size: 13px; }
.overview-stats strong { margin-left: 14px; color: #6950bd; font-size: 34px; }
.overview-stats strong:first-child { margin-left: 0; }
.overview-panel,
.role-dashboard,
.student-visual { padding: 30px; border: 1px solid rgba(118, 75, 162, .12); border-radius: 26px; background: rgba(255, 255, 255, .76); box-shadow: 0 24px 56px rgba(82, 67, 145, .12); backdrop-filter: blur(12px); }
.panel-line { display: flex; justify-content: space-between; gap: 20px; padding: 20px 0; border-bottom: 1px solid #eeeef7; color: #7b8198; font-size: 14px; }
.panel-line:last-child { border-bottom: 0; }
.panel-line b { color: #3d3563; font-weight: 600; }
.page-next { position: absolute; bottom: 34px; left: 50%; display: inline-flex; align-items: center; gap: 6px; color: #6950bd; font-size: 13px; text-decoration: none; transform: translateX(-50%); animation: hintFloat 2s ease-in-out infinite; }
.system-grid { width: 100%; max-width: none; margin: 0; }
.process-line { display: flex; justify-content: center; align-items: center; gap: 26px; margin-top: 62px; color: #6950bd; font-size: 15px; font-weight: 600; }
.process-line i { color: #c2b8e8; font-size: 22px; font-style: normal; }
.role-page-layout { display: grid; grid-template-columns: 1fr 1fr; align-items: center; gap: 92px; }
.reverse-layout { grid-template-columns: 1fr 1fr; }
.role-page-copy h2 { margin: 14px 0; color: #20243a; font-size: clamp(28px, 4vw, 46px); line-height: 1.2; }
.role-page-copy > p { max-width: 510px; margin: 0; color: #7b8198; font-size: 16px; line-height: 1.9; }
.role-list { display: grid; gap: 13px; margin: 30px 0 0; padding: 0; list-style: none; color: #4e5270; font-size: 14px; }
.role-list li { display: flex; align-items: center; gap: 10px; }
.role-list li::before { content: '✓'; display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; color: #fff; background: linear-gradient(135deg, #667eea, #764ba2); font-size: 12px; }
.dashboard-title { display: flex; justify-content: space-between; margin-bottom: 26px; color: #343653; font-size: 17px; font-weight: 600; }
.dashboard-title span { color: #999eb4; font-size: 12px; font-weight: 400; }
.dashboard-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.dashboard-metrics b { padding: 18px 8px; border-radius: 14px; color: #6950bd; background: #f3f1ff; font-size: 22px; text-align: center; }
.dashboard-metrics small { display: block; margin-top: 7px; color: #9397ae; font-size: 11px; font-weight: 400; }
.dashboard-bars { display: flex; align-items: end; gap: 14px; height: 150px; margin-top: 28px; padding: 0 16px; border-bottom: 1px solid #e7e5f2; }
.dashboard-bars i, .student-chart i { flex: 1; min-height: 12px; border-radius: 7px 7px 0 0; background: linear-gradient(180deg, #8b7ae0, #c5bdf1); }
.teacher-tasks { display: grid; gap: 12px; }
.teacher-tasks div { display: flex; justify-content: space-between; padding: 19px; border-radius: 14px; background: #f5f3ff; color: #8a8ea5; }
.teacher-tasks b { color: #51417f; }
.dashboard-progress { margin-top: 28px; color: #777c96; font-size: 13px; }
.dashboard-progress b { float: right; color: #6950bd; }
.dashboard-progress em { display: block; height: 9px; margin-top: 14px; overflow: hidden; border-radius: 99px; background: #e8e5f7; }
.dashboard-progress em i { display: block; width: 82%; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #667eea, #764ba2); }
.student-back { margin-top: 34px; }
.student-visual { display: flex; justify-content: center; padding: 46px; }
.student-card { width: min(300px, 100%); padding: 26px; border-radius: 22px; background: linear-gradient(145deg, #f1efff, #fff); }
.student-avatar { display: inline-flex; align-items: center; justify-content: center; width: 52px; height: 52px; margin-bottom: 28px; border-radius: 16px; color: #fff; background: linear-gradient(135deg, #667eea, #764ba2); font-size: 25px; }
.student-card b, .student-card strong { display: block; color: #4c426f; }
.student-card strong { margin-top: 8px; color: #6950bd; font-size: 24px; }
.student-chart { display: flex; align-items: end; gap: 9px; height: 130px; margin-top: 28px; padding-top: 16px; border-bottom: 1px solid #ddd8f5; }

/* ===== 可纵向浏览的系统介绍区 ===== */
.login-page {
  min-height: 100vh;
  overflow-x: hidden;
  overflow-y: auto;
  scroll-behavior: smooth;
}
.login-wrapper {
  min-height: calc(100vh - 80px);
  box-sizing: border-box;
}
.scroll-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 22px;
  color: #667eea;
  font-size: 13px;
  text-decoration: none;
  transition: color .2s ease, transform .2s ease;
  animation: hintFloat 2s ease-in-out infinite;
}
.scroll-hint:hover {
  color: #764ba2;
  transform: translateY(2px);
}
@keyframes hintFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(5px); }
}
.product-intro {
  position: relative;
  z-index: 3;
  padding: 104px 32px 72px;
  background:
    linear-gradient(180deg, rgba(247, 248, 255, .97) 0%, rgba(255, 255, 255, .98) 48%, #f7f4ff 100%);
}
.intro-inner {
  width: min(1120px, 100%);
  margin: 0 auto;
}
.intro-heading,
.section-heading {
  text-align: center;
}
.eyebrow {
  display: inline-block;
  color: #764ba2;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}
.intro-heading h2,
.section-heading h2,
.intro-bottom h2 {
  margin: 12px 0 10px;
  color: #20243a;
  font-size: clamp(26px, 4vw, 40px);
  line-height: 1.25;
  letter-spacing: 1px;
}
.intro-heading p,
.intro-bottom p {
  margin: 0;
  color: #7b8198;
  font-size: 15px;
  line-height: 1.8;
}
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 52px;
}
.feature-card,
.role-card {
  border: 1px solid rgba(118, 75, 162, .10);
  border-radius: 22px;
  background: rgba(255, 255, 255, .82);
  box-shadow: 0 16px 38px rgba(82, 67, 145, .08);
  transition: transform .25s ease, box-shadow .25s ease;
}
.feature-card {
  display: flex;
  gap: 16px;
  padding: 26px;
}
.feature-card:hover,
.role-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 22px 44px rgba(82, 67, 145, .14);
}
.feature-card-main {
  background: linear-gradient(135deg, #f0efff 0%, #fff 100%);
}
.feature-icon,
.role-icon {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #667eea, #764ba2);
  box-shadow: 0 8px 18px rgba(118, 75, 162, .23);
}
.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: 13px;
  font-size: 21px;
}
.feature-card h3,
.role-card h3 {
  margin: 2px 0 8px;
  color: #2d3150;
  font-size: 17px;
}
.feature-card p,
.role-card p {
  margin: 0;
  color: #7b8198;
  font-size: 13px;
  line-height: 1.8;
}
.role-section {
  margin-top: 112px;
}
.section-heading h2 {
  font-size: clamp(24px, 3vw, 32px);
}
.role-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 38px;
}
.role-card {
  position: relative;
  padding: 30px;
  overflow: hidden;
}
.role-card-accent {
  background: linear-gradient(145deg, #f1efff 0%, #fff 72%);
}
.role-number {
  position: absolute;
  top: 18px;
  right: 22px;
  color: rgba(118, 75, 162, .14);
  font-size: 32px;
  font-weight: 800;
}
.role-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 22px;
  border-radius: 15px;
  font-size: 23px;
}
.role-card ul {
  display: grid;
  gap: 7px;
  margin: 18px 0 0;
  padding: 16px 0 0 17px;
  border-top: 1px solid #eeeef7;
  color: #667eea;
  font-size: 12px;
}
.role-card li::marker {
  color: #764ba2;
}
.intro-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 112px;
  padding: 38px 44px;
  border-radius: 26px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 20px 46px rgba(98, 78, 178, .22);
}
.intro-bottom .eyebrow,
.intro-bottom h2,
.intro-bottom p {
  color: #fff;
}
.intro-bottom h2 {
  margin-bottom: 7px;
  font-size: clamp(23px, 3vw, 32px);
}
.intro-bottom p {
  opacity: .82;
}
.back-login {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  padding: 12px 18px;
  border-radius: 999px;
  color: #59418c;
  background: #fff;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: transform .2s ease, box-shadow .2s ease;
}
.back-login:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(36, 25, 82, .2);
}

/* 系统功能卡片固定为两列排布（共 6 张，三行） */
.system-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }

/* ============================================================
 * 响应式
 * ============================================================ */
@media (max-width: 1100px) {
  .s-6, .s-7, .s-8 { display: none; }   /* 中屏隐藏新增的 3 条 */
  .deco-triangle, .deco-dots-2 { display: none; }
}
@media (max-width: 900px) {
  .system-page-layout,
  .technology-layout,
  .role-page-layout,
  .reverse-layout { grid-template-columns: 1fr; gap: 42px; }
  .system-page-layout .role-page-copy,
  .technology-copy,
  .role-page-copy { text-align: center; }
  .system-page-layout .role-page-copy > p,
  .technology-copy p,
  .role-page-copy > p { margin-left: auto; margin-right: auto; }
  .role-list { display: inline-grid; text-align: left; }
  .reverse-layout .role-page-copy { order: 1; }
  .reverse-layout .role-dashboard { order: 2; }
  .technology-panel { width: min(300px, 80vw); }
}
@media (max-width: 760px) {
  .feature-grid,
  .role-grid,
  .system-grid { grid-template-columns: 1fr; }
  .product-intro { min-height: auto; padding: 76px 20px 84px; }
  .intro-overview { min-height: 100vh; }
  .role-section,
  .intro-bottom { margin-top: 76px; }
  .intro-bottom { align-items: flex-start; flex-direction: column; padding: 30px 26px; }
  .process-line { flex-wrap: wrap; gap: 12px; }
  .page-next { bottom: 22px; }
}
@media (max-width: 560px) {
  .page-nav { right: 8px; gap: 8px; }
  .page-nav a span { display: none; }
  .system-grid { grid-template-columns: 1fr; }
  .process-line { font-size: 13px; }
  .role-card { padding: 22px; }
  .page-actions { flex-direction: column; gap: 16px; }
  .technology-panel { width: min(270px, 80vw); }
}
@media (max-width: 760px) {
  .learning-bot { right: 4%; bottom: 8%; transform: scale(.86); }
  .orbit-book { left: 4%; }
  .orbit-pencil { right: 4%; }
  .orbit-chart { left: 5%; bottom: 18%; }
}
@media (max-width: 560px) {
  .learning-floaters { opacity: .65; }
  .person-float { transform: scale(.78); }
  .teacher-float { top: 17%; right: 4%; }
  .student-float { bottom: 13%; left: 3%; }
  .learning-orbit { padding: 7px 9px; }
  .learning-orbit span { display: none; }
  .learning-bot { right: 7%; bottom: 9%; transform: scale(.68); }
  .top-brand { padding: 16px 20px; }
  .login-card { padding: 32px 24px 26px; border-radius: 14px; }
  .card-title { font-size: 20px; }
  .blob-3, .deco, .deco-wave { display: none; }
  .s-2, .s-3, .s-4, .s-5, .s-6, .s-7, .s-8 { display: none; }   /* 小屏只保留左上一条 */
  .slogan { font-size: 15px; padding: 10px 16px; }
  .feature-card,
  .role-card { padding: 22px; }
  .intro-heading h2 { font-size: 28px; }
}

/* 尊重「减少动态效果」偏好：关闭全部动效 */
@media (prefers-reduced-motion: reduce) {
  .blob, .login-card, .deco, .learning-orbit, .learning-bot, .person-float, .bot-head i, .tech-particle, .section-tech-grid, .product-intro::before, .product-intro::after { animation: none !important; }
}
</style>
