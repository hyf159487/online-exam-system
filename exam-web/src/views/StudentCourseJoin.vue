<template>
  <div class="course-join-page">
    <div class="page-header">
      <div class="page-title-row">
        <h2 class="page-title">加入课程</h2>
        <span class="page-sub">浏览开放课程，提交选课申请后由任课教师审核；审核通过后可查看该课程的学习资料。</span>
      </div>
    </div>

    <el-card shadow="never">
      <!-- 筛选区：课程名称 / 授课教师 / 课程类型 / 加入状态 -->
      <div class="filter-bar">
        <!-- 课程名称：聚焦展示全部课程名下拉候选，也可直接输入关键字做模糊查询 -->
        <el-autocomplete
          v-model="query.courseName"
          :fetch-suggestions="fetchCourseNameSuggestions"
          value-key="value"
          clearable
          placeholder="课程名称，可输入关键字"
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
          @select="handleSearch"
        />
        <!-- 授课教师：聚焦展示实际任课教师下拉候选，也可输入姓名关键字模糊查询 -->
        <el-autocomplete
          v-model="query.teacherName"
          :fetch-suggestions="fetchTeacherNameSuggestions"
          value-key="value"
          clearable
          placeholder="授课教师，可输入关键字"
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
          @select="handleSearch"
        />
        <!-- 课程类型：选项取自课程表中已有类型（支持管理端自由新增的类型） -->
        <el-select v-model="query.courseType" placeholder="课程类型" clearable style="width: 160px"
          @change="handleSearch" @clear="query.courseType = ''; handleSearch()">
          <el-option v-for="type in courseTypeOptions" :key="type" :label="type" :value="type" />
        </el-select>
        <el-select v-model="query.statusFilter" placeholder="加入状态" clearable style="width: 140px"
          @change="handleSearch" @clear="query.statusFilter = ''; handleSearch()">
          <el-option label="已加入" value="joined" />
          <el-option label="未加入" value="notJoined" />
          <el-option label="审核中" value="pending" />
        </el-select>
        <el-button type="primary" plain @click="handleSearch">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <!-- 课程卡片视图：封面 + 课程名 + 授课教师 + 简介 + 参加人数 + 状态与操作 -->
      <div v-loading="loading" class="course-grid-wrap">
        <div v-if="list.length" class="course-card-grid">
          <!-- 整张卡片可点击进入课程详情页；卡片内按钮用 .stop 阻止冒泡，避免误跳 -->
          <div v-for="row in list" :key="row.courseId" class="course-card course-card--clickable"
            @click="goDetail(row)">
            <!-- 封面：由「课程管理」上传；未设置时用渐变底色 + 课程名首字占位 -->
            <div class="course-card__cover">
              <img v-if="row.cover" :src="row.cover" :alt="row.courseName" />
              <span v-else class="course-card__cover-text">{{ (row.courseName || '课').slice(0, 1) }}</span>
              <span class="course-card__badge" :class="badgeClass(row)">{{ statusText(row) }}</span>
            </div>

            <div class="course-card__body">
              <h3 class="course-card__name" :title="row.courseName">{{ row.courseName }}</h3>

              <div class="course-card__teacher">
                <span class="course-card__teacher-label">授课教师</span>
                <span class="course-card__teacher-names" :title="row.teacherNames || ''">
                  {{ row.teacherNames || '暂未安排' }}
                </span>
              </div>

              <!-- 简介固定两行，超出省略，悬停可看完整内容 -->
              <p class="course-card__desc" :title="row.description || ''">
                {{ row.description || '该课程暂无简介' }}
              </p>

              <div class="course-card__meta">
                <span>参加人数 <b>{{ row.studentCount || 0 }}</b> 人</span>
                <span v-if="row.startTime">开课 {{ formatTimeShort(row.startTime) }}</span>
                <span v-else>长期开放</span>
              </div>

              <div class="course-card__ops">
                <el-button v-if="row.canApply" type="primary" size="small" @click.stop="handleApply(row)">申请加入</el-button>
                <el-button v-else-if="row.inPending" type="warning" size="small" plain disabled>审核中</el-button>
                <el-button v-else-if="row.joined" type="success" size="small" plain disabled>已加入</el-button>
                <el-button v-else type="info" size="small" plain disabled>报名截止</el-button>

                <el-button v-if="row.canQuit" size="small" @click.stop="handleQuit(row)">退出课程</el-button>
                <el-button v-else-if="row.quitPending" size="small" plain disabled>退课审核中</el-button>
                <!-- 查看详情：进入课程详情页（课程介绍 / 课程资料 / 课程公告 / 课程作业 / 课程考试）-->
                <el-button size="small" plain @click.stop="goDetail(row)">查看详情</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态：按当前筛选条件给出对应的引导 -->
        <div v-else-if="!loading" class="course-empty">
          <!-- 筛选「已加入」却没有数据：引导去申请加入 -->
          <el-empty v-if="query.statusFilter === 'joined'" :image-size="110"
            description="您暂时还没有加入任何课程">
            <div class="empty-guide">
              <p class="empty-guide__tip">加入课程后即可查看该课程的学习资料、参加课程考试。是否现在前往加入课程？</p>
              <el-button type="primary" @click="goApplyCourses">前往加入课程</el-button>
            </div>
          </el-empty>

          <!-- 筛选「审核中」却没有数据 -->
          <el-empty v-else-if="query.statusFilter === 'pending'" :image-size="110"
            description="您暂时没有审核中的课程申请">
            <el-button type="primary" plain @click="goApplyCourses">查看可加入的课程</el-button>
          </el-empty>

          <!-- 筛选「未加入」却没有数据：可能都已加入或已截止报名 -->
          <el-empty v-else-if="query.statusFilter === 'notJoined'" :image-size="110"
            description="当前没有可申请加入的课程">
            <el-button @click="reset">重置筛选</el-button>
          </el-empty>

          <!-- 无筛选条件：确实没有开放报名的课程 -->
          <el-empty v-else :image-size="110" description="暂无开放报名的课程" />
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[8, 16, 24, 32]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="loadList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getStudentOpenCourses,
  applyCourse,
  quitCourse,
  getStudentCourseTypes,
  getStudentCourseFilterOptions
} from '@/api/studentCourse'

const router = useRouter()

// 「加入课程」列表状态缓存键：进入课程详情页再返回时，恢复原来的筛选条件与页码
const LIST_STATE_KEY = 'student-course-join-list-state'

const query = reactive({
  courseName: '',
  teacherName: '',
  courseType: '',
  statusFilter: '',
  pageNum: 1,
  // 卡片视图：每页 8 门（4 列 × 2 行，整屏一屏两行不出现半行卡片）
  pageSize: 8
})
const list = ref([])
const total = ref(0)
const loading = ref(false)
// 课程类型下拉选项（取课程表中已有类型，兼容管理端自由新增的类型）
const courseTypeOptions = ref([])
// 课程名称 / 授课教师下拉候选（去重列表，供筛选框下拉选择 + 输入关键字模糊查询）
const courseNameOptions = ref([])
const teacherNameOptions = ref([])

// 开课时间：只显示到分钟，卡片底部一行放得下
function formatTimeShort(t) {
  if (!t) return null
  return String(t).replace('T', ' ').slice(0, 16)
}

function statusText(row) {
  if (row.joined) return '已加入'
  if (row.inPending) return '审核中'
  if (row.applyClosed) return '报名截止'
  return '开放报名'
}

/** 封面上角状态角标的配色（与 statusText 一一对应） */
function badgeClass(row) {
  if (row.joined) return 'badge--joined'
  if (row.inPending) return 'badge--pending'
  if (row.applyClosed) return 'badge--closed'
  return 'badge--open'
}

async function loadList() {
  loading.value = true
  try {
    const res = await getStudentOpenCourses({
      statusFilter: query.statusFilter,
      courseName: query.courseName.trim(),
      teacherName: query.teacherName.trim(),
      courseType: query.courseType,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleSizeChange() {
  query.pageNum = 1
  loadList()
}

// 加载课程类型下拉选项（失败时留空，不影响课程列表本身）
async function loadCourseTypes() {
  try {
    const res = await getStudentCourseTypes()
    courseTypeOptions.value = res.data || []
  } catch {
    courseTypeOptions.value = []
  }
}

async function handleApply(row) {
  try {
    await ElMessageBox.confirm(`确认要申请加入《${row.courseName}》课程吗？`, '申请加入', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  try {
    const res = await applyCourse(row.courseId)
    ElMessage.success(res.msg || '申请已提交，请等待教师审核')
    loadList()
  } catch {
    // 拦截器已弹错误提示
  }
}

async function handleQuit(row) {
  try {
    await ElMessageBox.confirm(`确认要申请退出《${row.courseName}》课程吗？`, '退出课程', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  try {
    const res = await quitCourse(row.courseId)
    ElMessage.success(res.msg || '退出申请已提交，请等待教师审核')
    loadList()
  } catch {
    // 拦截器已弹错误提示
  }
}

/**
 * 进入课程详情页（课程介绍 / 课程资料 / 课程公告 / 课程作业 / 课程考试）。
 * 跳转前缓存当前筛选条件与页码，从详情页返回时仍停留在原来的浏览位置。
 */
function goDetail(row) {
  saveListState()
  router.push({ name: 'StudentCourseDetail', params: { courseId: row.courseId } })
}

function saveListState() {
  try {
    sessionStorage.setItem(LIST_STATE_KEY, JSON.stringify({ ...query }))
  } catch {
    // 隐私模式等场景写入失败不影响跳转
  }
}

/** 详情页返回时恢复筛选条件与页码，保证「返回后仍是加入课程的页面」 */
function restoreListState() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(LIST_STATE_KEY) || 'null')
    if (saved) Object.assign(query, saved)
  } catch {
    // 缓存损坏时按默认条件展示
  }
}

function reset() {
  query.courseName = ''
  query.teacherName = ''
  query.courseType = ''
  query.statusFilter = ''
  query.pageNum = 1
  loadList()
}

/**
 * 空状态引导：切到「未加入」筛选，直接展示可以申请加入的课程。
 * 仅切换加入状态，保留学生已填的课程名称/教师/类型条件，避免丢失用户输入。
 */
function goApplyCourses() {
  query.statusFilter = 'notJoined'
  handleSearch()
}

onMounted(() => {
  // 从课程详情页返回时：先恢复上次的筛选条件与页码，再按该条件加载列表
  restoreListState()
  loadCourseTypes()
  loadFilterOptions()
  loadList()
})

// 加载「课程名称 / 授课教师」下拉候选（失败时留空，仍可手动输入关键字查询）
async function loadFilterOptions() {
  try {
    const res = await getStudentCourseFilterOptions()
    courseNameOptions.value = res.data?.courseNames || []
    teacherNameOptions.value = res.data?.teacherNames || []
  } catch {
    courseNameOptions.value = []
    teacherNameOptions.value = []
  }
}

/**
 * 下拉候选本地过滤：关键字为空时展示全部候选，否则按包含匹配（不区分大小写），最多 50 条。
 * 候选中没有合适项时可直接输入关键字并点「查询」，由后端做模糊匹配。
 */
function buildSuggestions(source, keyword) {
  const kw = (keyword || '').trim().toLowerCase()
  const matched = kw ? source.filter((name) => name.toLowerCase().includes(kw)) : source
  return matched.slice(0, 50).map((name) => ({ value: name }))
}

function fetchCourseNameSuggestions(keyword, cb) {
  cb(buildSuggestions(courseNameOptions.value, keyword))
}

function fetchTeacherNameSuggestions(keyword, cb) {
  cb(buildSuggestions(teacherNameOptions.value, keyword))
}
</script>

<style scoped>
.course-join-page {
  padding: 4px;
}
/* 页面留白整体收窄：标题区 / 筛选区 / 分页间距调小，
   让「4 列 × 2 行 = 8 门」整屏一屏可见，不必上下滚动 */
.page-header {
  margin-bottom: 8px;
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
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
  align-items: center;
}

/* 卡片区上下留白收窄（el-card 默认 20px），为整屏放下两行卡片挤出空间 */
:deep(.el-card__body) {
  padding: 12px 16px;
}

/* ============ 课程卡片网格 ============ */
.course-grid-wrap {
  min-height: 200px;
}
/* 网格列数由 theme.css 的 .course-card-grid 控制（每行 4 门，与每页 8 条对齐）；
   这里只把行列间距 16px 收到 10px，配合更矮的卡片让两行稳定在一屏内 */
.course-card-grid {
  gap: 10px;
}
.course-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  transition: box-shadow 0.2s, transform 0.2s;
}
.course-card:hover {
  box-shadow: 0 8px 20px rgba(31, 41, 55, 0.1);
  transform: translateY(-2px);
}
/* 封面：横幅比例，无封面时用渐变底 + 首字占位
   高度由 122px → 104px → 88px，是压低单张卡片总高的主要来源 */
.course-card__cover {
  position: relative;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, #409eff, #79bbff);
}
.course-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.course-card__cover-text {
  font-size: 26px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
  letter-spacing: 2px;
}
.course-card__badge {
  position: absolute;
  top: 6px;
  right: 6px;
  padding: 0 7px;
  border-radius: 9px;
  font-size: 12px;
  line-height: 17px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
}
.badge--joined {
  background: rgba(103, 194, 58, 0.92);
}
.badge--pending {
  background: rgba(230, 162, 60, 0.92);
}
.badge--closed {
  background: rgba(144, 147, 153, 0.92);
}
.badge--open {
  background: rgba(64, 158, 255, 0.92);
}
/* 卡片内容区：内边距与行间距各收紧 2px，单卡累计省下约 12px */
.course-card__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
}
.course-card__name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-card__teacher {
  display: flex;
  gap: 6px;
  font-size: 12px;
  color: #606266;
}
.course-card__teacher-label {
  flex: none;
  color: #909399;
}
.course-card__teacher-names {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 简介固定 1 行（超出省略，悬停 title 看全文），高度固定不跳动 */
.course-card__desc {
  margin: 0;
  min-height: 18px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.course-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #909399;
}
.course-card__meta b {
  color: #409eff;
}
.course-card__ops {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;
  padding-top: 2px;
}
/* 用 gap 控制间距，去掉 Element Plus 相邻按钮的默认左外边距，避免卡片换行错位 */
.course-card__ops :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 整卡可点击：鼠标手型 + 轻微上浮，提示「点卡片进详情」 */
.course-card--clickable {
  cursor: pointer;
}
.course-card--clickable:active {
  transform: translateY(0);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
/* 空状态引导文案 + 按钮 */
.empty-guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.empty-guide__tip {
  margin: 0;
  max-width: 420px;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
}
</style>
