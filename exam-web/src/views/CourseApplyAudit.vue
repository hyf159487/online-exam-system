<template>
  <div class="course-apply-page">
    <div class="page-header">
      <div class="page-title-row">
        <h2 class="page-title">课程申请审核</h2>
        <span class="page-sub">审核学生加入课程的申请，同意后学生可查看该课程资料；驳回时填写理由，系统将通知学生。</span>
      </div>
      <!-- 导出：按当前筛选条件导出审核记录 Excel -->
      <el-button type="warning" plain :icon="Download" @click="handleExport">导出审核记录</el-button>
    </div>

    <!-- 统计卡片：待审核 / 本月已处理（点「待审核」可快速筛选） -->
    <div class="stat-row">
      <div class="stat-card stat-pending" :class="{ active: status === 0 }" @click="togglePendingFilter">
        <el-icon class="stat-icon"><Clock /></el-icon>
        <div class="stat-body">
          <div class="stat-num">{{ stats.pendingCount }}</div>
          <div class="stat-label">待审核申请</div>
        </div>
      </div>
      <div class="stat-card stat-handled">
        <el-icon class="stat-icon"><CircleCheck /></el-icon>
        <div class="stat-body">
          <div class="stat-num">{{ stats.handledThisMonth }}</div>
          <div class="stat-label">本月已处理申请</div>
        </div>
      </div>
    </div>

    <el-card shadow="never">
      <!-- 筛选：全部申请（状态）+ 课程 -->
      <div class="filter-bar">
        <el-select v-model="status" placeholder="全部申请" clearable style="width: 150px"
          @change="handleQuery" @clear="handleQuery">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
        </el-select>
        <el-select v-model="filterCourseId" placeholder="全部课程" clearable filterable style="width: 220px"
          @change="syncQuery" @clear="syncQuery">
          <el-option v-for="c in courseOptions" :key="c.id" :label="c.courseName" :value="Number(c.id)" />
        </el-select>
        <el-button type="primary" plain @click="handleQuery">查询</el-button>
        <el-button plain @click="handleReset">重置</el-button>
        <el-tag type="info" effect="plain">共 {{ list.length }} 条记录</el-tag>
      </div>

      <!-- 批量操作：批量驳回需要逐条填写理由，因此只提供批量通过 -->
      <div class="batch-bar">
        <el-button type="success" :icon="Check" :disabled="!batchRows.length" @click="handleBatchApprove">
          批量通过{{ batchRows.length ? `（${batchRows.length}）` : '' }}
        </el-button>
        <span class="batch-tip">
          {{ batchRows.length
            ? '批量通过后学生立即加入对应课程，并收到系统通知'
            : '批量驳回无法说明各自理由，请在操作列逐条驳回' }}
        </span>
      </div>

      <el-table ref="tableRef" :data="list" v-loading="loading" stripe border class="kp-table"
        :empty-text="emptyText" @selection-change="onSelectionChange">
        <!-- 只允许勾选待审核记录 -->
        <el-table-column type="selection" width="45" :selectable="row => row.status === 0" />
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="学生姓名" width="110" align="center">
          <template #default="{ row }">
            <!-- 点击学生姓名 → 查看学生基础信息 -->
            <el-link type="primary" :underline="false" @click="openStudent(row)">{{ row.studentName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="className" label="班级" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.className || '-' }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="申请课程" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- 点击课程名 → 跳转「我的课程」，自动打开该课程的学生名单 -->
            <el-link type="primary" :underline="false" @click="goMyCourse(row)">{{ row.courseName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="teacherNames" label="任课教师" width="150" show-overflow-tooltip />
        <el-table-column label="申请类型" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.applyType === 1 ? 'warning' : ''" effect="plain">
              {{ row.applyType === 1 ? '退出' : '加入' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="165" align="center" />
        <el-table-column label="申请状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button type="success" link @click="handleApprove(row)">
                {{ row.applyType === 1 ? '允许退课' : '通过' }}
              </el-button>
              <el-button type="danger" link @click="openReject(row)">驳回</el-button>
            </template>
            <template v-else>
              <!-- 已处理记录：置灰不可再次操作，驳回记录可查看理由 -->
              <span class="muted" :title="handleHint(row)">已处理</span>
              <el-button v-if="row.rejectReason" type="info" link @click="showReason(row)">查看理由</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 驳回弹窗：填写理由 → 提交后自动通知学生 -->
    <el-dialog v-model="rejectVisible" title="驳回申请" width="460px">
      <el-form label-position="top">
        <el-form-item label="申请学生">
          <span>{{ current?.studentName }}（{{ current?.courseName }}）</span>
        </el-form-item>
        <el-form-item label="驳回理由" required>
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="3"
            placeholder="请填写驳回理由，学生将收到该理由通知"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 学生信息弹窗：点击学生姓名查看 -->
    <el-dialog v-model="studentVisible" title="学生信息" width="720px" destroy-on-close>
      <div v-loading="studentLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="学生姓名">{{ studentInfo.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ studentInfo.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ studentInfo.className || '-' }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">
            <el-tag size="small" :type="studentInfo.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ studentInfo.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="手机号">{{ studentInfo.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ studentInfo.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="已加入课程数">{{ studentInfo.joinedCourseCount ?? 0 }} 门</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatTime(studentInfo.registerTime) || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-title">该学生在您任教课程下的申请记录</div>
        <el-table :data="studentInfo.applies || []" border stripe size="small" max-height="260"
          empty-text="暂无申请记录" class="kp-table">
          <el-table-column prop="courseName" label="申请课程" min-width="130" show-overflow-tooltip />
          <el-table-column label="类型" width="70" align="center">
            <template #default="{ row }">{{ row.applyType === 1 ? '退出' : '加入' }}</template>
          </el-table-column>
          <el-table-column prop="applyTime" label="申请时间" width="160" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="驳回理由" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="studentVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Check, Clock, CircleCheck } from '@element-plus/icons-vue'
import { getMyCourses } from '@/api/myCourse'
import {
  getCourseApplyList,
  getCourseApplyStats,
  getCourseApplyStudentDetail,
  approveCourseApply,
  batchApproveCourseApply,
  rejectCourseApply,
  exportCourseApplies
} from '@/api/courseApply'

const route = useRoute()
const router = useRouter()

// 状态筛选：支持从布局的「课程申请提醒」弹窗跳转过来时带 ?status=0，直接落到待审核
const statusParam = route.query.status
const status = ref(statusParam === undefined || statusParam === '' ? null : Number(statusParam))
const rawList = ref([])
const loading = ref(false)
const stats = ref({ pendingCount: 0, handledThisMonth: 0 })

// 课程下拉（当前教师任教的课程），支持从「我的课程」跳转过来时带 ?courseId=xx
const courseOptions = ref([])
const filterCourseId = ref(Number(route.query.courseId) || null)
const list = computed(() => filterCourseId.value
  ? rawList.value.filter(r => Number(r.courseId) === filterCourseId.value)
  : rawList.value)

// 空状态文案：按当前筛选条件给出更贴合的提示
const emptyText = computed(() => {
  if (filterCourseId.value) {
    return '该课程暂无课程加入申请'
  }
  if (status.value === 0) {
    return '暂无课程加入申请，学生提交申请后会出现在这里'
  }
  if (status.value === 1) {
    return '暂无已通过的申请记录'
  }
  if (status.value === 2) {
    return '暂无已驳回的申请记录'
  }
  return '暂无课程加入申请'
})

// 表格选中项（只有待审核记录可勾选）
const tableRef = ref(null)
const selection = ref([])
const batchRows = computed(() => selection.value.filter(r => r.status === 0))

function onSelectionChange(rows) {
  selection.value = rows || []
}

/** 课程筛选变化时同步到地址栏，刷新后仍保持筛选 */
function syncQuery() {
  const query = {}
  if (filterCourseId.value) query.courseId = filterCourseId.value
  if (status.value !== null) query.status = status.value
  router.replace({ path: route.path, query })
}

function handleQuery() {
  syncQuery()
  loadList()
}

function handleReset() {
  status.value = null
  filterCourseId.value = null
  router.replace({ path: route.path, query: {} })
  loadList()
}

/** 点统计卡「待审核」：在「只看待审核」与「全部申请」间切换 */
function togglePendingFilter() {
  status.value = status.value === 0 ? null : 0
  handleQuery()
}

/** 点击申请课程名 → 跳转「我的课程」并自动打开该课程学生名单 */
function goMyCourse(row) {
  router.push({ path: '/my-courses', query: { courseId: row.courseId } })
}

const rejectVisible = ref(false)
const rejectReason = ref('')
const current = ref(null)
const submitting = ref(false)

const studentVisible = ref(false)
const studentLoading = ref(false)
const studentInfo = ref({})

function statusText(s) {
  return s === 0 ? '待审核' : s === 1 ? '已通过' : '已驳回'
}

/** 状态配色：待审核蓝、已通过绿、已驳回红 */
function statusTagType(s) {
  return s === 0 ? 'primary' : s === 1 ? 'success' : 'danger'
}

/** 已处理记录的悬停说明：带上审核时间，便于追溯 */
function handleHint(row) {
  const time = row.auditTime ? `审核时间：${row.auditTime}` : '已审核'
  return `${statusText(row.status)}｜${time}`
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadList() {
  loading.value = true
  try {
    const res = await getCourseApplyList(status.value)
    rawList.value = res.data || []
  } catch {
    rawList.value = []
  } finally {
    loading.value = false
    selection.value = []
    tableRef.value?.clearSelection()
  }
}

async function loadStats() {
  try {
    const res = await getCourseApplyStats()
    stats.value = { pendingCount: 0, handledThisMonth: 0, ...(res.data || {}) }
  } catch {
    // 统计失败不影响列表
  }
}

async function loadCourses() {
  try {
    const res = await getMyCourses()
    courseOptions.value = res.data || []
  } catch {
    courseOptions.value = []
  }
}

/** 列表与统计一起刷新 */
function refresh() {
  loadList()
  loadStats()
}

async function handleApprove(row) {
  try {
    const res = await approveCourseApply(row.id)
    const isQuit = row.applyType === 1
    ElMessage.success(res.msg || (isQuit ? '已允许退课，学生已退出课程' : '已同意，学生成功加入课程'))
    refresh()
  } catch {
    // 拦截器已弹错误提示
  }
}

/** 批量通过：确认后逐条执行，后端会跳过已被处理的记录 */
async function handleBatchApprove() {
  const rows = batchRows.value
  if (!rows.length) return
  const names = rows.slice(0, 3).map(r => `${r.studentName}（${r.courseName}）`).join('、')
  const more = rows.length > 3 ? ` 等 ${rows.length} 位学生` : ''
  try {
    await ElMessageBox.confirm(
      `确认批量通过 ${rows.length} 条申请？\n${names}${more}\n\n通过后学生立即加入对应课程并收到通知。`,
      '批量通过确认',
      { type: 'success', confirmButtonText: '批量通过' }
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    const res = await batchApproveCourseApply(rows.map(r => r.id))
    ElMessage.success(res.msg || `已批量通过 ${rows.length} 条申请`)
    refresh()
  } catch {
    // 拦截器已弹错误提示
  } finally {
    submitting.value = false
  }
}

function openReject(row) {
  current.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由')
    return
  }
  submitting.value = true
  try {
    const res = await rejectCourseApply(current.value.id, rejectReason.value)
    ElMessage.success(res.msg || '已驳回，已通知学生')
    rejectVisible.value = false
    refresh()
  } catch {
    // 拦截器已弹错误提示
  } finally {
    submitting.value = false
  }
}

/** 查看驳回理由（已处理记录） */
function showReason(row) {
  ElMessageBox.alert(row.rejectReason || '无', `驳回理由｜${row.studentName}（${row.courseName}）`, {
    confirmButtonText: '知道了'
  })
}

/** 查看学生基础信息 + 该生在本教师任教课程下的申请记录 */
async function openStudent(row) {
  studentInfo.value = {}
  studentVisible.value = true
  studentLoading.value = true
  try {
    const res = await getCourseApplyStudentDetail(row.studentId)
    studentInfo.value = res.data || {}
  } catch {
    // 拦截器已弹错误提示
  } finally {
    studentLoading.value = false
  }
}

/** 导出审核记录（Excel），沿用当前筛选条件 */
async function handleExport() {
  try {
    const res = await exportCourseApplies({
      status: status.value ?? undefined,
      courseId: filterCourseId.value || undefined
    })
    saveBlob(res.data || res, `课程申请审核记录_${Date.now()}.xlsx`)
    ElMessage.success('导出成功，请查看浏览器下载')
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || 'download'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

onMounted(() => {
  loadCourses()
  loadList()
  loadStats()
})
</script>

<style scoped>
.course-apply-page {
  padding: 4px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.page-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}
.page-sub {
  font-size: 12px;
  color: #909399;
}

/* ===== 统计卡片 ===== */
.stat-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 190px;
  padding: 12px 18px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  transition: all .2s;
}
.stat-card.stat-pending {
  cursor: pointer;
}
.stat-card.stat-pending:hover {
  border-color: #93c5fd;
}
.stat-card.stat-pending.active {
  border-color: #3b82f6;
  background: #eff6ff;
}
.stat-icon {
  font-size: 24px;
}
.stat-pending .stat-icon {
  color: #f59e0b;
}
.stat-handled .stat-icon {
  color: #10b981;
}
.stat-num {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.15;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}

.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.batch-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.batch-tip {
  font-size: 12px;
  color: #909399;
}
.muted {
  color: #c0c4cc;
  font-size: 12px;
  margin-right: 6px;
}
.detail-title {
  margin: 14px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  border-left: 3px solid #3b82f6;
  padding-left: 8px;
}
/* 表头与内容统一居中 */
.kp-table :deep(.el-table__header th.el-table__cell),
.kp-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}
/* 操作列按钮居中：theme.css 为了让多个按钮挤在同一行，把表格最后一列的 .cell
   设成了 display:flex，flex 容器下 td 的 text-align:center 对子项不再生效
   （子项默认从容器左侧开始排列），所以这里显式补 justify-content:center */
.kp-table :deep(.el-table__body td:last-child .cell) {
  justify-content: center;
}
/* 窄屏：说明文字换行，标题与导出按钮不再抢同一行 */
@media (max-width: 900px) {
  .page-header {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .page-title-row {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
