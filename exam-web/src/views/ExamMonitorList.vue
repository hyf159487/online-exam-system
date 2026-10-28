<template>
  <div class="exam-monitor">
    <!-- 选择考试 -->
    <el-card class="mb-4" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><Monitor /></el-icon>
          <span>选择正在进行的考试</span>
        </div>
      </template>
      <el-form :inline="true">
        <el-form-item label="进行中的考试">
          <el-select
            v-model="selectedExamId"
            placeholder="请选择考试"
            style="width: 360px"
            @change="onExamChange"
          >
            <el-option
              v-for="exam in runningExams"
              :key="exam.id"
              :label="`${exam.examName} - ${exam.courseName || '无课程'}`"
              :value="exam.id"
            >
              <div class="exam-option">
                <span class="exam-name">{{ exam.examName }}</span>
                <span class="exam-time">{{ formatTime(exam.startTime) }} ~ {{ formatTime(exam.endTime) }}</span>
                <el-tag size="small" type="success">{{ exam.activeCount || 0 }}/{{ exam.studentCount || 0 }} 进行中</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadExamList">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 考试信息摘要 -->
    <el-card v-if="currentExam" class="mb-4" shadow="never">
      <div class="exam-summary">
        <div class="summary-item">
          <div class="label">考试名称</div>
          <div class="value">{{ currentExam.examName }}</div>
        </div>
        <div class="summary-item">
          <div class="label">开始时间</div>
          <div class="value">{{ formatTime(currentExam.startTime) }}</div>
        </div>
        <div class="summary-item">
          <div class="label">结束时间</div>
          <div class="value">{{ formatTime(currentExam.endTime) }}</div>
        </div>
        <div class="summary-item">
          <div class="label">总参与人数</div>
          <div class="value">{{ currentExam.studentCount || 0 }}</div>
        </div>
        <div class="summary-item highlight">
          <div class="label">考试中</div>
          <div class="value">{{ currentExam.activeCount || 0 }}</div>
        </div>
      </div>
    </el-card>

    <!-- 筛选条件 -->
    <el-card v-if="selectedExamId" class="mb-4" shadow="never">
      <el-form :inline="true">
        <el-form-item label="班级">
          <el-select v-model="filterClassId" placeholder="全部班级" clearable style="width: 180px">
            <el-option
              v-for="cls in classOptions"
              :key="cls.classId"
              :label="cls.className"
              :value="cls.classId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="考试中" :value="1" />
            <el-option label="已交卷" :value="2" />
            <el-option label="强制交卷" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filterKeyword" placeholder="姓名/学号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchStudents">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
          <el-switch v-model="autoRefresh" active-text="自动刷新" inactive-text="" style="margin-left: 12px" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 学生监控表格 -->
    <el-card v-if="selectedExamId" shadow="never">
      <template #header>
        <div class="card-header">
          <span>学生监控列表</span>
          <el-tag v-if="autoRefresh" type="success" effect="dark" size="small">自动刷新中</el-tag>
        </div>
      </template>

      <el-table :data="studentList" v-loading="loading" stripe border :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="学号" prop="username" width="120" />
        <el-table-column label="姓名" prop="realName" width="100" />
        <el-table-column label="班级" prop="className" width="140" />
        <el-table-column label="考试状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="答题进度" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progressPercent"
              :color="progressColor(row.progressPercent)"
              :stroke-width="14"
              :text-inside="true"
            />
            <div class="progress-text">{{ row.answeredCount || 0 }}/{{ row.totalCount || 0 }} 题</div>
          </template>
        </el-table-column>
        <el-table-column label="剩余时间" width="120">
          <template #default="{ row }">
            <span :class="{ 'text-danger': tickOf(row) < 300 }">{{ formatTick(tickOf(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="切屏次数" prop="screenSwitchCount" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-warning': (row.screenSwitchCount || 0) >= 3 }">{{ row.screenSwitchCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进入考试时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.enterTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">查看详情</el-button>
            <el-button
              type="danger"
              link
              size="small"
              :disabled="row.status !== 1"
              @click="confirmForceSubmit(row)"
            >强制交卷</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="searchStudents"
          @current-change="searchStudents"
        />
      </div>
    </el-card>

    <!-- 答题详情对话框 -->
    <el-dialog v-model="detailVisible" :title="`${detailStudent?.realName} - 答题详情`" width="70%" top="5vh">
      <div v-if="detailLoading" class="text-center py-8">加载中...</div>
      <div v-else-if="detailList.length === 0" class="text-center py-8 text-gray-500">暂无答题记录</div>
      <el-table v-else :data="detailList" border stripe max-height="60vh">
        <el-table-column label="题号" type="index" width="60" />
        <el-table-column label="题型" width="80">
          <template #default="{ row }">{{ questionTypeText(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="题目内容" min-width="200">
          <template #default="{ row }">{{ row.content }}</template>
        </el-table-column>
        <el-table-column label="学生答案" width="150">
          <template #default="{ row }">
            <span v-if="row.answered">{{ row.userAnswer || '(空)' }}</span>
            <el-tag v-else type="info" size="small">未作答</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参考答案" width="150">
          <template #default="{ row }">{{ row.correctAnswer }}</template>
        </el-table-column>
        <el-table-column label="判定" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isCorrect === 1" type="success" size="small">正确</el-tag>
            <el-tag v-else-if="row.isCorrect === 0" type="danger" size="small">错误</el-tag>
            <el-tag v-else type="warning" size="small">待判</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Refresh } from '@element-plus/icons-vue'
import {
  getRunningExams,
  getStudentList,
  getAnswerDetail,
  forceSubmit as forceSubmitApi
} from '@/api/examMonitor'

// 数据
const runningExams = ref([])
const selectedExamId = ref(null)
const currentExam = ref(null)
const studentList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const autoRefresh = ref(true)  // 默认打开实时监控：每 5 秒自动刷新学生列表与顶部总览卡片

// 筛选
const filterClassId = ref(null)
const filterStatus = ref(null)
const filterKeyword = ref('')
const classOptions = computed(() => {
  const seen = new Map()
  studentList.value.forEach(s => {
    if (s.classId && !seen.has(s.classId)) {
      seen.set(s.classId, { classId: s.classId, className: s.className })
    }
  })
  return Array.from(seen.values())
})

// 答题详情
const detailVisible = ref(false)
const detailStudent = ref(null)
const detailList = ref([])
const detailLoading = ref(false)

let refreshTimer = null
let fastTimer = null   // 学生列表 1 秒静默轮询
let slowTimer = null   // 顶部总览 5 秒轮询

// ===== 剩余时间本地每秒跳动 =====
// 后端 remainingTime 是快照值（学生 10 秒心跳才同步一次），前端在两次刷新之间本地递减，
// 让管理员看到的剩余时间每秒实时跳动；5 秒轮询到新数据时重新校准。
let tickTimer = null

function tickOf(row) {
  // 已交卷/强制交卷的行固定显示 0，不再递减
  if (row.status != null && row.status >= 2) return 0
  if (row._tick == null) row._tick = row.remainingTime || 0
  return row._tick
}

function formatTick(sec) {
  if (sec == null || sec <= 0) return '00:00:00'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return [h, m, s].map(x => String(x).padStart(2, '0')).join(':')
}

function startTickTimer() {
  stopTickTimer()
  tickTimer = setInterval(() => {
    studentList.value.forEach(row => {
      if (row.status != null && row.status >= 2) return
      if (row._tick == null) row._tick = row.remainingTime || 0
      if (row._tick > 0) row._tick--
    })
  }, 1000)
}

function stopTickTimer() {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}

// 每行上一轮的后端 remainingTime 快照（rowId -> remainingTime），用于判断心跳是否更新过
const serverRemainingMap = new Map()

// ============ 方法 ============

async function loadRunningExams() {
  try {
    const res = await getRunningExams()
    runningExams.value = res.data || []
    if (runningExams.value.length > 0 && !selectedExamId.value) {
      selectedExamId.value = runningExams.value[0].id
      onExamChange(selectedExamId.value)
    }
  } catch (e) {
    console.error('加载考试列表失败', e)
  }
}

function onExamChange(examId) {
  currentExam.value = runningExams.value.find(e => e.id === examId)
  pageNum.value = 1
  searchStudents()
}

async function searchStudents(silent = false) {
  if (!selectedExamId.value) return
  if (!silent) loading.value = true   // 静默轮询不显示 loading，避免表格每秒闪烁
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (filterClassId.value) params.classId = filterClassId.value
    if (filterStatus.value != null) params.status = filterStatus.value
    if (filterKeyword.value) params.keyword = filterKeyword.value

    const res = await getStudentList(selectedExamId.value, params)
    const data = res.data || {}
    // 校准本地倒计时：后端值变化（学生心跳到了）→ 用后端值校准；后端值没变 → 沿用本地递减，避免跳动被冻结
    const prevTicks = new Map(studentList.value.map(r => [r.id, r._tick]))
    studentList.value = data.records || []
    studentList.value.forEach(r => {
      const last = serverRemainingMap.get(r.id)
      const prev = prevTicks.get(r.id)
      if (last === undefined || r.remainingTime !== last) {
        r._tick = r.remainingTime || 0
      } else {
        r._tick = prev != null ? prev : (r.remainingTime || 0)
      }
      serverRemainingMap.set(r.id, r.remainingTime)
    })
    total.value = data.total || 0
    // 新数据到位 → 本地每秒倒计时重新校准并开跑
    startTickTimer()
  } catch (e) {
    console.error('加载学生列表失败', e)
  } finally {
    if (!silent) loading.value = false
  }
}

function resetFilter() {
  filterClassId.value = null
  filterStatus.value = null
  filterKeyword.value = ''
  pageNum.value = 1
  searchStudents()
}

async function loadExamList() {
  await loadRunningExams()
  if (selectedExamId.value) {
    searchStudents()
  }
}

async function viewDetail(row) {
  detailStudent.value = row
  detailVisible.value = true
  detailLoading.value = true
  detailList.value = []
  try {
    const res = await getAnswerDetail(row.examId, row.studentId)
    detailList.value = res.data || []
  } catch (e) {
    console.error('加载答题详情失败', e)
  } finally {
    detailLoading.value = false
  }
}

async function confirmForceSubmit(row) {
  try {
    await ElMessageBox.confirm(
      `确定要强制交卷吗？学生「${row.realName}」的当前作答将被保存并提交，状态标记为"强制交卷"。此操作不可撤销。`,
      '强制交卷确认',
      { confirmButtonText: '确定强制交卷', cancelButtonText: '取消', type: 'warning' }
    )
    await doForceSubmit(row)
  } catch (e) {
    // 用户取消
  }
}

async function doForceSubmit(row) {
  try {
    const res = await forceSubmitApi(row.examId, row.studentId)
    if (res.code === 200) {
      ElMessage.success('强制交卷成功')
      searchStudents()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('强制交卷失败：' + (e.message || '未知错误'))
  }
}

// ============ 辅助方法 ============

function statusText(status) {
  const map = { 0: '未开始', 1: '考试中', 2: '已交卷', 3: '强制交卷' }
  return map[status] || '未知'
}

function statusTagType(status) {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return map[status] || 'info'
}

function progressColor(percent) {
  if (percent >= 80) return '#67C23A'
  if (percent >= 50) return '#409EFF'
  if (percent >= 20) return '#E6A23C'
  return '#F56C6C'
}

function questionTypeText(type) {
  const map = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }
  return map[type] || '未知'
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

function formatDateTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

// ============ 生命周期 ============

let pollingInFlight = false

watch(autoRefresh, (val) => {
  stopAutoRefreshTimers()
  if (val) {
    // 学生列表 1 秒静默轮询：学生切屏/答题/倒计时近实时同步（不显示 loading 防闪烁）
    fastTimer = setInterval(async () => {
      if (pollingInFlight || !selectedExamId.value) return  // 上一请求未返回则跳过本轮，防堆积
      pollingInFlight = true
      try {
        await searchStudents(true)
      } finally {
        pollingInFlight = false
      }
    }, 1000)
    // 顶部总览卡片（总参与/考试中）5 秒刷新一次即可
    slowTimer = setInterval(() => {
      loadRunningExams()
    }, 5000)
  }
}, { immediate: true })  // 默认 autoRefresh=true，进页面立即启动轮询

function stopAutoRefreshTimers() {
  if (fastTimer) {
    clearInterval(fastTimer)
    fastTimer = null
  }
  if (slowTimer) {
    clearInterval(slowTimer)
    slowTimer = null
  }
}

onMounted(() => {
  loadRunningExams()
})

onBeforeUnmount(() => {
  stopAutoRefreshTimers()
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  stopTickTimer()
})
</script>

<style scoped>
.exam-monitor {
  padding: 0;
}

.mb-4 {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.exam-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px 0;
}

.exam-option .exam-name {
  font-weight: 500;
}

.exam-option .exam-time {
  font-size: 12px;
  color: #909399;
}

.exam-summary {
  display: flex;
  gap: 32px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-item .label {
  font-size: 12px;
  color: #909399;
}

.summary-item .value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.summary-item.highlight .value {
  color: #E6A23C;
}

.progress-text {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.text-danger {
  color: #F56C6C;
  font-weight: 600;
}

.text-warning {
  color: #E6A23C;
  font-weight: 600;
}

.text-center {
  text-align: center;
}

.py-8 {
  padding: 32px 0;
}

.text-gray-500 {
  color: #909399;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
