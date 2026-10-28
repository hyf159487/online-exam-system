<template>
  <div class="stats-question">
    <!-- 筛选条件 -->
    <el-card class="mb-4" shadow="never">
      <el-form :inline="true">
        <el-form-item label="考试">
          <el-select
            v-model="filterExamId"
            placeholder="请选择已结束的考试"
            clearable
            style="width: 320px"
            @change="onExamChange"
          >
            <el-option
              v-for="exam in examOptions"
              :key="exam.id"
              :label="exam.examName + ' - ' + (exam.courseName || '')"
              :value="exam.id"
            >
              <div class="exam-option">
                <span>{{ exam.examName }}</span>
                <span class="exam-sub">
                  {{ exam.courseName || '' }} · 已批阅 {{ exam.completedCount || 0 }} 人
                </span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!filterExamId" @click="loadData">
            查询分析
          </el-button>
          <el-button @click="resetFilter">重置</el-button>
          <el-button
            type="success"
            :disabled="!questionList.length"
            @click="exportData"
          >
            <el-icon><Download /></el-icon>导出数据
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 难度系数图表 -->
    <el-card v-if="questionList.length" shadow="never" class="mb-4">
      <template #header>
        <div class="card-header">
          <span>试题难度系数分布</span>
          <el-tag type="info" size="small">P = 平均分 / 满分，0.4-0.7 为理想难度</el-tag>
        </div>
      </template>
      <div ref="difficultyChartRef" class="chart-container"></div>
    </el-card>

    <!-- 试题质量列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>试题质量分析列表</span>
          <span class="sub-text">共 {{ questionList.length }} 道题</span>
        </div>
      </template>

      <el-table v-if="questionList.length" :data="pagedQuestionList" stripe border v-loading="loading">
        <!-- 序号跨页连续：用 indexMethod 计算真实序号，避免每页都从 1 开始 -->
        <el-table-column label="序号" type="index" :index="indexMethod" width="70" align="center" />
        <el-table-column label="题干摘要" min-width="250" show-overflow-tooltip>
          <template #default="{ row, $index }">
            <span class="question-text">{{ indexMethod($index) }}. {{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="题型" prop="questionTypeName" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagColor(row.questionType)" size="small">{{ row.questionTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="满分" prop="score" width="80" align="center" />
        <el-table-column label="作答人数" prop="totalCount" width="90" align="center" />
        <el-table-column label="答对" prop="correctCount" width="70" align="center">
          <template #default="{ row }">
            <span class="correct">{{ row.correctCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="答错" prop="wrongCount" width="70" align="center">
          <template #default="{ row }">
            <span class="wrong">{{ row.wrongCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="正确率" width="100" align="center">
          <template #default="{ row }">
            <span :class="rateClass(row.correctRate)">{{ row.correctRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="错误率" width="100" align="center">
          <template #default="{ row }">
            <span :class="rateClass(row.wrongRate)">{{ row.wrongRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="难度系数 P" width="100" align="center">
          <template #default="{ row }">
            <span :class="difficultyClass(row.difficulty)">{{ row.difficulty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="难度等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="difficultyTagType(row.difficultyLevel)" size="small">
              {{ row.difficultyLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewQuestion(row)">
              原题
            </el-button>
            <el-button type="success" link size="small" @click="viewDetails(row)">
              答题明细
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else-if="!loading" description="请选择已结束的考试查看试题质量分析" />

      <!-- 分页：题目数据一次性返回，按前端分页展示，默认每页 10 条 -->
      <div v-if="questionList.length" class="pager-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="questionList.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 原题弹窗 -->
    <el-dialog v-model="showQuestionDialog" title="试题详情" width="650px" destroy-on-close>
      <div v-if="currentQuestion" class="question-detail">
        <div class="detail-row">
          <span class="label">题型：</span>
          <el-tag>{{ currentQuestion.questionTypeName }}</el-tag>
          <span class="ml-4 label">满分：</span>
          <span>{{ currentQuestion.score }}</span>
        </div>
        <div class="detail-content">
          <div class="content-label">题干</div>
          <div class="content-text">{{ currentQuestion.content }}</div>
        </div>
        <div v-if="currentQuestion.options" class="detail-content">
          <div class="content-label">选项</div>
          <div class="content-text" v-html="formatOptions(currentQuestion.options)"></div>
        </div>
        <div class="detail-row">
          <span class="label">参考答案：</span>
          <span class="answer">{{ currentQuestion.answer }}</span>
        </div>
        <div v-if="currentQuestion.analysis" class="detail-content">
          <div class="content-label">解析</div>
          <div class="content-text">{{ currentQuestion.analysis }}</div>
        </div>
        <el-divider />
        <div class="stats-row">
          <div class="stat-item">
            <div class="stat-num">{{ currentQuestion.totalCount }}</div>
            <div class="stat-label">作答人数</div>
          </div>
          <div class="stat-item stat-green">
            <div class="stat-num">{{ currentQuestion.correctCount }}</div>
            <div class="stat-label">答对人数</div>
          </div>
          <div class="stat-item stat-red">
            <div class="stat-num">{{ currentQuestion.wrongCount }}</div>
            <div class="stat-label">答错人数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ currentQuestion.correctRate }}%</div>
            <div class="stat-label">正确率</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ currentQuestion.difficulty }}</div>
            <div class="stat-label">难度系数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ currentQuestion.difficultyLevel }}</div>
            <div class="stat-label">难度等级</div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 答题明细弹窗 -->
    <el-dialog v-model="showDetailDialog" title="学生答题明细" width="700px" destroy-on-close>
      <div v-if="detailLoading" class="loading-wrap">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
      <div v-else-if="detailList.length">
        <el-table :data="detailList" stripe max-height="400" border>
          <el-table-column label="学号" prop="username" width="120" />
          <el-table-column label="姓名" prop="realName" width="100" />
          <el-table-column label="学生作答" prop="userAnswer" min-width="150" show-overflow-tooltip />
          <el-table-column label="得分" prop="getScore" width="80" align="center" />
          <el-table-column label="正误" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isCorrect === 1 ? 'success' : 'danger'" size="small">
                {{ row.isCorrect === 1 ? '正确' : '错误' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="detail-summary">
          共 {{ detailList.length }} 人作答，
          <span class="correct">{{ detailList.filter(d => d.isCorrect === 1).length }}</span> 人正确，
          <span class="wrong">{{ detailList.filter(d => d.isCorrect !== 1).length }}</span> 人错误
        </div>
      </div>
      <el-empty v-else description="暂无答题明细数据" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getExamList, getQuestionData, getAnswerDetail } from '@/api/statsQuestion'

const loading = ref(false)
const detailLoading = ref(false)
const examOptions = ref([])
const filterExamId = ref(null)
const questionList = ref([])
const currentQuestion = ref(null)
const detailList = ref([])
const showQuestionDialog = ref(false)
const showDetailDialog = ref(false)

const difficultyChartRef = ref(null)
let difficultyChart = null

/* ===== 列表分页（前端分页：题目数据一次性返回后按页切片）===== */
const page = ref(1)
const pageSize = ref(10)

const pagedQuestionList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return questionList.value.slice(start, start + pageSize.value)
})

/** 序号 = 页码偏移 + 当前页行号，保证跨页连续（1、2、…、11、12…） */
function indexMethod(indexInPage) {
  return (page.value - 1) * pageSize.value + indexInPage + 1
}

function handleSizeChange() {
  page.value = 1
}

/* 数据条数变化后页码可能越界（如从 50 条/页切回小页），自动回到有效页 */
watch(() => questionList.value.length, (total) => {
  if (!total) {
    page.value = 1
    return
  }
  const maxPage = Math.max(1, Math.ceil(total / pageSize.value))
  if (page.value > maxPage) page.value = maxPage
})

function loadExams() {
  getExamList().then(res => {
    examOptions.value = res.data || []
  }).catch(err => {
    ElMessage.error(err?.message || '加载考试列表失败')
  })
}

function onExamChange() {
  questionList.value = []
  page.value = 1
  if (difficultyChart) {
    difficultyChart.dispose()
    difficultyChart = null
  }
}

function loadData() {
  if (!filterExamId.value) return
  loading.value = true
  getQuestionData(filterExamId.value).then(res => {
    questionList.value = res.data || []
    // 重新查询后回到第一页，避免停留在旧页码
    page.value = 1
    nextTick(() => {
      renderDifficultyChart()
    })
  }).catch(err => {
    ElMessage.error(err?.message || '加载试题质量数据失败')
  }).finally(() => {
    loading.value = false
  })
}

function resetFilter() {
  filterExamId.value = null
  questionList.value = []
  page.value = 1
  if (difficultyChart) {
    difficultyChart.dispose()
    difficultyChart = null
  }
}

function renderDifficultyChart() {
  if (!difficultyChartRef.value || !questionList.value.length) return
  if (difficultyChart) difficultyChart.dispose()
  difficultyChart = echarts.init(difficultyChartRef.value)

  const data = questionList.value.map((q, i) => ({
    index: i + 1,
    value: q.difficulty || 0,
    level: q.difficultyLevel
  }))

  difficultyChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const d = data[params[0].dataIndex]
        const q = questionList.value[d.index - 1]
        return `<div style="font-weight:600">第 ${d.index} 题</div>
                <div style="max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${q.content}</div>
                <div>难度系数: <b>${d.value}</b></div>
                <div>难度等级: ${d.level}</div>`
      }
    },
    grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: data.map(d => d.index + '题'),
      name: '题号',
      axisLabel: { rotate: 45 }
    },
    yAxis: { type: 'value', name: '难度系数 P', min: 0, max: 1 },
    series: [{
      type: 'bar',
      data: data.map(d => ({
        value: d.value,
        itemStyle: { color: difficultyColor(d.value) }
      })),
      barWidth: '60%',
      label: { show: true, position: 'top', formatter: '{c}' },
      markArea: {
        itemStyle: { opacity: 0.15 },
        data: [[
          { yAxis: 0.4, itemStyle: { color: '#67c23a' } },
          { yAxis: 0.7 }
        ]]
      }
    }]
  })
}

function difficultyColor(p) {
  if (!p || p <= 0) return '#c0c4cc'
  if (p < 0.2) return '#f56c6c'
  if (p < 0.4) return '#e6a23c'
  if (p < 0.7) return '#67c23a'
  if (p < 0.9) return '#409EFF'
  return '#909399'
}

function typeTagColor(type) {
  switch (type) {
    case 1: return ''
    case 2: return 'warning'
    case 3: return 'success'
    case 4: return 'info'
    case 5: return 'danger'
    default: return ''
  }
}

function rateClass(rate) {
  if (!rate) return ''
  if (rate >= 80) return 'rate-high'
  if (rate >= 50) return 'rate-mid'
  return 'rate-low'
}

function difficultyClass(d) {
  if (d == null) return ''
  if (d < 0.4) return 'diff-hard'
  if (d <= 0.7) return 'diff-mid'
  return 'diff-easy'
}

function difficultyTagType(level) {
  switch (level) {
    case '过难': return 'danger'
    case '偏难': return 'warning'
    case '适中': return 'success'
    case '偏易': return ''
    case '过易': return 'info'
    default: return 'info'
  }
}

function viewQuestion(row) {
  currentQuestion.value = row
  showQuestionDialog.value = true
}

function viewDetails(row) {
  showDetailDialog.value = true
  detailList.value = []
  detailLoading.value = true
  getAnswerDetail(filterExamId.value, row.questionId).then(res => {
    detailList.value = res.data?.answerDetails || []
  }).catch(err => {
    ElMessage.error(err?.message || '加载答题明细失败')
  }).finally(() => {
    detailLoading.value = false
  })
}

function formatOptions(optionsStr) {
  if (!optionsStr) return ''
  try {
    const arr = JSON.parse(optionsStr)
    return arr.map((opt, i) => {
      const letter = String.fromCharCode(65 + i)
      return `<div>${letter}. ${opt}</div>`
    }).join('')
  } catch {
    return optionsStr
  }
}

function exportData() {
  if (!questionList.value.length) return
  let csv = '\uFEFF'
  csv += '试题质量分析报告\n'
  csv += '考试ID,' + filterExamId.value + '\n\n'
  csv += '序号,题干,题型,满分,作答人数,答对,答错,正确率,错误率,难度系数,难度等级\n'
  questionList.value.forEach((q, i) => {
    const line = [
      i + 1,
      '"' + (q.content || '').replace(/"/g, '""') + '"',
      q.questionTypeName,
      q.score,
      q.totalCount,
      q.correctCount,
      q.wrongCount,
      q.correctRate + '%',
      q.wrongRate + '%',
      q.difficulty,
      q.difficultyLevel
    ].join(',')
    csv += line + '\n'
  })

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `试题质量分析_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

function handleResize() {
  if (difficultyChart) difficultyChart.resize()
}

onMounted(() => {
  loadExams()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (difficultyChart) difficultyChart.dispose()
})
</script>

<style scoped>
.stats-question {
  padding: 4px;
}

.mb-4 {
  margin-bottom: 16px;
}

.chart-container {
  height: 350px;
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

/* 分页条：贴右显示，与表格留出间距 */
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.exam-option {
  display: flex;
  flex-direction: column;
}

.exam-sub {
  font-size: 12px;
  color: #909399;
}

.question-text {
  font-size: 13px;
  line-height: 1.5;
}

.correct {
  color: #67c23a;
  font-weight: 600;
}

.wrong {
  color: #f56c6c;
  font-weight: 600;
}

.rate-high {
  color: #67c23a;
  font-weight: 600;
}

.rate-mid {
  color: #e6a23c;
  font-weight: 600;
}

.rate-low {
  color: #f56c6c;
  font-weight: 600;
}

.diff-hard {
  color: #f56c6c;
  font-weight: 600;
}

.diff-mid {
  color: #67c23a;
  font-weight: 600;
}

.diff-easy {
  color: #409EFF;
  font-weight: 600;
}

.ml-4 {
  margin-left: 16px;
}

.question-detail {
  line-height: 1.8;
}

.detail-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.detail-row .label {
  color: #606266;
  font-weight: 600;
}

.answer {
  color: #409EFF;
  font-weight: 600;
}

.detail-content {
  margin-bottom: 12px;
}

.content-label {
  color: #606266;
  font-weight: 600;
  margin-bottom: 6px;
}

.content-text {
  color: #303133;
  white-space: pre-wrap;
  line-height: 1.8;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.stats-row {
  display: flex;
  justify-content: space-around;
  margin-top: 12px;
}

.stat-item {
  text-align: center;
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

.stat-green .stat-num {
  color: #67c23a;
}

.stat-red .stat-num {
  color: #f56c6c;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.loading-wrap {
  text-align: center;
  padding: 40px;
  color: #909399;
}

.detail-summary {
  margin-top: 12px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}
</style>
