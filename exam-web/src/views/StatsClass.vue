<template>
  <div class="stats-class">
    <!-- ==================== 筛选区 ==================== -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-tips">
        <el-icon class="tips-icon"><InfoFilled /></el-icon>
        <span>按 课程 → 考试 → 班级 依次选择，成绩统计基于试卷真实满分动态计算及格线 / 优秀线 / 分数段，适配 50 / 100 / 120 / 150 分试卷。</span>
      </div>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="课程">
          <el-select
            v-model="filterCourseId"
            placeholder="全部课程"
            clearable
            style="width: 180px"
            @change="onCourseChange"
          >
            <el-option
              v-for="c in courseOptions"
              :key="c.courseId"
              :label="c.courseName"
              :value="c.courseId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="考试">
          <el-select
            v-model="filterExamId"
            placeholder="请选择考试"
            clearable
            filterable
            style="width: 270px"
            @change="onExamChange"
          >
            <el-option
              v-for="exam in visibleExams"
              :key="exam.id"
              :label="exam.examName"
              :value="exam.id"
            >
              <div class="exam-option">
                <span>{{ exam.examName }}</span>
                <span class="exam-sub">
                  {{ exam.courseName || '' }} · 结束于 {{ exam.endTime }} · 参考 {{ exam.studentCount || 0 }} 人
                </span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            :clearable="true"
            @change="onTimeRangeChange"
          />
        </el-form-item>

        <el-form-item label="班级">
          <el-select
            v-model="filterClassId"
            placeholder="请选择班级"
            clearable
            filterable
            style="width: 190px"
            :disabled="!filterExamId"
            @change="clearStats"
          >
            <el-option
              v-for="cls in classOptions"
              :key="cls.classId"
              :label="`${cls.className}（全班 ${cls.classSize || 0} 人）`"
              :value="cls.classId"
            />
          </el-select>
        </el-form-item>

        <el-form-item class="filter-buttons">
          <el-button type="primary" :disabled="!canSearch" @click="loadStats">
            <el-icon><Search /></el-icon>查询统计
          </el-button>
          <el-button @click="resetFilter"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-tooltip
            :content="canExport ? '导出指标 / 分数段 / 知识点 / 学生明细 完整 Excel' : '请先查询统计后导出'"
            placement="top"
          >
            <el-button type="success" :disabled="!canExport" :loading="exporting" @click="exportExcel">
              <el-icon><Download /></el-icon>导出 Excel
            </el-button>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ==================== 统计结果区 ==================== -->
    <div v-loading="loading" class="result-area">
      <template v-if="statsData">
        <!-- 本次统计对象说明 -->
        <div class="scope-bar">
          <span class="scope-title">{{ statsData.examName }}</span>
          <el-tag size="small" type="warning" effect="plain">{{ statsData.courseName || '未知课程' }}</el-tag>
          <el-tag size="small" type="info" effect="plain">{{ statsData.className }}</el-tag>
          <el-divider direction="vertical" />
          <span class="scope-meta">试卷满分 {{ fmt(statsData.fullScore) }} 分 · 及格线 ≥ {{ fmt(statsData.passLine) }} 分（60%） · 优秀线 ≥ {{ fmt(statsData.excellentLine) }} 分（85%）</span>
        </div>

        <!-- 指标卡片：三行排布 9 个指标（课程人数 / 应考人数 / 参考人数 / 平均分 / 最高分 / 最低分 / 及格率 / 不及格 / 优秀） -->
        <el-row :gutter="14" class="stat-cards">
          <el-col v-for="card in cardItems" :key="card.key" :xs="12" :sm="8" :md="8">
            <el-card shadow="hover" class="stat-card" :class="`card-${card.tone}`">
              <div class="stat-body">
                <div class="stat-value">{{ card.value }}</div>
                <div class="stat-label">
                  {{ card.label }}
                  <el-tooltip :content="card.tip" placement="top">
                    <el-icon class="tip-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 图表区：左分数段分布 / 右知识点正确率 -->
        <el-row :gutter="14" class="chart-row">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="chart-card">
              <template #header>
                <div class="card-header">
                  <span>分数段分布</span>
                  <span class="sub-text">按得分率百分比分段 · 悬浮查看详情</span>
                </div>
              </template>
              <div v-if="scoreHasData" ref="scoreChartRef" class="chart-container" />
              <el-empty
                v-else
                class="chart-empty"
                description="暂无该班级本次考试分数数据，请更换条件查询"
                :image-size="80"
              />
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card v-loading="dimensionLoading" shadow="never" class="chart-card">
              <template #header>
                <div class="card-header">
                  <span>作答维度统计</span>
                  <span class="sub-text">正确题数 / 总答题数</span>
                  <span class="flex-spacer" />
                  <el-radio-group v-model="dimensionMode" size="small" @change="onDimensionModeChange">
                    <el-radio value="type">按题型</el-radio>
                    <el-radio value="difficulty">按难度</el-radio>
                  </el-radio-group>
                </div>
              </template>
              <div v-if="dimensionHasData" ref="dimensionChartRef" class="chart-container" />
              <el-empty
                v-else
                class="chart-empty"
                :description="dimensionEmptyText"
                :image-size="80"
              />
            </el-card>
          </el-col>
        </el-row>

        <!-- 学生成绩明细 -->
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-header detail-header">
              <span class="detail-title">学生成绩明细</span>
              <span class="sub-text">应考 {{ statsData.candidateCount || 0 }} 人 · 参考 {{ statsData.studentCount || 0 }} · 缺考 {{ statsData.absentCount || 0 }} · 已按成绩降序，点击列头可排序</span>
              <span class="flex-spacer" />
              <el-button size="small" :disabled="!sortedRows.length" @click="exportCurrentRows">
                <el-icon><Download /></el-icon>导出当前明细
              </el-button>
            </div>
          </template>

          <el-table
            :data="pagedRows"
            stripe
            border
            :header-cell-style="{ background: '#f5f7fa', color: '#303133' }"
            @sort-change="onSortChange"
          >
            <el-table-column label="排名" width="64" align="center">
              <template #default="{ row }">
                <span v-if="!row.absent" class="rank-text">{{ row.rank }}</span>
                <span v-else class="muted-text">—</span>
              </template>
            </el-table-column>
            <el-table-column
              label="学号"
              prop="username"
              sortable="custom"
              width="130"
              align="center"
            />
            <el-table-column label="姓名" prop="realName" min-width="90" />
            <el-table-column label="得分 / 满分" width="130" align="center" sortable="custom" prop="totalScore">
              <template #default="{ row }">
                <template v-if="row.absent">
                  <span class="muted-text">—</span>
                </template>
                <template v-else>
                  <span :class="scoreClass(row.totalScore, statsData.fullScore)">
                    {{ fmt(row.totalScore) }} / {{ fmt(statsData.fullScore) }}
                  </span>
                </template>
              </template>
            </el-table-column>
            <el-table-column label="是否及格" width="92" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.absent" type="info" size="small" effect="plain">缺考</el-tag>
                <el-tag v-else :type="row.isPass === 1 ? 'success' : 'danger'" size="small">
                  {{ row.isPass === 1 ? '及格' : '不及格' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="答题用时" width="110" align="center">
              <template #default="{ row }">
                <span v-if="row.absent" class="muted-text">—</span>
                <span v-else>{{ row.usedMinutes ?? '—' }} 分钟</span>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" width="165" align="center">
              <template #default="{ row }">
                <span v-if="row.absent" class="muted-text">—</span>
                <span v-else>{{ row.submitTime || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.absent"
                  size="small"
                  type="primary"
                  link
                  @click="openAnswerDialog(row)"
                >
                  查看作答详情
                </el-button>
                <span v-else class="muted-text">无作答</span>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页：统计接口一次性返回全部学生，前端按页切片展示 -->
          <div v-if="sortedRows.length > 0" class="pagination-bar">
            <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="sortedRows.length"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="pageNum = 1"
            />
          </div>
        </el-card>
      </template>

      <!-- 未执行查询的空状态 -->
      <el-empty
        v-else-if="!loading"
        class="idle-empty"
        description="请选择考试与班级后点击“查询统计”查看学情数据"
        :image-size="110"
      >
        <el-button type="primary" :disabled="!canSearch" @click="loadStats">
          立即查询统计
        </el-button>
      </el-empty>
    </div>

    <!-- ==================== 作答详情弹窗 ==================== -->
    <el-dialog
      v-model="answerDialogVisible"
      :title="answerTitle"
      width="min(980px, 94vw)"
      top="4vh"
      destroy-on-close
    >
      <div v-loading="answerLoading">
        <template v-if="answerDetail">
          <!-- 成绩摘要 -->
          <div class="answer-summary">
            <div class="summary-main">
              <span class="summary-name">{{ answerDetail.realName }}（{{ answerDetail.username }}）</span>
              <span class="summary-score">
                得分 <b>{{ fmt(answerDetail.totalScore) }}</b> / {{ fmt(answerDetail.fullScore) }} 分
              </span>
              <el-tag v-if="answerDetail.recordStatus === 1" type="warning" size="small">主观题待教师阅卷</el-tag>
            </div>
            <div class="summary-sub">
              <span>用时：{{ answerDetail.usedMinutes ?? '—' }} 分钟</span>
              <span>提交：{{ answerDetail.submitTime || '—' }}</span>
              <span>答对 {{ answerDetail.correctCount || 0 }} 题</span>
              <span>答错 {{ answerDetail.wrongCount || 0 }} 题</span>
              <span v-if="(answerDetail.pendingCount || 0) > 0">待阅 {{ answerDetail.pendingCount }} 题</span>
            </div>
          </div>

          <!-- 逐题列表 -->
          <div class="answer-list">
            <div v-for="(a, idx) in answerDetail.answers" :key="a.questionId" class="answer-item">
              <div class="a-head">
                <span class="a-no">{{ idx + 1 }}.</span>
                <el-tag size="small" type="info" effect="plain" class="a-type">{{ typeName(a.questionType) }}</el-tag>
                <el-tag
                  size="small"
                  :type="resultType(a)"
                  effect="light"
                  class="a-result"
                >{{ resultText(a) }}</el-tag>
                <span class="a-score">本题 {{ fmt(a.getScore) }} / {{ fmt(a.questionScore) }} 分</span>
                <el-tag v-if="a.knowledgeName" size="small" type="warning" effect="plain" class="a-knowledge">{{ a.knowledgeName }}</el-tag>
              </div>
              <div class="a-content" v-html="plainText(a.content)"></div>

              <div v-if="a.options" class="a-options">
                <div
                  v-for="(opt, oi) in optionLines(a)"
                  :key="oi"
                  class="a-option"
                  :class="optionClass(a, opt)"
                >
                  <span class="opt-mark">{{ chosenMark(a, opt) }}</span>
                  <span class="opt-text" v-html="plainText(opt.text)"></span>
                </div>
              </div>

              <div class="a-answer-row">
                <span class="row-label">我的答案：</span>
                <span class="row-value" :class="{ 'no-answer': !a.userAnswer }">{{ a.userAnswer || '未作答' }}</span>
              </div>
              <div v-if="a.standardAnswer || a.analysis" class="a-answer-row std">
                <span class="row-label">标准答案：</span>
                <span class="row-value">{{ a.standardAnswer || '—' }}</span>
                <template v-if="a.analysis">
                  <span class="row-label">解析：</span>
                  <span class="row-value">{{ a.analysis }}</span>
                </template>
              </div>
            </div>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Download, Search, Refresh, InfoFilled
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getExamList, getClassList, getClassData, getStudentAnswers, exportClassStatsExcel, getDimensionStats } from '@/api/statsClass'

// ---------- 筛选状态 ----------
const loading = ref(false)
const exporting = ref(false)
const allExams = ref([])
const filterCourseId = ref(null)
const filterExamId = ref(null)
const filterClassId = ref(null)
const timeRange = ref([])
const classOptions = ref([])
const statsData = ref(null)

// ---------- 作答维度统计（题型 / 难度） ----------
const dimensionMode = ref('difficulty')     // 默认按难度
const dimensionData = ref(null)
const dimensionLoading = ref(false)

// ---------- 图表 ref ----------
const scoreChartRef = ref(null)
const dimensionChartRef = ref(null)
let scoreChart = null
let dimensionChart = null

// ==================== 课程 / 考试联动 ====================
const courseOptions = computed(() => {
  const map = new Map()
  allExams.value.forEach(e => {
    if (!map.has(e.courseId) && e.courseId) {
      map.set(e.courseId, { courseId: e.courseId, courseName: e.courseName || '未知课程' })
    }
  })
  return Array.from(map.values())
})

const visibleExams = computed(() => {
  let list = allExams.value
  if (filterCourseId.value) {
    list = list.filter(e => e.courseId === filterCourseId.value)
  }
  if (timeRange.value && timeRange.value.length === 2) {
    const [start, end] = timeRange.value
    list = list.filter(e => {
      const t = e.endTime ? String(e.endTime).slice(0, 10) : ''
      return t >= start && t <= end
    })
  }
  return list
})

const canSearch = computed(() => filterExamId.value && filterClassId.value)
const canExport = computed(() => !!statsData.value && !!filterExamId.value && !!filterClassId.value)
const scoreHasData = computed(() => {
  const d = statsData.value
  return !!(d && d.studentCount > 0 && d.scoreRanges && d.scoreRanges.length)
})

const dimensionHasData = computed(() =>
  !!(dimensionData.value && dimensionData.value.items && dimensionData.value.items.length)
)

const dimensionEmptyText = computed(() =>
  dimensionMode.value === 'difficulty'
    ? '暂无该班级本次考试不同难度作答数据，请更换条件查询'
    : '暂无该班级本次考试题型作答数据，请更换条件查询'
)

// ---------- 列表加载 ----------
function loadExams() {
  loading.value = true
  getExamList().then(res => {
    allExams.value = res.data || []
    if (!allExams.value.length) {
      ElMessage.warning('暂无已结束且存在有效交卷记录的考试')
    }
  }).catch(() => {
    /* 拦截器已提示 */
  }).finally(() => {
    loading.value = false
  })
}

function loadClassOptions(examId) {
  classOptions.value = []
  if (!examId) return
  getClassList(examId).then(res => {
    classOptions.value = res.data || []
    if (!classOptions.value.length) {
      ElMessage.warning('该考试暂无可统计班级，请检查学生班级和交卷记录')
    }
  }).catch(() => { /* 拦截器已提示 */ })
}

function onCourseChange() {
  filterExamId.value = null
  filterClassId.value = null
  classOptions.value = []
  clearStats()
}

function onTimeRangeChange() {
  // 所选考试被时间范围过滤掉时自动清空
  if (filterExamId.value && !visibleExams.value.some(e => e.id === filterExamId.value)) {
    filterExamId.value = null
    filterClassId.value = null
    classOptions.value = []
  }
  clearStats()
}

function onExamChange() {
  filterClassId.value = null
  classOptions.value = []
  clearStats()
  loadClassOptions(filterExamId.value)
}

function clearStats() {
  statsData.value = null
  dimensionData.value = null
  disposeCharts()
}

// ---------- 查询统计 ----------
function loadStats() {
  if (!canSearch.value) return
  loading.value = true
  disposeCharts()
  getClassData({
    examId: filterExamId.value,
    classId: filterClassId.value
  }).then(res => {
    const data = res.data || null
    statsData.value = data
    if (!data) {
      ElMessage.warning('该班级暂无有效成绩数据')
      return
    }
    if (data.fullScore <= 0) {
      ElMessage.error('该考试关联试卷满分异常（0 分），无法进行学情统计')
      statsData.value = null
      return
    }
    // 新一次统计从第一页开始展示
    pageNum.value = 1
    sortedRows.value = data.studentScores || []
    nextTick(() => renderCharts())
    loadDimensionStats()
  }).catch(() => {
    statsData.value = null
  }).finally(() => {
    loading.value = false
  })
}

function resetFilter() {
  filterCourseId.value = null
  filterExamId.value = null
  filterClassId.value = null
  timeRange.value = []
  classOptions.value = []
  statsData.value = null
  sortedRows.value = []
  pageNum.value = 1
  dimensionData.value = null
  disposeCharts()
}

// ---------- 作答维度统计 ----------
function loadDimensionStats() {
  if (!filterExamId.value || !filterClassId.value) return
  dimensionLoading.value = true
  getDimensionStats({ examId: filterExamId.value, classId: filterClassId.value }, dimensionMode.value)
    .then(res => {
      dimensionData.value = res.data || null
      nextTick(() => renderDimensionChart())
    }).catch(() => {
      dimensionData.value = null
    }).finally(() => {
      dimensionLoading.value = false
    })
}

function onDimensionModeChange() {
  // 切换题型 / 难度时直接重新请求，无需再点查询统计
  loadDimensionStats()
}

// ==================== 图表 ====================
function renderCharts() {
  if (scoreHasData.value) renderScoreChart()
}

function renderScoreChart() {
  if (!scoreChartRef.value) return
  if (scoreChart) scoreChart.dispose()
  scoreChart = echarts.init(scoreChartRef.value)
  const ranges = statsData.value.scoreRanges || []
  scoreChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter(p) {
        const d = ranges[p.dataIndex]
        return `${d.rangeName}<br/>人数：<b>${d.count} 人</b><br/>占参考人数：${fmtNum(d.ratio)}%<br/>对应实际分数：${d.actualText}`
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '38', containLabel: true },
    xAxis: { type: 'category', data: ranges.map(r => r.rangeName), name: '得分率' },
    yAxis: { type: 'value', name: '人数', minInterval: 1, min: 0 },
    series: [{
      name: '人数',
      type: 'bar',
      barWidth: 44,
      data: ranges.map(r => r.count),
      itemStyle: {
        color(p) {
          const colors = ['#f56c6c', '#e6a23c', '#409eff', '#409eff', '#67c23a']
          return colors[p.dataIndex] || '#409EFF'
        },
        borderRadius: [6, 6, 0, 0]
      },
      label: { show: true, position: 'top', color: '#606266' }
    }]
  })
}

function renderDimensionChart() {
  if (!dimensionHasData.value) return
  if (!dimensionChartRef.value) return
  if (dimensionChart) dimensionChart.dispose()
  dimensionChart = echarts.init(dimensionChartRef.value)
  const data = dimensionData.value.items || []
  const dimLabel = dimensionMode.value === 'difficulty' ? '难度' : '题型'
  dimensionChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter(p) {
        const d = data[p.dataIndex]
        return `${dimLabel}：${d.name}<br/>答对：${d.correctCount} / 总作答：${d.totalCount}<br/>正确率：${fmtNum(d.correctRate)}%`
      }
    },
    grid: { left: 8, right: 70, bottom: 10, top: 10, containLabel: true },
    xAxis: { type: 'value', name: '正确率(%)', max: 100, min: 0 },
    yAxis: {
      type: 'category',
      data: data.map(x => x.name),
      axisLabel: { width: 90, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      barWidth: 18,
      data: data.map(x => ({
        value: x.correctRate,
        itemStyle: {
          color: colorByRate(x.correctRate),
          borderRadius: [0, 4, 4, 0]
        }
      })),
      label: { show: true, position: 'right', formatter: '{c}%', color: '#606266' }
    }]
  })
}

function colorByRate(v) {
  if (v >= 80) return '#67c23a'
  if (v >= 60) return '#e6a23c'
  return '#f56c6c'
}

function disposeCharts() {
  if (scoreChart) { scoreChart.dispose(); scoreChart = null }
  if (dimensionChart) { dimensionChart.dispose(); dimensionChart = null }
}

function handleResize() {
  if (scoreChart) scoreChart.resize()
  if (dimensionChart) dimensionChart.resize()
}

// ==================== 指标卡片 ====================
const cardItems = computed(() => {
  const d = statsData.value
  if (!d) return []
  return [
    { key: 'courseSize', tone: 'blue', label: '课程人数', value: d.courseSize || 0, tip: `已加入「${d.courseName || '本课程'}」的学生共 ${d.courseSize || 0} 人（不限班级；未加入课程的学生不计入）` },
    { key: 'candidateCount', tone: 'cyan', label: '应考人数', value: d.candidateCount || 0, tip: `本班已加入该课程、应参加本次考试的学生 ${d.candidateCount || 0} 人；其中缺考 ${d.absentCount || 0} 人` },
    { key: 'studentCount', tone: 'green', label: '参考人数', value: d.studentCount || 0, tip: `实际参考 ${d.studentCount || 0} 人；缺考 ${d.absentCount || 0} 人` },
    { key: 'avgScore', tone: 'orange', label: '平均分', value: d.avgScore == null ? '—' : fmtNum(d.avgScore), tip: '全体参考学生平均分' },
    { key: 'maxScore', tone: 'purple', label: '最高分', value: d.maxScore == null ? '—' : fmtNum(d.maxScore), tip: '参考学生中的最高得分' },
    { key: 'minScore', tone: 'red', label: '最低分', value: d.minScore == null ? '—' : fmtNum(d.minScore), tip: '参考学生中的最低得分' },
    { key: 'passRate', tone: 'indigo', label: '及格率', value: `${fmtNum(d.passRate)}%`, tip: `及格 ${d.passCount || 0} 人 / 参考 ${d.studentCount || 0} 人；及格线：≥ ${fmt(d.passLine)} 分（满分 ${fmt(d.fullScore)} 的 60%）` },
    { key: 'failCount', tone: 'gray', label: '不及格人数', value: d.failCount || 0, tip: `得分低于 ${fmt(d.passLine)} 分（满分 ${fmt(d.fullScore)} 的 60%）的人数` },
    { key: 'excellentCount', tone: 'teal', label: '优秀人数', value: d.excellentCount || 0, tip: `得分 ≥ ${fmt(d.excellentLine)} 分（满分 ${fmt(d.fullScore)} 的 85%）` }
  ]
})

// ==================== 明细表格排序 ====================
const sortedRows = ref([])
const sortState = ref({ prop: 'totalScore', order: 'descending' })

// ---------- 明细表格分页（统计接口返回全量学生，前端切片展示） ----------
const pageNum = ref(1)
const pageSize = ref(10)
const pagedRows = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return sortedRows.value.slice(start, start + pageSize.value)
})

/** 页数不足当前页时回退到最后一页（切换页码大小 / 排序后仍有效） */
function clampPage() {
  const maxPage = Math.max(1, Math.ceil(sortedRows.value.length / pageSize.value))
  if (pageNum.value > maxPage) pageNum.value = maxPage
}

function refreshSortedRows() {
  let rows = statsData.value?.studentScores ? [...statsData.value.studentScores] : []
  const { prop, order } = sortState.value
  if (!order || order === 'none') {
    sortedRows.value = rows
    clampPage()
    return
  }
  const dir = order === 'ascending' ? 1 : -1
  rows.sort((a, b) => {
    // 缺考始终沉底
    if (a.absent !== b.absent) return a.absent ? 1 : -1
    if (prop === 'username') {
      return (String(a.username).localeCompare(String(b.username), 'zh-CN')) * dir
    }
    const av = a.totalScore == null ? -1 : Number(a.totalScore)
    const bv = b.totalScore == null ? -1 : Number(b.totalScore)
    return (av - bv) * dir
  })
  sortedRows.value = rows
  clampPage()
}

function onSortChange({ prop, order }) {
  sortState.value = { prop, order }
  refreshSortedRows()
}

// ==================== 作答详情弹窗 ====================
const answerDialogVisible = ref(false)
const answerLoading = ref(false)
const answerDetail = ref(null)
const answerTitle = ref('查看作答详情')

function openAnswerDialog(row) {
  answerTitle.value = `作答详情 · ${row.realName}（${row.username}）`
  answerDialogVisible.value = true
  answerDetail.value = null
  answerLoading.value = true
  getStudentAnswers({
    examId: filterExamId.value,
    studentId: row.studentId,
    classId: filterClassId.value
  }).then(res => {
    answerDetail.value = res.data || null
    if (!answerDetail.value) ElMessage.warning('未获取到作答数据')
  }).catch(() => { /* 拦截器已提示 */ }).finally(() => {
    answerLoading.value = false
  })
}

// ==================== 导出 ====================
function exportExcel() {
  if (!canExport.value) return
  exporting.value = true
  exportClassStatsExcel({
    examId: filterExamId.value,
    classId: filterClassId.value
  }).then(resp => {
    const blob = resp.data instanceof Blob ? resp.data : new Blob([resp.data])
    const d = statsData.value || {}
    const safe = (v) => String(v || '班级学情').replace(/[\\/:*?"<>|]/g, '_')
    const filename = `${safe(d.examName)}_${safe(d.className)}_学情报表_${dateStr()}.xlsx`
    downloadBlob(blob, filename)
    ElMessage.success('导出成功')
  }).catch(() => { /* 拦截器已提示 */ }).finally(() => {
    exporting.value = false
  })
}

function exportCurrentRows() {
  const rows = sortedRows.value
  if (!rows.length) return
  const d = statsData.value || {}
  let csv = '\uFEFF'
  csv += `学生成绩明细（${d.examName} · ${d.className} · 试卷满分 ${fmt(d.fullScore)} 分）\n`
  csv += '排名,学号,姓名,得分,试卷满分,是否及格,答题用时(分钟),提交时间,状态\n'
  rows.forEach(r => {
    if (r.absent) {
      csv += `—,${r.username},${r.realName},—,${fmt(d.fullScore)},—,—,—,缺考\n`
    } else {
      csv += `${r.rank},${r.username},${r.realName},${fmt(r.totalScore)},${fmt(d.fullScore)},${r.isPass === 1 ? '及格' : '不及格'},${r.usedMinutes ?? ''},${r.submitTime || ''},已交卷\n`
    }
  })
  downloadBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), `${safeFileName(d.className)}_学生明细_${dateStr()}.csv`)
  ElMessage.success('明细导出成功')
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function dateStr() {
  const dt = new Date()
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
}

function safeFileName(v) {
  return String(v || '班级学情').replace(/[\\/:*?"<>|]/g, '_')
}

// ==================== 展示工具 ====================
function fmt(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return String(Math.round(n * 10) / 10)
}

function fmtNum(v) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  if (Number.isNaN(n)) return '0'
  return String(Math.round(n * 100) / 100)
}

function scoreClass(score, full) {
  const total = Number(full) || 100
  const ratio = (Number(score) || 0) / total
  if (ratio >= 0.85) return 'score-high'
  if (ratio >= 0.6) return 'score-mid'
  return 'score-low'
}

function typeName(t) {
  return { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }[t] || '题目'
}

function resultType(a) {
  if (a.correct === true) return 'success'
  if (a.correct === false) return 'danger'
  return 'warning'
}

function resultText(a) {
  if (a.correct === true) return '答对'
  if (a.correct === false) return '答错'
  if (a.questionType === 4 || a.questionType === 5) return '待阅卷'
  return '未判'
}

function plainText(html) {
  if (!html) return ''
  return String(html)
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/\n{2,}/g, '\n')
}

function parseOption(raw) {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    if (Array.isArray(arr)) {
      return arr.map(o => {
        if (o && typeof o === 'object') {
          return { label: o.label ?? o.key ?? '', text: o.text ?? o.content ?? '' }
        }
        return { label: '', text: String(o) }
      })
    }
  } catch (e) { /* ignore */ }
  // 兼容以换行/分号分隔的纯文本选项
  return String(raw).split(/[\n;；]/).filter(s => s.trim()).map(s => ({ label: '', text: s.trim() }))
}

function optionLines(a) {
  const list = parseOption(a.options)
  return list.length
    ? list.map(o => ({ ...o, chosen: optionChosen(a.userAnswer, o.label) }))
    : []
}

function optionChosen(answer, label) {
  if (!answer || !label) return false
  const ans = String(answer).toUpperCase()
  const lb = String(label).toUpperCase()
  if (!lb) return false
  return ans.split(/[,，;；\s]+/).includes(lb)
}

function chosenMark(a, opt) {
  if (opt.chosen) return '●'
  return ''
}

function optionClass(a, opt) {
  if (!opt.chosen) return ''
  return a.correct === true ? 'opt-right' : 'opt-wrong'
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadExams()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})

</script>

<style scoped>
.stats-class {
  padding: 4px;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-tips {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  background: #f7f8fa;
  border-radius: 6px;
  padding: 6px 10px;
  margin-bottom: 14px;
}

.tips-icon {
  color: #409eff;
  flex-shrink: 0;
}

.result-area {
  min-height: 200px;
}

.idle-empty {
  padding: 40px 0;
}

.scope-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 14px;
  font-size: 13px;
}

.scope-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.scope-meta {
  color: #909399;
}

.stat-cards {
  margin-bottom: 14px;
}

.stat-card {
  margin-bottom: 14px;
  height: 104px;
  border-radius: 10px;
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
}

.stat-value {
  font-size: 27px;
  font-weight: 700;
  color: #303133;
  line-height: 1.3;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

.tip-icon {
  color: #c0c4cc;
  cursor: help;
}

/* 卡片左侧强调条 */
.card-blue   { border-left: 4px solid #409eff; }
.card-cyan   { border-left: 4px solid #36cfc9; }
.card-green  { border-left: 4px solid #67c23a; }
.card-orange { border-left: 4px solid #e6a23c; }
.card-red    { border-left: 4px solid #f56c6c; }
.card-purple { border-left: 4px solid #9254de; }
.card-gray   { border-left: 4px solid #909399; }
.card-indigo { border-left: 4px solid #597ef7; }
.card-teal   { border-left: 4px solid #13c2c2; }

.chart-card {
  margin-bottom: 14px;
}

.chart-row {
  margin: 0 -7px;
}

.chart-row .el-col {
  padding: 0 7px;
}

.chart-container {
  height: 330px;
  width: 100%;
}

.chart-empty {
  height: 330px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-header {
  flex-wrap: wrap;
}

.detail-title {
  font-weight: 600;
}

.flex-spacer {
  flex: 1;
}

.sub-text {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

.detail-card {
  margin-bottom: 14px;
}

/* 明细表格分页栏 */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: 14px;
}

.rank-text {
  font-weight: 600;
  color: #303133;
}

.muted-text {
  color: #c0c4cc;
}

.score-high { color: #67c23a; font-weight: 600; }
.score-mid { color: #e6a23c; font-weight: 600; }
.score-low { color: #f56c6c; font-weight: 600; }

.exam-option {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}

.exam-sub {
  font-size: 12px;
  color: #909399;
}

/* ===== 作答详情弹窗 ===== */
.answer-summary {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 14px;
  background: #f9fafc;
}

.summary-main {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-name {
  font-weight: 600;
  font-size: 15px;
}

.summary-score b {
  color: #409eff;
  font-size: 16px;
}

.summary-sub {
  display: flex;
  gap: 18px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  flex-wrap: wrap;
}

.answer-list {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
}

.answer-item {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
}

.a-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.a-no {
  font-weight: 600;
  color: #303133;
}

.a-result {
  margin-left: 2px;
}

.a-score {
  font-size: 12px;
  color: #909399;
}

.a-knowledge {
  margin-left: auto;
}

.a-content {
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  margin-bottom: 6px;
}

.a-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 6px;
}

.a-option {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  padding: 2px 8px;
  border-radius: 4px;
}

.opt-mark {
  width: 14px;
  text-align: center;
  color: #409eff;
}

.opt-right {
  background: #f0f9eb;
  color: #67c23a;
}

.opt-wrong {
  background: #fef0f0;
  color: #f56c6c;
}

.opt-text {
  white-space: pre-wrap;
  word-break: break-all;
}

.a-answer-row {
  display: flex;
  gap: 6px;
  font-size: 13px;
  line-height: 1.7;
  align-items: flex-start;
  flex-wrap: wrap;
  color: #606266;
}

.row-label {
  color: #909399;
  flex-shrink: 0;
}

.row-value {
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}

.row-value.no-answer {
  color: #c0c4cc;
}

.std {
  margin-top: 4px;
}
</style>
