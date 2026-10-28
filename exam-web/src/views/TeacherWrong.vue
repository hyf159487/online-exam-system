<template>
  <div class="wrong-manage">
    <div class="page-title">
      <h2>错题管理</h2>
      <span class="tip">查看全班错题分布与高频错题，辅助讲评与薄弱点定位</span>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="14">
        <el-card shadow="never" class="stat-card">
          <template #header><span class="card-title">按学科分布</span></template>
          <el-table :data="courseStats" size="small" v-loading="statLoading">
            <el-table-column prop="courseName" label="学科" min-width="120" />
            <el-table-column prop="wrongCount" label="错题总数" width="100" align="center" />
            <el-table-column prop="studentCount" label="涉及学生" width="100" align="center" />
            <template #empty><el-empty description="暂无数据" :image-size="40" /></template>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" class="stat-card">
          <template #header><span class="card-title">高频错题 Top10</span></template>
          <el-table :data="topQuestions" size="small" v-loading="statLoading">
            <el-table-column type="index" label="#" width="45" />
            <el-table-column prop="content" label="题干" min-width="180" show-overflow-tooltip />
            <el-table-column prop="wrongCount" label="错次" width="70" align="center" />
            <el-table-column prop="studentCount" label="学生数" width="75" align="center" />
            <template #empty><el-empty description="暂无数据" :image-size="40" /></template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="班级">
          <el-select v-model="query.classId" placeholder="全部班级" clearable style="width: 160px" @change="handleSearch">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学科">
          <el-select v-model="query.courseId" placeholder="全部学科" clearable style="width: 150px" @change="handleSearch">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="query.questionType" placeholder="全部题型" clearable style="width: 130px" @change="handleSearch">
            <el-option v-for="(t, k) in TYPE_MAP" :key="k" :label="t" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="summary">
          共 <b>{{ total }}</b> 条错题
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 全班错题列表 -->
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe>
        <!-- 序号列：跨页连续编号（第 2 页第 1 行 = 11），由 indexMethod 补上分页偏移量 -->
        <el-table-column label="#" type="index" width="50" :index="indexMethod" />
        <el-table-column prop="studentName" label="学生" width="90" />
        <el-table-column prop="className" label="班级" width="100">
          <template #default="{ row }">{{ row.className || '-' }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="学科" width="100" />
        <el-table-column label="题型" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeText(row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="题干" min-width="200" show-overflow-tooltip />
        <el-table-column label="学生答案" width="110" align="center">
          <template #default="{ row }"><span class="ans wrong">{{ row.userAnswer || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="正确答案" width="110" align="center">
          <template #default="{ row }"><span class="ans right">{{ row.correctAnswer || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="wrongCount" label="错次" width="65" align="center" />
        <el-table-column label="最近错误" min-width="145">
          <template #default="{ row }">{{ row.lastWrongTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无错题记录" /></template>
      </el-table>

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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="错题详情" width="560px">
      <div v-if="current" class="detail">
        <div class="d-row">
          <span class="d-label">学生</span>{{ current.studentName }}（{{ current.studentNo }}）
          <span v-if="current.className" style="margin-left:12px;color:#909399">{{ current.className }}</span>
        </div>
        <div class="d-row">
          <span class="d-label">题型</span>{{ typeText(current.questionType) }}（{{ current.courseName }}）
        </div>
        <div class="d-row"><span class="d-label">题干</span>{{ current.content }}</div>
        <div v-if="optionsList.length" class="d-row">
          <span class="d-label">选项</span>
          <div class="opts">
            <div v-for="o in optionsList" :key="o.key" class="opt-item">{{ o.key }}. {{ o.text }}</div>
          </div>
        </div>
        <div class="d-row"><span class="d-label">学生答案</span><span class="ans wrong">{{ current.userAnswer }}</span></div>
        <div class="d-row"><span class="d-label">正确答案</span><span class="ans right">{{ current.correctAnswer }}</span></div>
        <div class="d-row"><span class="d-label">错误次数</span>{{ current.wrongCount }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getTeacherWrongQuestions, getTeacherWrongStats, getClassList } from '@/api/teacherWrong'
import { getCourseList } from '@/api/course'

const TYPE_MAP = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }
const typeText = (t) => TYPE_MAP[t] || '未知'

const loading = ref(false)
const statLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const courseStats = ref([])
const topQuestions = ref([])
const classes = ref([])
const courses = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, classId: null, courseId: null, questionType: null })

/**
 * 序号列的取值：表格自增索引只从 1 开始，翻到第 2 页会重新从 1 数，
 * 这里补上分页偏移量，让序号跨页连续（第 2 页第 1 行 = 11）。
 */
function indexMethod(index) {
  return (query.pageNum - 1) * query.pageSize + index + 1
}

const detailVisible = ref(false)
const current = ref(null)

const optionsList = computed(() => {
  if (!current.value?.options) return []
  try {
    const arr = JSON.parse(current.value.options)
    if (Array.isArray(arr)) return arr.map((o, i) => ({ key: String.fromCharCode(65 + i), text: o }))
  } catch (e) {}
  return []
})

async function loadData() {
  loading.value = true
  try {
    const res = await getTeacherWrongQuestions({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      classId: query.classId ?? undefined,
      courseId: query.courseId ?? undefined,
      questionType: query.questionType ?? undefined
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (err) {
    console.error('加载错题列表失败:', err)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  statLoading.value = true
  try {
    const res = await getTeacherWrongStats({
      classId: query.classId ?? undefined,
      courseId: query.courseId ?? undefined,
      questionType: query.questionType ?? undefined
    })
    courseStats.value = res.data?.courseStats || []
    topQuestions.value = res.data?.topQuestions || []
  } catch (err) {
    console.error('加载错题统计失败:', err)
  } finally {
    statLoading.value = false
  }
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

function handleSearch() {
  query.pageNum = 1
  loadData()
  loadStats()
}

function handleReset() {
  query.classId = null
  query.courseId = null
  query.questionType = null
  query.pageNum = 1
  loadData()
  loadStats()
}

async function loadFilters() {
  try {
    const [cls, cs] = await Promise.all([getClassList(), getCourseList()])
    classes.value = cls.data || []
    courses.value = cs.data || []
  } catch (e) {}
}

onMounted(() => {
  loadFilters()
  loadData()
  loadStats()
})
</script>

<style scoped>
.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.page-title h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.tip {
  font-size: 13px;
  color: #909399;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  height: 100%;
}
.card-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.filter-card {
  margin-bottom: 16px;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.summary {
  margin-left: auto;
  color: #606266;
}
.summary b {
  color: #f56c6c;
  font-size: 16px;
}
.ans {
  font-weight: 600;
}
.ans.wrong {
  color: #f56c6c;
}
.ans.right {
  color: #67c23a;
}
.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.detail .d-row {
  margin-bottom: 14px;
  line-height: 1.7;
  color: #303133;
}
.d-label {
  display: inline-block;
  width: 70px;
  color: #909399;
  font-size: 13px;
}
.opts {
  display: inline-block;
  vertical-align: top;
}
.opt-item {
  margin-bottom: 4px;
}
</style>
