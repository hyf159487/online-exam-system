<template>
  <div class="course-resource-page">
    <div class="page-header">
      <div class="page-title-row">
        <h2 class="page-title">课程资料</h2>
        <span class="page-sub">已加入课程的学习资料，在线预览（PDF）与下载。</span>
      </div>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-select
          v-model="courseId"
          placeholder="全部已加入课程（可搜索学科）"
          clearable
          filterable
          remote
          :remote-method="remoteSearchCourse"
          :loading="courseLoading"
          style="width: 280px"
          @change="handleCourseChange"
          @clear="handleCourseChange(null)"
          @visible-change="handleVisible"
        >
          <el-option
            v-for="c in courseViewOptions"
            :key="c.id"
            :label="courseLabel(c)"
            :value="c.id"
          >
            <div class="course-option">
              <span>{{ c.courseName }}</span>
              <span v-if="c.courseCode" class="course-code">{{ c.courseCode }}</span>
            </div>
          </el-option>
        </el-select>
        <el-button type="primary" plain @click="loadList">刷新</el-button>
        <span class="resource-tip">不选课程默认展示已加入课程的全部资料，PDF 可在线预览，外部链接可直接去学习</span>
      </div>

      <el-table :data="list" v-loading="loading" stripe border class="res-table" empty-text="暂无学习资料">
        <el-table-column label="资料名称" min-width="240">
          <template #default="{ row }">
            <div class="paper-name">{{ row.resourceName }}</div>
            <div v-if="row.description" class="paper-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="resourceTagType(row.fileType)" effect="plain">
              {{ resourceTypeText(row.fileType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSizeText" label="大小" width="100" align="center" />
        <el-table-column label="上传人" width="120" align="center">
          <template #default="{ row }">{{ row.uploadTeacherName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="170" align="center" />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <!-- 外部链接资料（第三方教学视频）：新窗口跳转学习，不提供下载 -->
            <el-button v-if="row.fileType === 'link'" type="primary" link @click="openLink(row)">
              去学习
            </el-button>
            <template v-else>
              <el-button v-if="row.fileType === 'pdf'" type="primary" link @click="previewResource(row)">
                预览
              </el-button>
              <el-button type="primary" link @click="downloadResource(row)">下载</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getJoinedCourses } from '@/api/course'
import {
  getStudentStudyResources,
  downloadStudentStudyResource,
  previewStudentStudyResource,
  viewStudentStudyResource
} from '@/api/studyResource'

function courseLabel(c) {
  return c.courseCode ? `${c.courseName}（${c.courseCode}）` : c.courseName
}

// 学科下拉远程搜索器：只返回【当前学生已加入的课程】，否则与"只能查看已加入课程"的约束相悖
function createCourseRemoteSearch() {
  const options = ref([])
  const selected = ref(null)
  const loading = ref(false)
  let timer = null
  const viewOptions = computed(() => {
    const list = options.value.slice()
    const cur = selected.value
    if (cur && !list.some((c) => c.id === cur.id)) list.unshift(cur)
    return list
  })
  function search(kw) {
    const keyword = (kw || '').trim()
    clearTimeout(timer)
    timer = setTimeout(async () => {
      loading.value = true
      try {
        const res = await getJoinedCourses(keyword)
        options.value = res.data || []
      } catch {
        options.value = []
      } finally {
        loading.value = false
      }
    }, keyword ? 250 : 0)
  }
  function onSelect(val) {
    selected.value = viewOptions.value.find((c) => c.id === val) || null
  }
  return { options, selected, loading, viewOptions, search, onSelect }
}

const courseSearch = createCourseRemoteSearch()
const courseId = ref(null)
const courseLoading = courseSearch.loading
const courseViewOptions = courseSearch.viewOptions
function remoteSearchCourse(kw) {
  courseSearch.search(kw)
}
function handleVisible(v) {
  if (v && courseSearch.options.value.length === 0) courseSearch.search('')
}
function handleCourseChange(val) {
  courseSearch.onSelect(val)
  loadList()
}

const RESOURCE_TYPE_LABEL = {
  pdf: 'PDF', word: 'Word', ppt: 'PPT', excel: 'Excel',
  text: '文本', compress: '压缩包', image: '图片', video: '视频', audio: '音频',
  link: '外部链接', other: '其他'
}
const RESOURCE_TYPE_TAG = {
  pdf: 'danger', word: 'primary', ppt: 'warning', excel: 'success',
  text: 'info', compress: 'info', image: 'info', video: 'info', audio: 'info',
  link: 'success', other: 'info'
}
const resourceTypeText = (t) => RESOURCE_TYPE_LABEL[t] || t || '-'
const resourceTagType = (t) => RESOURCE_TYPE_TAG[t] || 'info'

const list = ref([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await getStudentStudyResources(courseId.value || undefined)
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function previewResource(row) {
  try {
    const res = await previewStudentStudyResource(row.id)
    const blob = res.data || res
    if (!(blob instanceof Blob)) {
      ElMessage.error('预览失败')
      return
    }
    window.open(URL.createObjectURL(blob), '_blank')
  } catch (e) {
    ElMessage.error(e?.message || '预览失败')
  }
}

// 外部链接资料（第三方教学视频）：先记录一次浏览，再新窗口跳转站外学习
async function openLink(row) {
  if (!row.filePath) {
    ElMessage.error('链接地址缺失，请联系老师重新设置')
    return
  }
  try {
    await viewStudentStudyResource(row.id)
  } catch {
    // 统计失败不阻断跳转
  }
  window.open(row.filePath, '_blank', 'noopener,noreferrer')
}

async function downloadResource(row) {
  try {
    const res = await downloadStudentStudyResource(row.id)
    const blob = res.data || res
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.resourceName || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}

onMounted(loadList)
</script>

<style scoped>
.course-resource-page {
  padding: 4px;
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
}
.resource-tip {
  font-size: 12px;
  color: #909399;
}
.course-option {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.course-code {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}
.paper-name {
  font-weight: 500;
  color: #303133;
}
.paper-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
/* 表头与内容统一居中 */
.res-table :deep(.el-table__header th.el-table__cell),
.res-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}
</style>
