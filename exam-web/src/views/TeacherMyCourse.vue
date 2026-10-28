<template>
  <div class="my-course-page">
    <!-- 课程加入申请提醒：放在页面最顶部，进入页面第一时间就能看到，不再挤在筛选行右侧。
         不弹窗、不打断操作，有申请就常驻显示；点「前往查看」直达审核页（已筛待审核）。
         「×」只是本次收起，一旦出现新的申请会再次出现。 -->
    <div v-if="applyTip && !tipClosed" class="apply-tip">
      <el-icon class="apply-tip__icon"><BellFilled /></el-icon>
      <span class="apply-tip__text">{{ applyTip }}</span>
      <el-button type="primary" size="small" class="apply-tip__btn" @click="goApplyAudit">
        前往查看
      </el-button>
      <el-icon class="apply-tip__close" title="收起提示" @click="tipClosed = true"><Close /></el-icon>
    </div>

    <div class="page-header">
      <div class="page-title-row">
        <h2 class="page-title">我的课程</h2>
        <span class="page-sub">只展示您任教（被绑定为任课教师）的课程；点击课程卡片可查看并修改课程信息，也可查看学生名单、跳转资料管理与申请审核。</span>
      </div>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <!-- 筛选条件统一为「文字标签：输入框」形式：查什么写在 label 上，输入框内只留简短占位 -->
        <el-form :inline="true" class="filter-form" label-suffix="：" @submit.prevent>
          <!-- 课程名称/编码：下拉候选来自我任教的课程，也可直接输入关键字做模糊查询 -->
          <el-form-item label="课程名称/编码">
            <el-autocomplete
              v-model="keyword"
              :fetch-suggestions="fetchCourseSuggestions"
              value-key="value"
              clearable
              placeholder="请输入关键字"
              style="width: 200px"
              @keyup.enter="handleQuery"
              @clear="handleQuery"
              @select="handleQuery"
            >
              <template #default="{ item }">
                <span class="sug-name">{{ item.value }}</span>
                <span v-if="item.code" class="sug-code">{{ item.code }}</span>
              </template>
            </el-autocomplete>
          </el-form-item>
          <!-- 课程类型：下拉候选取我任教课程里出现过的类型，也可输入关键字做模糊查询 -->
          <el-form-item label="课程类型">
            <el-autocomplete
              v-model="courseType"
              :fetch-suggestions="fetchTypeSuggestions"
              value-key="value"
              clearable
              placeholder="请输入关键字"
              style="width: 170px"
              @keyup.enter="handleQuery"
              @clear="handleQuery"
              @select="handleQuery"
            />
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" plain @click="handleQuery">查询</el-button>
            <el-button plain @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-tag type="info" effect="plain">共 {{ list.length }} 门课程</el-tag>
      </div>

      <!-- 课程卡片视图：封面 + 课程名 + 课程编码 + 简介 + 创建时间/学生人数 + 操作 -->
      <div v-loading="loading" class="course-grid-wrap">
        <div v-if="pagedList.length" class="course-card-grid">
          <CourseCard
            v-for="row in pagedList"
            :key="row.id"
            :course="row"
            :badge="row.courseType || '未分类'"
            badge-type="open"
            info-label="课程编码"
            :info-value="row.courseCode || '未设置'"
            clickable
            @click="goInfo(row)"
          >
            <template #meta>
              <span :title="formatTime(row.createTime)">{{ formatDate(row.createTime) || '-' }}</span>
              <span>学生人数 <b>{{ row.studentCount || 0 }}</b> 人</span>
            </template>

            <template #ops>
              <el-button size="small" type="primary" plain @click="openStudents(row)">查看学生</el-button>
              <el-button size="small" type="primary" plain @click="goResources(row)">资料管理</el-button>
              <el-button size="small" type="warning" plain @click="goApplies(row)">申请审核</el-button>
              <!-- 课程公告：发布后学生端课程详情页「课程公告」Tab 立即可见 -->
              <el-button size="small" plain @click="openNotices(row)">课程公告</el-button>
            </template>
          </CourseCard>
        </div>

        <el-empty
          v-else-if="!loading"
          description="暂无任课课程，请联系管理员在「课程列表」中为您绑定课程"
        />
      </div>

      <!-- 分页（每页 8 门，与卡片 4 列 × 2 行对齐） -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[8, 16, 24, 32]"
          :total="list.length"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="pageNum = 1"
        />
      </div>
    </el-card>

    <!-- 学生名单弹窗 -->
    <el-dialog
      v-model="studentsVisible"
      :title="currentCourse ? `《${currentCourse.courseName}》学生名单` : '学生名单'"
      width="820px"
      destroy-on-close
    >
      <div class="dialog-toolbar">
        <el-input
          v-model="studentKeyword"
          placeholder="学生姓名 / 学号搜索"
          clearable
          style="width: 220px"
          @keyup.enter="loadStudents"
          @clear="loadStudents"
        />
        <el-button type="primary" plain @click="loadStudents">查询</el-button>
        <el-tag type="info" effect="plain">共 {{ students.length }} 人</el-tag>
      </div>

      <el-table
        :data="students"
        v-loading="studentsLoading"
        border
        stripe
        max-height="380"
        empty-text="暂无学生加入该课程"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="realName" label="学生姓名" width="120" align="center">
          <template #default="{ row }">{{ row.realName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="学号" width="150" align="center" />
        <el-table-column prop="className" label="班级" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.className || '-' }}</template>
        </el-table-column>
        <el-table-column prop="joinTime" label="加入时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.joinTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemove(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="studentsVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 课程公告管理弹窗：发布 / 编辑 / 删除。
         发布后学生端「课程详情 → 课程公告」Tab 立即可见，
         同时后端会向该课程已加入学生推送一条系统通知（学生端「通知公告」同步可见）。 -->
    <el-dialog
      v-model="noticeVisible"
      :title="currentCourse ? `《${currentCourse.courseName}》课程公告` : '课程公告'"
      width="760px"
      destroy-on-close
    >
      <el-form :model="noticeForm" label-width="60px" class="notice-form" @submit.prevent>
        <el-form-item label="标题">
          <el-input v-model="noticeForm.title" maxlength="128" show-word-limit
            placeholder="一句话说明公告主题，例如「第 3 次作业提交时间调整」" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="noticeForm.content" type="textarea" :rows="3" maxlength="1000" show-word-limit
            placeholder="公告正文：作业要求、上课安排、考试提醒等" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="noticeSaving" @click="submitNotice">
            {{ noticeForm.id ? '保存修改' : '发布公告' }}
          </el-button>
          <el-button v-if="noticeForm.id" @click="resetNoticeForm">取消编辑</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">已发布公告（{{ notices.length }} 条）</el-divider>

      <el-table v-loading="noticeLoading" :data="notices" border stripe max-height="300"
        empty-text="该课程暂无公告">
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column label="发布时间" width="170" align="center">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="editNotice(row)">编辑</el-button>
            <el-button link type="danger" @click="removeNotice(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="noticeVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BellFilled, Close } from '@element-plus/icons-vue'
import { getMyCourses, getMyCourseStudents, removeMyCourseStudent } from '@/api/myCourse'
import { getCourseApplyList } from '@/api/courseApply'
// 课程公告：学生端「课程详情 → 课程公告」Tab 的发布/维护入口
import { createCourseNotice, deleteCourseNotice, getCourseNoticeList, updateCourseNotice } from '@/api/course'
// 课程卡片与「学生端加入课程」「管理端课程列表」共用同一套视觉
import CourseCard from '@/components/CourseCard.vue'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const courseType = ref('')
const list = ref([])
// 全量任教课程（下拉候选数据源）：只加载一次，不随筛选变化，供两个筛选框做候选
const allCourses = ref([])
const loading = ref(false)

// 分页：接口返回全量数据，前端切片展示；卡片视图每页 8 门（4 列 × 2 行）
const pageNum = ref(1)
const pageSize = ref(8)
const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

const studentsVisible = ref(false)
const studentsLoading = ref(false)
const studentKeyword = ref('')
const students = ref([])
const currentCourse = ref(null)

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

/** 卡片底部只展示到日期：一行放得下，完整时间悬停可见 */
function formatDate(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 10)
}

async function loadList() {
  loading.value = true
  try {
    const res = await getMyCourses({ keyword: keyword.value, courseType: courseType.value })
    list.value = res.data || []
    // 当前页超出总页数（筛选/移除学生后）时回退到最后一页
    const maxPage = Math.max(1, Math.ceil(list.value.length / pageSize.value))
    if (pageNum.value > maxPage) pageNum.value = maxPage
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

/** 加载全量任教课程：作为两个筛选框的下拉候选数据源（失败静默，不影响列表查询） */
async function loadAllCourses() {
  try {
    const res = await getMyCourses()
    allCourses.value = res.data || []
  } catch {
    allCourses.value = []
  }
}

/** 课程类型候选：取我任教课程中出现过的类型（管理端可自由新增类型，故不写死枚举） */
const courseTypeOptions = computed(() => {
  const set = new Set()
  allCourses.value.forEach(c => {
    if (c.courseType) set.add(c.courseType)
  })
  return [...set]
})

/** 课程名称/编码候选：按输入关键字过滤；选中后填入课程名（后端按名称/编码模糊匹配） */
function fetchCourseSuggestions(query, cb) {
  const q = (query || '').trim().toLowerCase()
  cb(allCourses.value
    .filter(c => !q
      || String(c.courseName || '').toLowerCase().includes(q)
      || String(c.courseCode || '').toLowerCase().includes(q))
    .slice(0, 50)
    .map(c => ({ value: c.courseName, code: c.courseCode || '' })))
}

/** 课程类型候选：按输入关键字过滤；也可不选候选，直接输入关键字做模糊查询 */
function fetchTypeSuggestions(query, cb) {
  const q = (query || '').trim().toLowerCase()
  cb(courseTypeOptions.value
    .filter(t => !q || t.toLowerCase().includes(q))
    .map(t => ({ value: t })))
}

function handleQuery() {
  pageNum.value = 1
  loadList()
}

function handleReset() {
  keyword.value = ''
  courseType.value = ''
  pageNum.value = 1
  loadList()
}

/* ========== 课程信息页（点击课程卡片进入，可查看并修改课程信息） ==========
 * 列表浏览状态缓存：从课程信息页返回后仍保持原来的筛选条件与页码；
 * 只写 sessionStorage（关闭窗口即失效），不影响其它页面。
 */
const LIST_STATE_KEY = 'teacherMyCourse:state'

function saveListState() {
  try {
    sessionStorage.setItem(LIST_STATE_KEY, JSON.stringify({
      keyword: keyword.value,
      courseType: courseType.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }))
  } catch {
    // 隐私模式等场景写入失败不影响跳转
  }
}

function restoreListState() {
  let saved = null
  try {
    saved = JSON.parse(sessionStorage.getItem(LIST_STATE_KEY) || 'null')
  } catch {
    // 缓存损坏时按默认条件展示
  }
  if (!saved) return
  keyword.value = saved.keyword || ''
  courseType.value = saved.courseType || ''
  pageSize.value = Number(saved.pageSize) || 8
  pageNum.value = Number(saved.pageNum) || 1
}

/** 进入课程信息页：先缓存列表浏览状态，返回时可恢复 */
function goInfo(row) {
  saveListState()
  router.push({ name: 'TeacherCourseInfo', params: { courseId: row.id } })
}

/** 打开某门课的学生名单 */
async function openStudents(row) {
  currentCourse.value = row
  studentKeyword.value = ''
  students.value = []
  studentsVisible.value = true
  await loadStudents()
}

async function loadStudents() {
  if (!currentCourse.value) return
  studentsLoading.value = true
  try {
    const res = await getMyCourseStudents(currentCourse.value.id, studentKeyword.value)
    students.value = res.data || []
  } catch {
    students.value = []
  } finally {
    studentsLoading.value = false
  }
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.realName || row.username}」移出课程《${currentCourse.value.courseName}》？移除后该学生将无法查看这门课的学习资料。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const res = await removeMyCourseStudent(currentCourse.value.id, row.studentId)
    ElMessage.success(res.msg || '已将该学生移出课程')
    loadStudents()
    loadList() // 学生人数同步刷新
  } catch {
    // 拦截器已弹错误提示
  }
}

/** 资料管理：带上 courseId，落地页自动按该课程筛选 */
function goResources(row) {
  router.push({ path: '/study-resources', query: { courseId: row.id } })
}

/** 申请审核：带上 courseId，审核页自动按该课程过滤 */
function goApplies(row) {
  router.push({ path: '/course/applies', query: { courseId: row.id } })
}

/* ========== 课程公告管理 ==========
 * 公告按课程维度组织：发布后学生端「课程详情 → 课程公告」Tab 立即可见，
 * 同时后端会向该课程已加入学生推送一条系统通知（未加入学生看不到公告内容）。
 */
const noticeVisible = ref(false)
const noticeLoading = ref(false)
const noticeSaving = ref(false)
const notices = ref([])
const noticeForm = reactive({ id: null, title: '', content: '' })

/** 打开某门课的公告管理弹窗 */
function openNotices(row) {
  currentCourse.value = row
  notices.value = []
  resetNoticeForm()
  noticeVisible.value = true
  loadNotices()
}

function resetNoticeForm() {
  noticeForm.id = null
  noticeForm.title = ''
  noticeForm.content = ''
}

async function loadNotices() {
  if (!currentCourse.value) return
  noticeLoading.value = true
  try {
    const res = await getCourseNoticeList(currentCourse.value.id)
    notices.value = res.data || []
  } catch {
    notices.value = []
  } finally {
    noticeLoading.value = false
  }
}

/** 发布 / 保存公告（标题与内容都必填，后端也会二次校验） */
async function submitNotice() {
  if (!noticeForm.title.trim()) {
    ElMessage.warning('请填写公告标题')
    return
  }
  if (!noticeForm.content.trim()) {
    ElMessage.warning('请填写公告内容')
    return
  }
  noticeSaving.value = true
  try {
    const title = noticeForm.title.trim()
    const content = noticeForm.content.trim()
    const res = noticeForm.id
      ? await updateCourseNotice(noticeForm.id, { title, content })
      : await createCourseNotice({ courseId: currentCourse.value.id, title, content })
    ElMessage.success(res.msg || (noticeForm.id ? '公告修改成功' : '公告发布成功'))
    resetNoticeForm()
    loadNotices()
  } catch {
    // 拦截器已弹错误提示
  } finally {
    noticeSaving.value = false
  }
}

/** 点「编辑」把该条公告填入上方表单，提交时走修改接口 */
function editNotice(row) {
  noticeForm.id = row.id
  noticeForm.title = row.title || ''
  noticeForm.content = row.content || ''
}

async function removeNotice(row) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？删除后学生端将不再显示。`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await deleteCourseNotice(row.id)
    ElMessage.success(res.msg || '公告已删除')
    if (noticeForm.id === row.id) resetNoticeForm()
    loadNotices()
  } catch {
    // 拦截器已弹错误提示
  }
}

/* ========== 课程加入申请提示条 ==========
 * 不用弹窗（多学生、多课程时弹窗会重复打扰），改为筛选条件右侧的常驻提示：
 * 有待审核申请就显示「有 N 位同学申请加入你的课程啦…」+「前往查看」。
 * 进入页面查一次，之后每 30 秒刷新，数量变化时重新显示（即使刚被手动收起）。
 */
const applyTip = ref('')
const tipClosed = ref(false)
let applyTimer = null

function buildApplyTip(applies) {
  const joins = applies.filter(a => a.applyType !== 1)
  const quits = applies.filter(a => a.applyType === 1)
  // 只说数量、不列课程名：多课程时文案不会变长，进审核页自然能看到是哪几门课
  if (joins.length > 0) {
    return `有 ${joins.length} 位同学申请加入你的课程啦，是否前往查看与审核？`
  }
  if (quits.length > 0) {
    return `有 ${quits.length} 位同学申请退出你的课程，是否前往查看与审核？`
  }
  return ''
}

async function loadApplyTip() {
  try {
    const res = await getCourseApplyList(0)
    const tip = buildApplyTip(Array.isArray(res.data) ? res.data : [])
    // 提示内容变化（有新申请/已处理完）时重新展开，避免用户收起后再也看不到
    if (tip !== applyTip.value) tipClosed.value = false
    applyTip.value = tip
  } catch {
    // 接口异常静默：不影响课程列表本身
  }
}

/** 前往审核页：直接定位到「待审核」筛选 */
function goApplyAudit() {
  router.push({ path: '/course/applies', query: { status: 0 } })
}

onMounted(async () => {
  // 从「课程信息」页返回时恢复筛选条件与页码
  restoreListState()
  // 先取全量任教课程：既填充两个筛选框的下拉候选，也用于下面按 courseId 定位课程
  await loadAllCourses()
  await loadList()
  // 从「申请审核」页课程名跳转过来时，自动打开该课程的学生名单
  const cid = Number(route.query.courseId)
  if (cid) {
    const target = allCourses.value.find(c => Number(c.id) === cid)
    if (target) {
      openStudents(target)
    } else {
      ElMessage.warning('未找到该课程，可能您已不再任教此课程')
    }
  }
  loadApplyTip()
  applyTimer = setInterval(loadApplyTip, 30000)
})

onUnmounted(() => {
  if (applyTimer) {
    clearInterval(applyTimer)
    applyTimer = null
  }
})
</script>

<style scoped>
.my-course-page {
  padding: 4px;
}
/* 收紧卡片内边距，把宽度让给表格 */
.my-course-page :deep(.el-card__body) {
  padding: 14px 14px 12px;
}
.page-header {
  margin-bottom: 16px;
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
  gap: 10px;
  margin-bottom: 14px;
  align-items: center;
  flex-wrap: wrap; /* 窗口窄时筛选项自动换行，不互相挤压 */
}
/* 筛选表单：label 直接写明「查什么」，输入框内不再放长提示。
   间距由 form-item 的右外边距控制（覆盖 Element Plus inline 默认的 32px），
   窄屏时 form-item 自动换行，不会把「共 N 门课程」挤出屏幕 */
.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
.filter-form :deep(.el-form-item) {
  margin: 0 14px 0 0;
}
.filter-form :deep(.el-form-item:last-child) {
  margin-right: 0;
}
.filter-form :deep(.el-form-item__label) {
  padding-right: 6px;
  font-size: 13px;
  color: #606266;
}
/* 下拉候选：课程名 + 灰色课程编码 */
.sug-name {
  margin-right: 8px;
}
.sug-code {
  color: #909399;
  font-size: 12px;
  font-family: Consolas, monospace;
}

/* ===== 课程加入申请提示条（页面顶部整行横幅） ===== */
.apply-tip {
  /* 独占一行占满内容宽度，与下方「我的课程」标题保持固定间距 */
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px 8px 14px;
  border-radius: 8px;
  background: linear-gradient(90deg, #fff7ed 0%, #fef2f2 100%);
  border: 1px solid #fed7aa;
  color: #b45309;
  font-size: 13px;
  line-height: 1.5;
  animation: applyTipIn .28s ease;
}
/* 从顶部滑入，符合「提示出现在页面顶部」的视觉方向 */
@keyframes applyTipIn {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}
.apply-tip__icon {
  color: #f59e0b;
  font-size: 15px;
  flex-shrink: 0;
  animation: applyTipBell 1.6s ease-in-out 3;
}
/* 铃铛轻微摇摆，只播 3 次，避免长时间闪烁分散注意力 */
@keyframes applyTipBell {
  0%, 100% { transform: rotate(0); }
  20% { transform: rotate(14deg); }
  40% { transform: rotate(-12deg); }
  60% { transform: rotate(8deg); }
  80% { transform: rotate(-6deg); }
}
.apply-tip__text {
  white-space: nowrap;
}
.apply-tip__btn {
  flex-shrink: 0;
  /* 横幅变宽后按钮靠右，文案与操作区分明 */
  margin-left: auto;
}
.apply-tip__close {
  cursor: pointer;
  font-size: 13px;
  color: #b45309;
  opacity: .55;
  flex-shrink: 0;
  transition: opacity .2s;
}
.apply-tip__close:hover {
  opacity: 1;
}
/* 窄屏：文案允许折行，按钮与关闭图标仍保持在右侧 */
@media (max-width: 900px) {
  .apply-tip {
    flex-wrap: wrap;
  }
  .apply-tip__text {
    white-space: normal;
  }
}
.dialog-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  align-items: center;
}

/* 课程公告：发布表单与下方已发布列表留一点呼吸感 */
.notice-form {
  margin-bottom: 4px;
}

/* 分页栏 */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: 14px;
}

/* 卡片网格容器：加载中给个最小高度，避免内容区高度抖动 */
.course-grid-wrap {
  min-height: 200px;
}
</style>
