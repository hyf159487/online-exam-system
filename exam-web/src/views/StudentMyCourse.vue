<template>
  <div class="my-course-page">
    <div class="page-header">
      <div class="page-title-row">
        <h2 class="page-title">我的课程</h2>
        <span class="page-sub">我已加入的课程，可查看课程信息并进入课程资料、课程作业。</span>
      </div>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="课程名称 / 编码 / 授课教师"
          clearable
          style="width: 280px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" plain :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        <span class="course-count">共 {{ list.length }} 门已加入课程</span>
      </div>

      <!-- 课程卡片：与「加入课程」「课程管理」共用同一套卡片视觉 -->
      <div v-loading="loading" class="course-grid-wrap">
        <div v-if="pagedList.length" class="course-card-grid">
          <CourseCard
            v-for="row in pagedList"
            :key="row.id"
            :course="row"
            :badge="badgeText(row)"
            :badge-type="isQuitPending(row) ? 'pending' : 'joined'"
          >
            <template #meta>
              <span :title="formatTime(row.joinTime)">加入 {{ formatDate(row.joinTime) || '-' }}</span>
              <span>参加 <b>{{ row.studentCount || 0 }}</b> 人 · 资料 {{ row.resourceCount || 0 }} 份</span>
            </template>

            <template #ops>
              <el-button size="small" type="success" plain @click="openDetail(row)">
                <el-icon><View /></el-icon> 详情
              </el-button>
              <el-button size="small" type="primary" plain @click="goResources">课程资料</el-button>
              <el-button size="small" type="primary" plain @click="goPractice">课程作业</el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :disabled="isQuitPending(row)"
                @click="handleQuit(row)"
              >{{ isQuitPending(row) ? '退出审核中' : '申请退出' }}</el-button>
            </template>
          </CourseCard>
        </div>

        <el-empty v-else-if="!loading" description="还没有加入任何课程，先去「加入课程」申请吧">
          <el-button type="primary" @click="$router.push('/student/course/join')">去加入课程</el-button>
        </el-empty>
      </div>

      <!-- 分页：每页 8 门，与卡片 4 列 × 2 行对齐 -->
      <div v-if="list.length > pageSize" class="pagination-bar">
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

    <!-- 课程详情弹窗 -->
    <el-dialog v-model="detailVisible" title="课程详情" width="520px">
      <div v-if="detailData">
        <!-- 封面（未设置则不占位） -->
        <img v-if="detailData.cover" :src="detailData.cover" class="detail-cover" alt="课程封面" />
        <el-descriptions :column="1" border>
          <el-descriptions-item label="课程名称">{{ detailData.courseName }}</el-descriptions-item>
          <el-descriptions-item label="课程编码">{{ detailData.courseCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="课程类型">{{ detailData.courseType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="授课教师">{{ detailData.teacherNames || '暂未安排' }}</el-descriptions-item>
          <el-descriptions-item label="参加人数">{{ detailData.studentCount || 0 }} 人</el-descriptions-item>
          <el-descriptions-item label="学习资料">{{ detailData.resourceCount || 0 }} 份</el-descriptions-item>
          <el-descriptions-item label="开课时间">{{ formatTime(detailData.startTime) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报名截止">{{ formatTime(detailData.enrollDeadline) || '长期开放' }}</el-descriptions-item>
          <el-descriptions-item label="我的加入时间">{{ formatTime(detailData.joinTime) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="课程简介">{{ detailData.description || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" plain @click="goResources">查看课程资料</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshLeft, View } from '@element-plus/icons-vue'
import CourseCard from '@/components/CourseCard.vue'
import { getMyJoinedCourses, quitCourse } from '@/api/studentCourse'

const router = useRouter()

const keyword = ref('')
const list = ref([])
const loading = ref(false)

// 课程详情弹窗
const detailVisible = ref(false)
const detailData = ref(null)

// 分页：接口返回全部已加入课程，前端切片展示；每页 8 门（4 列 × 2 行）
const pageNum = ref(1)
const pageSize = ref(8)
const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
})

/** 退出申请是否审核中：后端 COUNT 子查询返回 0/1（JSON 里可能是数字或布尔） */
function isQuitPending(row) {
  return Number(row?.quitPending) > 0
}

/** 卡片角标：默认展示课程类型，退出审核中时额外提示，避免学生重复提交 */
function badgeText(row) {
  const type = row.courseType || '未分类'
  return isQuitPending(row) ? `退出审核中 · ${type}` : type
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

function formatDate(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 10)
}

async function loadList() {
  loading.value = true
  try {
    const res = await getMyJoinedCourses(keyword.value.trim())
    list.value = res.data || []
    // 删除/退出后当前页可能已越界，回到最后一页，避免出现空白页
    const maxPage = Math.max(1, Math.ceil(list.value.length / pageSize.value))
    if (pageNum.value > maxPage) pageNum.value = maxPage
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadList()
}

function handleReset() {
  keyword.value = ''
  pageNum.value = 1
  loadList()
}

function openDetail(row) {
  detailData.value = { ...row }
  detailVisible.value = true
}

function goResources() {
  detailVisible.value = false
  router.push('/student/course/resources')
}

function goPractice() {
  router.push('/student/practice/papers')
}

/** 申请退出课程：提交后课程仍在列表中，直到任课教师审核通过才移除 */
async function handleQuit(row) {
  try {
    await ElMessageBox.confirm(
      `确认要申请退出《${row.courseName}》课程吗？提交后需等待任课教师审核。`,
      '退出课程',
      { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  try {
    const res = await quitCourse(row.id)
    ElMessage.success(res.msg || '退出申请已提交，请等待教师审核')
    loadList()
  } catch {
    // 拦截器已弹错误提示
  }
}

onMounted(loadList)
</script>

<style scoped>
.my-course-page {
  padding: 4px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
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
  flex-wrap: wrap;
}
.course-count {
  font-size: 12px;
  color: #909399;
}
/* 卡片网格容器：加载中给个最小高度，避免内容区高度抖动 */
.course-grid-wrap {
  min-height: 200px;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
/* 详情弹窗封面 */
.detail-cover {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 12px;
}
</style>
