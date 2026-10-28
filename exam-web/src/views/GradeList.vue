<template>
  <div class="grade-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="试卷">
          <el-select
            v-model="query.paperId"
            placeholder="请选择试卷"
            style="width: 220px"
            @change="handleSearch"
          >
            <el-option
              v-for="p in papers"
              :key="p.id"
              :label="p.paperName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="学科">
          <el-select
            v-model="query.courseId"
            placeholder="全部学科"
            clearable
            style="width: 150px"
            @change="handleSearch"
          >
            <el-option
              v-for="c in courses"
              :key="c.id"
              :label="c.courseName"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="学生">
          <el-input
            v-model="query.keyword"
            placeholder="学号 / 姓名"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 成绩列表 / 待阅卷 Tab -->
    <el-tabs v-model="activeTab" class="grade-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="成绩列表" name="grades" />
      <el-tab-pane label="待阅卷" name="pending" />
    </el-tabs>

    <!-- 成绩列表：需先选择试卷 -->
    <template v-if="activeTab === 'grades'">
    <!-- 未选试卷时的占位 -->
    <el-card v-if="!query.paperId" class="empty-card">
      <el-empty description="请先选择一份试卷，再查看成绩">
        <template #image>
          <el-icon :size="80" color="#c0c4cc"><Document /></el-icon>
        </template>
      </el-empty>
    </el-card>

    <!-- 已选试卷：展示统计 + 表格 + 分数段分布 -->
    <template v-else>
    <!-- 统计区 -->
    <div class="stats-row">
      <el-card class="stat-card">
        <div class="stat-label">参考人次</div>
        <div class="stat-value">{{ stats.totalCount || 0 }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-label">平均分</div>
        <div class="stat-value">{{ stats.avgScore ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-label">最高分 / 最低分</div>
        <div class="stat-value small">
          <span class="max">{{ stats.maxScore ?? 0 }}</span>
          <span class="sep">/</span>
          <span class="min">{{ stats.minScore ?? 0 }}</span>
        </div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-label">及格率</div>
        <div class="stat-value" :class="passRate >= 60 ? 'good' : 'bad'">
          {{ passRate }}%
        </div>
      </el-card>
    </div>

    <div class="main-row">
      <!-- 成绩表格 -->
      <el-card class="table-card">
        <div class="table-toolbar">
          <span class="total-info">共 {{ total }} 条成绩</span>
        </div>

        <el-table :data="tableData" v-loading="loading" border style="width: 100%" :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
          <el-table-column label="排名" width="70" align="center">
            <template #default="{ row }">{{ row.rank ?? '-' }}</template>
          </el-table-column>

          <el-table-column prop="username" label="学号" min-width="110" />

          <el-table-column prop="realName" label="姓名" min-width="90" />

          <el-table-column prop="paperName" label="试卷" min-width="150" show-overflow-tooltip />

          <el-table-column prop="courseName" label="学科" width="120">
            <template #default="{ row }">{{ row.courseName || '-' }}</template>
          </el-table-column>

          <el-table-column label="得分 / 满分" width="130" sortable :sort-method="sortByScore">
            <template #default="{ row }">
              <span class="score-num" :class="scoreClass(row)">{{ row.totalScore ?? '-' }}</span>
              <span class="score-full"> / {{ row.paperTotalScore ?? '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="isPass" label="是否及格" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isPass === 1 ? 'success' : 'danger'" effect="plain">
                {{ row.isPass === 1 ? '及格' : '不及格' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.status === 0" type="warning" effect="plain">考试中</el-tag>
              <el-tag v-else-if="row.status === 1" type="info" effect="plain">已交卷</el-tag>
              <el-tag v-else type="success" effect="plain">已判分</el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="submitTime" label="交卷时间" width="185" align="center">
            <template #default="{ row }">
              <span class="time-cell">{{ row.submitTime || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 1"
                type="primary"
                link
                :icon="EditPen"
                @click="goReview(row.id)"
              >去阅卷</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无成绩数据" />
          </template>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="loadData"
            @current-change="loadData"
          />
        </div>
      </el-card>

      <!-- 分数分布图（纯 CSS 柱状图） -->
      <el-card class="chart-card">
        <template #header>
          <span class="chart-title">分数分布（按得分率）</span>
        </template>

        <div class="bar-chart">
          <div v-for="seg in segments" :key="seg.label" class="bar-item">
            <div class="bar-count">{{ seg.count }}</div>
            <div
              class="bar-body"
              :class="seg.cls"
              :style="{ height: barHeight(seg.count) + 'px' }"
            />
            <div class="bar-label">{{ seg.label }}</div>
          </div>
        </div>

        <div v-if="!stats.totalCount" class="chart-empty">暂无数据</div>

        <div class="chart-note">得分率 = 得分 ÷ 试卷满分 × 100%，跨试卷统一口径</div>
      </el-card>
    </div>
    </template>
    </template>

    <!-- 待阅卷列表：status=1 已交卷、简答题待教师判分 -->
    <template v-else>
      <el-card class="table-card">
        <div class="table-toolbar">
          <span class="total-info">共 {{ pendingTotal }} 份待阅卷</span>
          <span class="pending-tip">客观题已自动判分，仅需对简答题人工打分</span>
        </div>

        <el-table :data="pendingData" v-loading="pendingLoading" border style="width: 100%">
          <el-table-column prop="paperName" label="试卷" min-width="150" show-overflow-tooltip />

          <el-table-column prop="courseName" label="学科" width="120">
            <template #default="{ row }">{{ row.courseName || '-' }}</template>
          </el-table-column>

          <el-table-column prop="username" label="学号" min-width="110" />

          <el-table-column prop="realName" label="姓名" min-width="90" />

          <el-table-column prop="totalScore" label="客观题得分" width="110" align="center">
            <template #default="{ row }">
              <span class="score-num">{{ row.totalScore ?? '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="submitTime" label="交卷时间" width="185" align="center">
            <template #default="{ row }">
              <span class="time-cell">{{ row.submitTime || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link :icon="EditPen" @click="goReview(row.id)">去阅卷</el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无待阅卷记录" />
          </template>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="pendingTotal"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="loadPending"
            @current-change="loadPending"
          />
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Document, EditPen } from '@element-plus/icons-vue'
import { getGradePage, getGradeStats, getPendingGrades } from '@/api/grade'
import { getPaperList } from '@/api/examPaper'
import { getCourseList } from '@/api/course'
import { useDefaultPageSize } from '@/composables/useDefaultPageSize'

const router = useRouter()
const activeTab = ref('grades')

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const papers = ref([])
const courses = ref([])
const stats = ref({})

const pendingLoading = ref(false)
const pendingData = ref([])
const pendingTotal = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  paperId: null,
  courseId: null,
  keyword: ''
})

/** 及格率（参考人次为 0 时显示 0） */
const passRate = computed(() => {
  const t = Number(stats.value.totalCount || 0)
  if (!t) return 0
  return Math.round((Number(stats.value.passCount || 0) / t) * 100)
})

/** 分数段（与后端 GradeStats 字段对应） */
const segments = computed(() => [
  { label: '<60', count: Number(stats.value.d0To59 || 0), cls: 'seg-fail' },
  { label: '60-69', count: Number(stats.value.d60To69 || 0), cls: 'seg-low' },
  { label: '70-79', count: Number(stats.value.d70To79 || 0), cls: 'seg-mid' },
  { label: '80-89', count: Number(stats.value.d80To89 || 0), cls: 'seg-good' },
  { label: '90-100', count: Number(stats.value.d90To100 || 0), cls: 'seg-excellent' }
])

/** 柱子高度：最高段 160px，其余按比例 */
function barHeight(count) {
  const max = Math.max(...segments.value.map((s) => s.count), 1)
  return Math.round((count / max) * 160)
}

/** 得分率 >= 60% 绿色，否则红色 */
function scoreClass(row) {
  if (row.totalScore == null || !row.paperTotalScore) return ''
  return (row.totalScore / row.paperTotalScore) * 100 >= 60 ? 'pass' : 'fail'
}

function sortByScore(a, b) {
  return (a.totalScore ?? -1) - (b.totalScore ?? -1)
}

async function loadData() {
  loading.value = true
  try {
    const [pageRes, statsRes] = await Promise.all([
      getGradePage({
        pageNum: query.pageNum,
        pageSize: query.pageSize,
        paperId: query.paperId ?? undefined,
        courseId: query.courseId ?? undefined,
        keyword: query.keyword || undefined
      }),
      getGradeStats({ paperId: query.paperId ?? undefined, courseId: query.courseId ?? undefined })
    ])
    // 后端通过 SQL 窗口函数 RANK() OVER (PARTITION BY paper_id ORDER BY total_score DESC) 已算出 rank
    tableData.value = pageRes.data?.records || []
    total.value = pageRes.data?.total || 0
    stats.value = statsRes.data || {}
  } catch (err) {
    console.error('加载成绩数据失败:', err)
    ElMessage.error('加载失败：' + (err.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

async function loadPending() {
  pendingLoading.value = true
  try {
    const res = await getPendingGrades({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      paperId: query.paperId ?? undefined,
      keyword: query.keyword || undefined
    })
    pendingData.value = res.data?.records || []
    pendingTotal.value = res.data?.total || 0
  } catch (err) {
    console.error('加载待阅卷列表失败:', err)
    ElMessage.error('加载失败：' + (err.message || '网络异常'))
  } finally {
    pendingLoading.value = false
  }
}

function handleTabChange() {
  if (activeTab.value === 'pending') {
    query.pageNum = 1
    loadPending()
  }
}

function goReview(recordId) {
  router.push(`/grades/${recordId}/review`)
}

async function loadPapers() {
  try {
    const res = await getPaperList({ pageNum: 1, pageSize: 100 })
    // list 接口可能返回 PageResult 或数组，做兼容
    papers.value = Array.isArray(res.data) ? res.data : res.data?.records || []
  } catch (err) {
    console.error('加载试卷列表失败:', err)
  }
}

async function loadCourses() {
  try {
    const res = await getCourseList()
    courses.value = res.data || []
  } catch (err) {
    console.error('加载学科列表失败:', err)
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.paperId = null
  query.courseId = null
  query.keyword = ''
  query.pageNum = 1
  loadData()
}

onMounted(async () => {
  try {
    query.pageSize = await useDefaultPageSize()
  } catch {}
  // 只加载试卷/学科下拉，不主动加载成绩（要求先选试卷）
  loadPapers()
  loadCourses()
})
</script>

<style scoped>
.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.grade-tabs {
  margin-top: 16px;
}

.pending-tip {
  color: #909399;
  font-size: 13px;
}

/* 未选试卷时的占位卡 */
.empty-card {
  margin-top: 16px;
}

.empty-card :deep(.el-empty) {
  padding: 60px 0;
}

/* 统计卡片行 */
.stats-row {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  text-align: center;
}

.stat-label {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 30px;
  font-weight: 700;
  color: #303133;
}

.stat-value.small {
  font-size: 24px;
}

.stat-value.small .sep {
  color: #c0c4cc;
  margin: 0 6px;
  font-weight: 400;
}

.stat-value .max {
  color: #409eff;
}

.stat-value .min {
  color: #909399;
}

.stat-value.good {
  color: #67c23a;
}

.stat-value.bad {
  color: #f56c6c;
}

/* 主体区：左表格 + 右分布图 */
.main-row {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 16px;
  align-items: start;
}

.table-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.total-info {
  color: #909399;
  font-size: 14px;
}

.score-num {
  font-size: 16px;
  font-weight: 700;
}

.score-num.pass {
  color: #67c23a;
}

.score-num.fail {
  color: #f56c6c;
}

.score-full {
  color: #909399;
  font-size: 13px;
}

.time-cell {
  color: #606266;
  font-family: Consolas, monospace;
  font-size: 13px;
  white-space: nowrap;   /* 交卷时间固定单行显示，不允许折行 */
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 分数分布柱状图 */
.chart-title {
  font-weight: 600;
}

.bar-chart {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  height: 230px;
  padding: 10px 6px 0;
}

.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
}

.bar-count {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  font-weight: 600;
}

.bar-body {
  width: 34px;
  min-height: 4px;
  border-radius: 4px 4px 0 0;
  transition: height 0.4s ease;
}

.seg-fail {
  background: linear-gradient(180deg, #fab6b6, #f56c6c);
}

.seg-low {
  background: linear-gradient(180deg, #f5dab1, #e6a23c);
}

.seg-mid {
  background: linear-gradient(180deg, #a0cfff, #79bbff);
}

.seg-good {
  background: linear-gradient(180deg, #b3e19d, #67c23a);
}

.seg-excellent {
  background: linear-gradient(180deg, #79bbff, #409eff);
}

.bar-label {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.chart-empty {
  text-align: center;
  color: #c0c4cc;
  margin-top: -140px;
  position: relative;
}

.chart-note {
  margin-top: 12px;
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
}

/* 窄屏时上下堆叠 */
@media (max-width: 1100px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .main-row {
    grid-template-columns: 1fr;
  }
}
</style>
