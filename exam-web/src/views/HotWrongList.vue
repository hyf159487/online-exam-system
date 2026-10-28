<template>
  <div class="hot-wrong-page">
    <!-- 筛选条件 -->
    <el-card shadow="never" class="filter-card">
      <!-- 筛选条件与查询/重置按钮固定同一行：不换行，窗口过窄时横向滚动 -->
      <el-form :inline="true" :model="query" class="filter-form" @submit.prevent>
        <el-form-item label="题型">
          <el-select v-model="query.questionType" placeholder="全部" clearable style="width:110px">
            <el-option label="单选题" :value="1" />
            <el-option label="多选题" :value="2" />
            <el-option label="判断题" :value="3" />
            <el-option label="填空题" :value="4" />
            <el-option label="简答题" :value="5" />
            <el-option label="名词解释" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="query.difficulty" placeholder="全部" clearable style="width:100px">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="query.courseId" placeholder="全部" clearable filterable style="width:150px">
            <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="最低错误率">
          <el-select v-model="query.minErrorRate" placeholder="不限" clearable style="width:100px">
            <el-option label="> 10%" :value="10" />
            <el-option label="> 30%" :value="30" />
            <el-option label="> 50%" :value="50" />
            <el-option label="> 70%" :value="70" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="query.sortBy" style="width:130px">
            <el-option label="错误率降序" value="errorRate_desc" />
            <el-option label="错误次数降序" value="wrongCount_desc" />
            <el-option label="作答次数降序" value="totalCount_desc" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" border stripe :header-cell-style="{ textAlign: 'center', whiteSpace: 'nowrap' }" :cell-style="{ textAlign: 'center' }">
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (query.pageNum - 1) * query.pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="content" label="题干" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="content-cell">{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="80" align="center">
          <template #default="{ row }">{{ typeText(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="难度" width="80" align="center">
          <template #default="{ row }">{{ difficultyText(row.difficulty) }}</template>
        </el-table-column>
        <el-table-column prop="totalCount" label="总作答" width="110" align="center" sortable />
        <el-table-column prop="wrongCount" label="错误次数" width="110" align="center" sortable />
        <el-table-column label="错误率" width="110" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="errorRateTagType(row.errorRate)" size="small">
              {{ row.errorRate != null ? row.errorRate.toFixed(1) + '%' : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="所属课程" width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="info" link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button type="warning" link size="small" @click="handleStat(row)">统计</el-button>
            <el-button type="success" link size="small" @click="handleAddToPaper(row)">加入试卷</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无高频错题数据" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 试题详情弹窗 -->
    <el-dialog v-model="detailVisible" title="试题详情" width="680px">
      <div v-if="currentItem" class="detail-content">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="题目ID">{{ currentItem.id }}</el-descriptions-item>
          <el-descriptions-item label="题型">{{ typeText(currentItem.questionType) }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ difficultyText(currentItem.difficulty) }}</el-descriptions-item>
          <el-descriptions-item label="总作答">{{ currentItem.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="错误次数">{{ currentItem.wrongCount }}</el-descriptions-item>
          <el-descriptions-item label="错误率">
            <el-tag :type="errorRateTagType(currentItem.errorRate)" size="small">
              {{ currentItem.errorRate?.toFixed(1) }}%
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-section">
          <div class="section-title">题干</div>
          <div class="section-body">{{ currentItem.content }}</div>
        </div>
        <div v-if="currentItem.options" class="detail-section">
          <div class="section-title">选项</div>
          <div class="section-body">
            <div v-for="(opt, idx) in parseOptions(currentItem.options)" :key="idx">
              {{ letter(idx) }}. {{ opt }}
            </div>
          </div>
        </div>
        <div class="detail-section">
          <div class="section-title">参考答案</div>
          <div class="section-body">{{ currentItem.answer || '-' }}</div>
        </div>
        <div v-if="currentItem.analysis" class="detail-section">
          <div class="section-title">解析</div>
          <div class="section-body">{{ currentItem.analysis }}</div>
        </div>
      </div>
    </el-dialog>

    <!-- 错误率统计弹窗 -->
    <el-dialog v-model="statVisible" title="错题统计" width="420px">
      <div v-if="statData" class="stat-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="题目ID">{{ statData.id }}</el-descriptions-item>
          <el-descriptions-item label="总作答次数">
            <span class="stat-num">{{ statData.totalCount || 0 }}</span> 次
          </el-descriptions-item>
          <el-descriptions-item label="错误次数">
            <span class="stat-num error">{{ statData.wrongCount || 0 }}</span> 次
          </el-descriptions-item>
          <el-descriptions-item label="正确次数">
            <span class="stat-num success">{{ (statData.totalCount || 0) - (statData.wrongCount || 0) }}</span> 次
          </el-descriptions-item>
          <el-descriptions-item label="错误率">
            <el-tag :type="errorRateTagType(statData.errorRate)" size="large">
              {{ statData.errorRate?.toFixed(2) }}%
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="stat-bar">
          <el-progress :percentage="statData.errorRate || 0" :stroke-width="20" :text-inside="true" />
        </div>
      </div>
    </el-dialog>

    <!-- 加入试卷弹窗 -->
    <el-dialog v-model="addPaperVisible" title="加入试卷" width="420px">
      <div v-if="currentItem">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="题干">{{ currentItem.content?.slice(0, 40) }}{{ currentItem.content && currentItem.content.length > 40 ? '...' : '' }}</el-descriptions-item>
          <el-descriptions-item label="题型">{{ typeText(currentItem.questionType) }}</el-descriptions-item>
          <el-descriptions-item label="错误率">{{ currentItem.errorRate?.toFixed(1) }}%</el-descriptions-item>
        </el-descriptions>
        <el-form :model="addPaperForm" label-width="90px">
          <el-form-item label="选择试卷" required>
            <el-select v-model="addPaperForm.paperId" placeholder="请选择试卷" filterable style="width: 100%">
              <el-option v-for="p in myPapers" :key="p.id" :label="p.paperName" :value="p.id">
                {{ p.paperName }}（{{ p.totalScore }}分）
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="设置分值" required>
            <el-input-number v-model="addPaperForm.score" :min="1" :max="100" :step="1" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="addPaperVisible = false">取消</el-button>
        <el-button type="primary" :loading="addPaperLoading" @click="confirmAddToPaper">确认加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getHotWrongList, getHotWrongStat, addToPaper } from '@/api/hotWrong'
import { getCourseList } from '@/api/course'
import { getPaperList } from '@/api/examPaper'

const TYPE_MAP = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }
const DIFFICULTY_MAP = { 1: '简单', 2: '中等', 3: '困难', 4: '较难', 5: '困难' }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  questionType: null,
  difficulty: null,
  courseId: null,
  minErrorRate: null,
  sortBy: 'errorRate_desc'
})

const courseList = ref([])
const myPapers = ref([])

const detailVisible = ref(false)
const statVisible = ref(false)
const addPaperVisible = ref(false)
const addPaperLoading = ref(false)
const currentItem = ref(null)
const statData = ref(null)
const addPaperForm = reactive({ paperId: null, score: 5 })

onMounted(async () => {
  loadList()
  loadCourses()
  loadMyPapers()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getHotWrongList(query)
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.questionType = null
  query.difficulty = null
  query.courseId = null
  query.minErrorRate = null
  query.sortBy = 'errorRate_desc'
  query.pageNum = 1
  loadList()
}

async function loadCourses() {
  try {
    const res = await getCourseList()
    courseList.value = res.data || []
  } catch {}
}

async function loadMyPapers() {
  try {
    const res = await getPaperList({ pageSize: 100 })
    myPapers.value = res.data?.records || res.data || []
  } catch {}
}

function handleDetail(row) {
  currentItem.value = row
  detailVisible.value = true
}

async function handleStat(row) {
  try {
    const res = await getHotWrongStat(row.id)
    statData.value = res.data
    statVisible.value = true
  } catch (e) {
    ElMessage.error(e?.msg || '统计查询失败')
  }
}

function handleAddToPaper(row) {
  currentItem.value = row
  addPaperForm.paperId = null
  addPaperForm.score = row.score || 5
  addPaperVisible.value = true
}

async function confirmAddToPaper() {
  if (!addPaperForm.paperId) {
    ElMessage.warning('请选择试卷')
    return
  }
  addPaperLoading.value = true
  try {
    await addToPaper(addPaperForm.paperId, currentItem.value.id, addPaperForm.score)
    ElMessage.success('已加入试卷')
    addPaperVisible.value = false
  } catch (e) {
    ElMessage.error(e?.msg || '操作失败')
  } finally {
    addPaperLoading.value = false
  }
}

function typeText(t) { return TYPE_MAP[t] || '未知' }
function difficultyText(d) { return DIFFICULTY_MAP[d] || '-' }
function parseOptions(json) {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}
function letter(idx) { return String.fromCharCode(65 + idx) }

function errorRateTagType(rate) {
  if (rate == null) return 'info'
  if (rate >= 70) return 'danger'
  if (rate >= 40) return 'warning'
  return 'success'
}
</script>

<style scoped>
.filter-card { margin-bottom: 12px; }
/* 筛选条件 + 查询/重置按钮强制同一行：
   1) 表单用 flex 且 nowrap，控件不再自动折行
   2) 收紧 form-item 的下间距与右间距，label 右侧留白压缩
   3) 窗口过窄时整行横向滚动，避免按钮被挤到第二行 */
.filter-card :deep(.el-card__body) { overflow-x: auto; }
.filter-form {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
}
.filter-form :deep(.el-form-item) {
  margin-right: 20px;
  margin-bottom: 0;
  flex-shrink: 0;
}
.filter-form :deep(.el-form-item:last-child) { margin-right: 0; }
.filter-form :deep(.el-form-item__label) { padding-right: 10px; }
.filter-form :deep(.el-form-item__content) { flex-wrap: nowrap; }
.content-cell {
  font-size: 13px;
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pagination-wrapper {
  display: flex; justify-content: flex-end; margin-top: 16px;
}
.stat-content { padding: 10px 0; }
.stat-num {
  font-size: 20px; font-weight: bold; color: #303133; margin: 0 4px;
}
.stat-num.error { color: #f56c6c; }
.stat-num.success { color: #67c23a; }
.stat-bar { margin-top: 20px; }
.detail-content { line-height: 1.7; }
.detail-section { margin-top: 16px; }
.detail-section .section-title {
  font-weight: bold; color: #303133; margin-bottom: 6px; font-size: 13px;
}
.detail-section .section-body {
  background: #f5f7fa; padding: 10px 14px; border-radius: 4px; color: #606266;
}
</style>
