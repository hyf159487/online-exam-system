<template>
  <div class="exam-do" v-loading="loading">
    <template v-if="exam">
      <!-- 顶部考试信息 + 倒计时 -->
      <el-card shadow="never" class="top-bar">
        <div class="top-info">
          <div>
            <h2 class="exam-name">{{ exam.examName }}</h2>
            <span class="sub">试卷：{{ exam.paperName }} · 共 {{ questions.length }} 题 · 满分 {{ exam.totalScore }} 分</span>
          </div>
          <div class="timer" :class="{ danger: remaining <= 60 }">
            <el-icon><Timer /></el-icon>
            <span>剩余 {{ formatTime(remaining) }}</span>
          </div>
        </div>
        <!-- 防作弊提示条 -->
        <div v-if="antiCheatEnabled" class="anti-cheat-bar">
          <el-icon><Warning /></el-icon>
          <span>防作弊已开启：切屏超过 {{ maxTabSwitches }} 次将自动交卷。当前切屏次数：<b>{{ tabSwitchCount }}</b></span>
          <span class="inactivity-tip" v-if="examTimeoutMinutes">
            ｜ 超时自动交卷：{{ examTimeoutMinutes }} 分钟无操作将自动提交
          </span>
        </div>
        <div v-else-if="examTimeoutMinutes" class="anti-cheat-bar soft">
          <el-icon><InfoFilled /></el-icon>
          <span>提示：{{ examTimeoutMinutes }} 分钟无操作将自动提交试卷</span>
        </div>
      </el-card>

      <!-- 题目列表 -->
      <el-card
        v-for="(q, i) in questions"
        :key="q.questionId"
        shadow="never"
        class="question-card"
      >
        <div class="q-head">
          <span class="q-index">{{ i + 1 }}.</span>
          <span class="q-type" :class="typeClass(q.questionType)">{{ typeText(q.questionType) }}</span>
          <span class="q-score">{{ q.score }} 分</span>
        </div>
        <p class="q-content">{{ q.content }}</p>

        <!-- 单选题 -->
        <el-radio-group v-if="q.questionType === 1" v-model="answers[q.questionId]" class="option-group">
          <div v-for="(opt, idx) in parseOptions(q.options)" :key="idx" class="option-line">
            <el-radio :value="letter(idx)">{{ letter(idx) }}. {{ opt }}</el-radio>
          </div>
        </el-radio-group>

        <!-- 多选题 -->
        <el-checkbox-group v-else-if="q.questionType === 2" v-model="answers[q.questionId]" class="option-group">
          <div v-for="(opt, idx) in parseOptions(q.options)" :key="idx" class="option-line">
            <el-checkbox :value="letter(idx)">{{ letter(idx) }}. {{ opt }}</el-checkbox>
          </div>
        </el-checkbox-group>

        <!-- 判断题 -->
        <el-radio-group v-else-if="q.questionType === 3" v-model="answers[q.questionId]">
          <el-radio value="T">正确（T）</el-radio>
          <el-radio value="F">错误（F）</el-radio>
        </el-radio-group>

        <!-- 填空题 -->
        <el-input
          v-else-if="q.questionType === 4"
          v-model="answers[q.questionId]"
          placeholder="请输入答案"
          style="max-width: 480px"
        />

        <!-- 简答题 / 名词解释 -->
        <el-input
          v-else-if="q.questionType === 5 || q.questionType === 6"
          v-model="answers[q.questionId]"
          type="textarea"
          :rows="3"
          placeholder="请输入你的作答"
        />
      </el-card>

      <!-- 底部交卷 -->
      <div class="footer-bar">
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          交卷
        </el-button>
      </div>
    </template>

    <el-empty v-else-if="!loading" :description="loadError || '未获取到考试信息'" />

    <!-- 交卷结果弹窗 -->
    <el-dialog
      v-model="resultVisible"
      title="交卷成功"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="result">
        <div class="result-score">
          <span class="num">{{ result.totalScore }}</span>
          <span class="den">/ {{ result.paperTotalScore }} 分</span>
        </div>
        <el-tag :type="result.isPass === 1 ? 'success' : 'danger'" effect="light" size="large">
          {{ result.isPass === 1 ? '及格' : '不及格' }}
        </el-tag>
        <p v-if="result.subjectiveCount > 0" class="result-tip">
          本次考试含 {{ result.subjectiveCount }} 道主观题，当前为客观题自动判分成绩，主观题得分待教师阅卷后更新。
        </p>
        <p v-else class="result-tip">成绩已即时生成。</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="goGrades">查看我的成绩</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, InfoFilled } from '@element-plus/icons-vue'
import { getExamQuestions, submitExam, saveExamProgress, heartbeatExamProgress } from '@/api/studentExam'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const exam = ref(null)
const questions = ref([])
const answers = reactive({})

// 倒计时
const remaining = ref(0)
let timer = null
const submitting = ref(false)
const resultVisible = ref(false)
const result = reactive({ totalScore: 0, paperTotalScore: 0, isPass: 0, subjectiveCount: 0 })

// ===== 系统配置（从后端下发）=====
const examTimeoutMinutes = ref(60)
const antiCheatEnabled = ref(true)
const maxTabSwitches = 5

// ===== 防作弊：切屏检测 =====
const tabSwitchCount = ref(0)

// ===== 实时监控心跳（每 10 秒同步一次剩余时间 + 切屏次数）=====
let heartbeatTimer = null

/** 发一次心跳：同步剩余时间/切屏次数到后端（管理员端监控实时可见） */
function fireHeartbeat() {
  if (submitting.value || resultVisible.value || loading.value) return
  if (!examId) return
  heartbeatExamProgress(examId, remaining.value, tabSwitchCount.value).catch(() => {})
}

/** 启动/重启心跳定时器（每 10 秒） */
function startHeartbeatTimer() {
  stopHeartbeatTimer()
  heartbeatTimer = setInterval(() => {
    fireHeartbeat()
  }, 10000)
}
function stopHeartbeatTimer() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

// ===== 超时自动交卷：无操作检测 =====
let inactivityTimer = null
let lastActivityTime = Date.now()

const examId = route.params.id

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('visibilitychange', onVisibilityChange)
  // 用户活动事件：鼠标/键盘/触摸 → 重置无操作计时器
  const activityEvents = ['mousemove', 'keydown', 'click', 'touchstart', 'scroll']
  activityEvents.forEach(evt => {
    window.addEventListener(evt, markActivity)
  })
  loadExam()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (progressTimer) clearTimeout(progressTimer)
  if (inactivityTimer) clearTimeout(inactivityTimer)
  stopHeartbeatTimer()
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  const activityEvents = ['mousemove', 'keydown', 'click', 'touchstart', 'scroll']
  activityEvents.forEach(evt => {
    window.removeEventListener(evt, markActivity)
  })
})

/** 标记用户有活动，重置无操作计时器 */
function markActivity() {
  lastActivityTime = Date.now()
}

/** 切屏 / 标签页切换检测 */
function onVisibilityChange() {
  if (document.hidden && antiCheatEnabled.value && !submitting.value) {
    tabSwitchCount.value++
    // 切屏后立即发一次心跳：管理员端监控"切屏次数"实时+1，不用等下一次 10s 轮询
    fireHeartbeat()
    if (tabSwitchCount.value >= maxTabSwitches) {
      autoSubmit(`防作弊检测：切屏 ${maxTabSwitches} 次，系统自动交卷`)
    } else {
      ElMessage.warning({
        message: `检测到切屏行为！（${tabSwitchCount.value}/${maxTabSwitches}），${maxTabSwitches - tabSwitchCount.value} 次后将自动交卷`,
        duration: 4000
      })
    }
  }
}

/** 启动无操作计时器：examTimeoutMinutes 分钟无活动自动交卷 */
function startInactivityTimer() {
  if (inactivityTimer) clearTimeout(inactivityTimer)
  const timeoutMs = examTimeoutMinutes.value * 60 * 1000
  inactivityTimer = setTimeout(() => {
    if (submitting.value || resultVisible.value) return
    const elapsed = Date.now() - lastActivityTime
    if (elapsed >= timeoutMs) {
      autoSubmit(`${examTimeoutMinutes.value} 分钟无操作，系统已自动交卷`)
    } else {
      // 用户在倒计时内有活动，重启计时器
      startInactivityTimer()
    }
  }, 5000)  // 每 5 秒检查一次
}

async function loadExam() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getExamQuestions(examId)
    exam.value = res.data.exam
    questions.value = res.data.questions || []
    // 读取后端下发的系统配置
    if (res.data.examTimeoutMinutes != null) {
      examTimeoutMinutes.value = res.data.examTimeoutMinutes
    }
    if (res.data.antiCheatEnabled != null) {
      antiCheatEnabled.value = res.data.antiCheatEnabled
    }
    // 恢复已累计切屏次数（刷新页面后不归零，从 exam_student 记录回读）
    if (res.data.screenSwitchCount != null && res.data.screenSwitchCount > tabSwitchCount.value) {
      tabSwitchCount.value = res.data.screenSwitchCount
    }
    // 初始化答案容器
    questions.value.forEach((q) => {
      answers[q.questionId] = q.questionType === 2 ? [] : ''
    })
    // 恢复已保存的答题进度
    ;(res.data.answers || []).forEach((item) => {
      const q = questions.value.find((x) => x.questionId === item.questionId)
      if (!q) return
      if (q.questionType === 2) {
        answers[q.questionId] = String(item.answer || '').split(/[,，]/).filter(Boolean)
      } else {
        answers[q.questionId] = item.answer || ''
      }
    })
    // 倒计时
    const deadline = res.data.deadline
    const duration = res.data.duration || exam.value.duration || 60
    if (deadline) {
      remaining.value = Math.max(0, Math.ceil((new Date(deadline).getTime() - Date.now()) / 1000))
    } else {
      remaining.value = duration * 60
    }
    startTimer()
    markActivity()
    startInactivityTimer()
    startHeartbeatTimer()  // 开考即启用心跳：实时同步剩余时间/切屏次数给管理员端
    fireHeartbeat()       // 先打一次，让监控立刻看到学生（而不是等头 10 秒）
  } catch (err) {
    loadError.value = err?.message || '加载失败'
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

// 刷新 / 关闭页面拦截
function onBeforeUnload(e) {
  if (submitting.value || resultVisible.value) return
  fireProgressSave()
  e.preventDefault()
  e.returnValue = ''
}

function fireProgressSave() {
  try {
    const token = sessionStorage.getItem('token')
    if (!token) return
    fetch(`/api/student/exams/${examId}/progress`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        answers: collectAnswers(),
        remainingTime: remaining.value,
        screenSwitchCount: tabSwitchCount.value,
      }),
      keepalive: true,
    }).catch(() => {})
  } catch (err) { /* 忽略 */ }
}

function startTimer() {
  timer = setInterval(() => {
    remaining.value--
    if (remaining.value <= 0) {
      remaining.value = 0
      clearInterval(timer)
      autoSubmit('考试时间已到，系统已自动交卷')
    }
  }, 1000)
}

function formatTime(sec) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function parseOptions(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch (e) { return [] }
}

function letter(idx) {
  return String.fromCharCode(65 + idx)
}

function typeText(t) {
  return { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }[t] || '未知'
}
function typeClass(t) {
  return { 1: 't-single', 2: 't-multiple', 3: 't-judge', 4: 't-fill', 5: 't-essay' }[t] || ''
}

function collectAnswers() {
  return questions.value.map((q) => {
    let ans = answers[q.questionId]
    if (q.questionType === 2) {
      ans = Array.isArray(ans) ? ans.join(',') : ''
    } else {
      ans = ans || ''
    }
    return { questionId: q.questionId, answer: ans }
  })
}

// ===== 答题进度自动保存（防抖 3 秒）=====
let progressTimer = null

function scheduleProgressSave() {
  if (progressTimer) clearTimeout(progressTimer)
  progressTimer = setTimeout(() => {
    progressTimer = null
    if (submitting.value || resultVisible.value) return
    saveExamProgress(examId, collectAnswers(), {
      remainingTime: remaining.value,
      screenSwitchCount: tabSwitchCount.value,
    }).catch(() => {})
  }, 3000)
}

watch(answers, () => {
  if (loading.value || resultVisible.value) return
  scheduleProgressSave()
  markActivity()
}, { deep: true })

async function handleSubmit() {
  if (submitting.value) return
  try {
    await ElMessageBox.confirm(
      '确认提交试卷吗？交卷后不可再修改答案。',
      '交卷确认',
      { confirmButtonText: '确认交卷', cancelButtonText: '再检查一下', type: 'warning' }
    )
  } catch (e) { return }
  submitting.value = true
  stopAllTimers()
  try {
    const res = await submitExam(examId, collectAnswers())
    Object.assign(result, res.data)
    resultVisible.value = true
  } catch (err) {
    ElMessage.error(err?.message || '交卷失败')
  } finally {
    submitting.value = false
  }
}

async function autoSubmit(msg) {
  if (submitting.value) return
  submitting.value = true
  stopAllTimers()
  ElMessage.warning(msg)
  try {
    const res = await submitExam(examId, collectAnswers())
    Object.assign(result, res.data)
    resultVisible.value = true
  } catch (err) {
    ElMessage.error(err?.message || '自动交卷失败')
  } finally {
    submitting.value = false
  }
}

function stopAllTimers() {
  if (timer) { clearInterval(timer); timer = null }
  if (inactivityTimer) { clearTimeout(inactivityTimer); inactivityTimer = null }
  if (progressTimer) { clearTimeout(progressTimer); progressTimer = null }
}

function goGrades() {
  resultVisible.value = false
  router.push('/student/grades')
}
function goBack() {
  router.push('/student/exams')
}
</script>

<style scoped>
.top-bar {
  margin-bottom: 16px;
}
.top-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.exam-name {
  margin: 0 0 4px;
  font-size: 18px;
  color: #303133;
}
.sub {
  font-size: 13px;
  color: #909399;
}
.timer {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: bold;
  color: #409eff;
}
.timer.danger {
  color: #f56c6c;
}
.anti-cheat-bar {
  margin-top: 12px;
  padding: 10px 14px;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 6px;
  color: #f56c6c;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.anti-cheat-bar .el-icon {
  font-size: 16px;
}
.anti-cheat-bar b {
  color: #f56c6c;
  font-weight: bold;
}
.anti-cheat-bar .inactivity-tip {
  margin-left: auto;
  color: #e6a23c;
}
.anti-cheat-bar.soft {
  background: #fdf6ec;
  border-color: #f5dab1;
  color: #e6a23c;
}
.anti-cheat-bar.soft .el-icon {
  color: #e6a23c;
}
.question-card {
  margin-bottom: 14px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.q-index {
  font-weight: bold;
  font-size: 16px;
  color: #303133;
}
.q-type {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #ecf5ff;
  color: #409eff;
}
.q-type.t-multiple { background: #f0f9eb; color: #67c23a; }
.q-type.t-judge { background: #fdf6ec; color: #e6a23c; }
.q-type.t-fill { background: #f4f4f5; color: #909399; }
.q-type.t-essay { background: #f9ecff; color: #a855f7; }
.q-score {
  font-size: 13px;
  color: #909399;
}
.q-content {
  margin: 4px 0 12px;
  line-height: 1.7;
  color: #303133;
}
/* 选项组：纵向排列，保证一个选项独占一行 */
.option-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: 100%;
  gap: 4px;
}
.option-group :deep(.el-radio),
.option-group :deep(.el-checkbox) {
  display: flex;
  width: 100%;
  height: auto;
  min-height: 34px;
  margin-right: 0;
  padding: 6px 10px;
  border-radius: 6px;
  align-items: flex-start;
  white-space: normal;
  font-size: 15px;
  transition: background-color 0.2s;
  box-sizing: border-box;
}
.option-group :deep(.el-radio:hover),
.option-group :deep(.el-checkbox:hover) {
  background: #f5f7fa;
}
.option-group :deep(.el-radio.is-checked),
.option-group :deep(.el-checkbox.is-checked) {
  background: #ecf5ff;
}
.option-group :deep(.el-radio__input),
.option-group :deep(.el-checkbox__input) {
  margin-top: 3px;
  flex-shrink: 0;
}
.option-group :deep(.el-radio__label),
.option-group :deep(.el-checkbox__label) {
  white-space: normal;
  word-break: break-word;
  line-height: 1.6;
}
.option-line {
  width: 100%;
  flex: 0 0 auto;
}
.footer-bar {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin: 20px 0;
}
.result {
  text-align: center;
}
.result-score {
  margin-bottom: 12px;
}
.result-score .num {
  font-size: 40px;
  font-weight: bold;
  color: #409eff;
}
.result-score .den {
  font-size: 16px;
  color: #909399;
  margin-left: 6px;
}
.result-tip {
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
}
</style>
