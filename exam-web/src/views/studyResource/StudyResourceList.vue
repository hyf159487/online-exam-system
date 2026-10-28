<template>
  <div class="study-resource-page">
    <!-- 筛选区 -->
    <el-card class="card filter-card" shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <!-- 嵌入「课程信息 → 课程资料」抽屉时，课程由父组件锁定，不再给下拉 -->
        <el-form-item label="所属课程" v-if="!embedded">
          <el-select v-model="query.courseId" placeholder="请选择课程（支持名称/编码搜索）" clearable filterable
            style="width: 240px" @change="handleCourseChange" :filter-method="(v) => (courseKeyword = v)">
            <el-option v-for="c in _filteredCourseList" :key="c.id" :label="courseLabel(c)" :value="c.id">
              <div class="course-option">
                <span>{{ c.courseName }}</span>
                <span v-if="c.courseCode" class="course-code">{{ c.courseCode }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="资料名称">
          <el-input v-model="query.keyword" placeholder="资料名称搜索" clearable style="width: 220px"
            @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表区 -->
    <el-card class="card list-card" shadow="never">
      <div class="toolbar">
        <span class="title">{{ embedded && currentCourseName ? `课程学习资料 — ${currentCourseName}` : '课程学习资料' }}</span>
        <div class="actions">
          <el-button type="danger" plain :icon="Delete" :disabled="!selection.length" @click="handleBatchDelete">
            批量删除
          </el-button>
          <el-button type="primary" :icon="Plus" @click="openUpload">新增资料</el-button>
          <el-button type="warning" :icon="Download" @click="handleExport">导出资料清单</el-button>
        </div>
      </div>

        <el-table :data="list" v-loading="loading" class="resource-table" border @selection-change="onSelectionChange" empty-text="暂无学习资料">
          <el-table-column type="selection" width="45" />
          <!-- 序号列：跨页连续编号（第 2 页第 1 行 = 11），由 indexMethod 补上分页偏移量 -->
          <el-table-column type="index" label="序号" width="70" align="center" :index="indexMethod" />
          <el-table-column prop="resourceName" label="资料名称" min-width="240" show-overflow-tooltip />
          <el-table-column prop="courseName" label="所属课程" width="160" show-overflow-tooltip />
          <el-table-column label="文件类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="fileTypeTagType(row.fileType)">{{ fileTypeText(row.fileType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fileSizeText" label="大小" width="110" align="center" />
          <el-table-column prop="viewCount" label="浏览次数" width="100" align="center" />
          <el-table-column prop="downloadCount" label="下载次数" width="100" align="center" />
          <el-table-column prop="uploadTeacherName" label="上传人" width="100" align="center">
            <template #default="{ row }">{{ row.uploadTeacherName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" width="170" align="center" />
          <!-- 备注：新增/编辑弹窗里填写的资料说明，超长省略、悬停显示全文 -->
          <el-table-column label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right" align="center">
            <template #default="{ row }">
              <!-- 外部链接资料没有文件本体，只提供「打开学习」 -->
              <el-button v-if="row.fileType === 'link'" link type="primary" @click="handleOpenLink(row)">
                打开学习
              </el-button>
              <template v-else>
                <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
                <el-button link type="primary" @click="handleDownload(row)">下载</el-button>
              </template>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination class="pager" background layout="total, sizes, prev, pager, next, jumper"
          :total="total" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]" @current-change="load" @size-change="handleSizeChange" />
    </el-card>

    <!-- 新增资料弹窗 -->
    <el-dialog v-model="uploadVisible" title="新增资料" width="520px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="所属课程">
          <el-select v-model="uploadForm.courseId" placeholder="请选择课程（支持名称/编码搜索）" filterable
            style="width: 100%" :filter-method="(v) => (uploadCourseKeyword = v)">
            <el-option v-for="c in _uploadCourseList" :key="c.id" :label="courseLabel(c)" :value="c.id">
              <div class="course-option">
                <span>{{ c.courseName }}</span>
                <span v-if="c.courseCode" class="course-code">{{ c.courseCode }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="资料类型">
          <el-radio-group v-model="uploadForm.mode">
            <el-radio-button value="file">上传文件</el-radio-button>
            <el-radio-button value="link">外部链接</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资料名称">
          <el-input v-model="uploadForm.resourceName"
            :placeholder="uploadForm.mode === 'link' ? '如 第3讲 树与二叉树（外部视频）' : '默认为文件名，可自定义'" />
        </el-form-item>
        <el-form-item v-if="uploadForm.mode === 'file'" label="选择文件">
          <el-upload ref="uploadRef" :auto-upload="false" :limit="1" :file-list="fileList"
            :on-change="onFileChange" :on-remove="onFileRemove" accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md,.zip,.rar,.7z"
            drag style="width: 100%">
            <div class="upload-tip">点击或拖拽文件到此处上传</div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF / Word / PPT / Excel / 文本 / 压缩包，单个不超过 300MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <!-- 链接模式：填第三方教学视频地址（B站/腾讯课堂等），学生点击「去学习」直接跳转 -->
        <el-form-item v-else label="链接地址">
          <el-input v-model="uploadForm.linkUrl" placeholder="https://www.bilibili.com/video/BVxxxxxxxx" clearable />
          <div class="form-tip">支持 B 站、腾讯课堂、网易云课堂等第三方教学视频页面地址，需以 http:// 或 https:// 开头</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" placeholder="选填，资料说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="520px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="所属课程">
          <el-select v-model="editForm.courseId" placeholder="请选择课程（支持名称/编码搜索）" filterable
            style="width: 100%" :filter-method="(v) => (editCourseKeyword = v)">
            <el-option v-for="c in _editCourseList" :key="c.id" :label="courseLabel(c)" :value="c.id">
              <div class="course-option">
                <span>{{ c.courseName }}</span>
                <span v-if="c.courseCode" class="course-code">{{ c.courseCode }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="资料名称">
          <el-input v-model="editForm.resourceName" placeholder="请输入资料名称" />
        </el-form-item>
        <!-- 外部链接资料：支持修改链接地址 -->
        <el-form-item v-if="editForm.fileType === 'link'" label="链接地址">
          <el-input v-model="editForm.linkUrl" placeholder="https://..." clearable />
          <div class="form-tip">需以 http:// 或 https:// 开头</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="选填，资料说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Download } from '@element-plus/icons-vue'
import { getCourseList } from '@/api/course'
import {
  getStudyResourceList, uploadStudyResource, addStudyResourceLink, updateStudyResource,
  deleteStudyResource, batchDeleteStudyResources, downloadStudyResource,
  exportStudyResources, viewStudyResource
} from '@/api/studyResource'

// ---------- 文件类型展示 ----------
const FILE_TYPE_LABEL = {
  pdf: 'PDF', word: 'Word', ppt: 'PPT', excel: 'Excel',
  text: '文本', compress: '压缩包', image: '图片', video: '视频', audio: '音频',
  link: '外部链接', other: '其他'
}
const FILE_TYPE_TAG = {
  pdf: 'danger', word: 'primary', ppt: 'warning', excel: 'success',
  text: 'info', compress: 'info', image: 'info', video: 'info', audio: 'info',
  link: 'success', other: 'info'
}
function fileTypeText(t) { return FILE_TYPE_LABEL[t] || t || '-' }
function fileTypeTagType(t) { return FILE_TYPE_TAG[t] || 'info' }

// ---------- 查询 ----------
const route = useRoute()

/**
 * 两种复用入口：
 * 1) 独立页面（/study-resources）：课程从路由 ?courseId= 带入，用户可自行切换筛选；
 * 2) 嵌入「课程信息 → 课程资料」抽屉：由父组件传入 courseId 锁定本课程（隐藏课程下拉）。
 */
const props = defineProps({
  courseId: { type: [Number, String], default: null },
  embedded: { type: Boolean, default: false }
})

const courseOptions = ref([])
const query = reactive({ courseId: null, keyword: '', pageNum: 1, pageSize: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

/**
 * 序号列的取值：表格自增索引只从 1 开始，翻到第 2 页会重新从 1 数，
 * 这里补上分页偏移量，让序号跨页连续（第 2 页第 1 行 = 11）。
 */
function indexMethod(index) {
  return (query.pageNum - 1) * query.pageSize + index + 1
}

// ---------- 课程下拉搜索（支持下拉选择 + 文本输入） ----------
const courseKeyword = ref('') // 筛选区关键字
const uploadCourseKeyword = ref('') // 新增弹窗关键字
const editCourseKeyword = ref('') // 编辑弹窗关键字

/** 课程选项显示文本：名称 + 编码 */
function courseLabel(c) {
  return c.courseCode ? `${c.courseName}（${c.courseCode}）` : c.courseName
}

/** 按名称或编码过滤课程 */
function filterCourseByKeyword(kw, list) {
  const k = (kw || '').trim().toLowerCase()
  if (!k) return list
  return list.filter(
    (c) =>
      (c.courseName || '').toLowerCase().includes(k) ||
      (c.courseCode || '').toLowerCase().includes(k)
  )
}
const _filteredCourseList = computed(() => filterCourseByKeyword(courseKeyword.value, courseOptions.value))
const _uploadCourseList = computed(() => filterCourseByKeyword(uploadCourseKeyword.value, courseOptions.value))
const _editCourseList = computed(() => filterCourseByKeyword(editCourseKeyword.value, courseOptions.value))
const selection = ref([])

async function load() {
  loading.value = true
  try {
    const res = await getStudyResourceList({
      courseId: query.courseId,
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

/** 当前锁定/筛选的课程名（嵌入抽屉时显示在标题里，明确管理的是哪门课） */
const currentCourseName = computed(() => {
  const c = courseOptions.value.find((item) => item.id === query.courseId)
  return c ? c.courseName : ''
})

function handleCourseChange() {
  query.courseId = query.courseId || null
  query.pageNum = 1
  load()
}
function handleQuery() { query.pageNum = 1; load() }
function handleReset() {
  // 嵌入抽屉时课程由父组件锁定，重置只清关键字
  if (!props.embedded) query.courseId = null
  query.keyword = ''
  query.pageNum = 1
  load()
}
function handleSizeChange() { query.pageNum = 1; load() }
function onSelectionChange(rows) { selection.value = rows }

// ---------- 上传 ----------
const uploadVisible = ref(false)
const uploading = ref(false)
// mode：file=上传文件，link=外部链接（第三方教学视频地址，不落盘，学生点击跳转学习）
const uploadForm = reactive({ courseId: null, mode: 'file', resourceName: '', linkUrl: '', description: '' })
const fileList = ref([])
const rawFile = ref(null)
const uploadRef = ref(null)

// 与后端 application.properties 的 spring.servlet.multipart.max-file-size 保持一致（300MB）
const MAX_UPLOAD_SIZE = 300 * 1024 * 1024

function openUpload() {
  uploadForm.courseId = query.courseId || null
  uploadForm.mode = 'file'
  uploadForm.resourceName = ''
  uploadForm.linkUrl = ''
  uploadForm.description = ''
  fileList.value = []
  rawFile.value = null
  uploadVisible.value = true
}
function onFileChange(file) {
  // 选择文件时先校验大小，超限立即提示并清空，避免传上去才报"网络异常"
  if (file.raw && file.raw.size > MAX_UPLOAD_SIZE) {
    ElMessage.error('单个文件不能超过 300MB，请压缩或拆分后重新上传')
    uploadRef.value?.clearFiles()
    rawFile.value = null
    return
  }
  rawFile.value = file.raw
  if (!uploadForm.resourceName) uploadForm.resourceName = file.name
}
function onFileRemove() { rawFile.value = null }

async function submitUpload() {
  if (!uploadForm.courseId) { ElMessage.warning('请选择所属课程'); return }
  // 链接模式：只提交名称 + 链接地址（后端会再次校验协议与长度）
  if (uploadForm.mode === 'link') {
    if (!uploadForm.resourceName) { ElMessage.warning('请输入资料名称'); return }
    const url = (uploadForm.linkUrl || '').trim()
    if (!url) { ElMessage.warning('请填写链接地址'); return }
    if (!/^https?:\/\//i.test(url)) { ElMessage.warning('链接地址需以 http:// 或 https:// 开头'); return }
    uploading.value = true
    try {
      await addStudyResourceLink({
        courseId: uploadForm.courseId,
        resourceName: uploadForm.resourceName,
        linkUrl: url,
        description: uploadForm.description
      })
      ElMessage.success('添加成功')
      uploadVisible.value = false
      if (String(query.courseId) === String(uploadForm.courseId) || !query.courseId) load()
    } finally {
      uploading.value = false
    }
    return
  }
  if (!rawFile.value) { ElMessage.warning('请选择要上传的文件'); return }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('courseId', uploadForm.courseId)
    fd.append('file', rawFile.value)
    if (uploadForm.resourceName) fd.append('resourceName', uploadForm.resourceName)
    if (uploadForm.description) fd.append('description', uploadForm.description)
    const res = await uploadStudyResource(fd)
    ElMessage.success('上传成功')
    uploadVisible.value = false
    if (String(query.courseId) === String(uploadForm.courseId) || !query.courseId) load()
  } finally {
    uploading.value = false
  }
}

// ---------- 编辑 ----------
const editVisible = ref(false)
const editing = ref(false)
// fileType：外部链接（link）资料在编辑弹窗中额外显示「链接地址」输入框
const editForm = reactive({ id: null, courseId: null, fileType: '', resourceName: '', linkUrl: '', description: '' })

function openEdit(row) {
  editForm.id = row.id
  editForm.courseId = row.courseId
  editForm.fileType = row.fileType || ''
  editForm.resourceName = row.resourceName
  // 链接资料的链接地址就存在 filePath 字段
  editForm.linkUrl = row.fileType === 'link' ? (row.filePath || '') : ''
  editForm.description = row.description || ''
  editVisible.value = true
}
async function submitEdit() {
  if (!editForm.resourceName) { ElMessage.warning('请输入资料名称'); return }
  editing.value = true
  try {
    const payload = {
      resourceName: editForm.resourceName,
      description: editForm.description,
      courseId: editForm.courseId
    }
    // 外部链接资料允许同时修改链接地址
    if (editForm.fileType === 'link') {
      const url = (editForm.linkUrl || '').trim()
      if (!url) { ElMessage.warning('请填写链接地址'); return }
      if (!/^https?:\/\//i.test(url)) { ElMessage.warning('链接地址需以 http:// 或 https:// 开头'); return }
      payload.linkUrl = url
    }
    await updateStudyResource(editForm.id, payload)
    ElMessage.success('修改成功')
    editVisible.value = false
    load()
  } finally {
    editing.value = false
  }
}

// ---------- 删除 ----------
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除资料「${row.resourceName}」？删除后不可恢复。`, '提示', { type: 'warning' })
  } catch { return }
  await deleteStudyResource(row.id)
  ElMessage.success('删除成功')
  load()
}

async function handleBatchDelete() {
  if (!selection.value.length) return
  try {
    await ElMessageBox.confirm(`确认删除勾选的 ${selection.value.length} 份资料？删除后不可恢复。`, '提示', { type: 'warning' })
  } catch { return }
  const res = await batchDeleteStudyResources(selection.value.map(r => r.id))
  const data = res.data || {}
  const success = data.success || 0
  const failed = data.failed || 0
  if (failed > 0) {
    ElMessage.warning(`已删除 ${success} 份，${failed} 份删除失败（可能已被删除）：${(data.failedReasons || []).join('；')}`)
  } else {
    ElMessage.success(`已删除 ${success} 份资料`)
  }
  load()
}

// ---------- 预览 / 下载 / 打开外部链接 ----------
/** 外部链接资料：新窗口打开站外教学视频，同时记一次浏览 */
async function handleOpenLink(row) {
  if (!row.filePath) { ElMessage.error('链接地址缺失'); return }
  try { await viewStudyResource(row.id) } catch (e) { /* 忽略统计失败 */ }
  window.open(row.filePath, '_blank', 'noopener,noreferrer')
}

async function handlePreview(row) {
  if (row.fileType !== 'pdf') { ElMessage.info('该类型暂不支持在线预览，请下载后查看'); return }
  // 直接用后端静态资源地址打开 PDF：浏览器原生流式加载，
  // 既不经过 axios，也不受 60s 请求超时影响，更不会把整份大文件读进内存，
  // 从根源上避免之前"网络异常/页面崩溃"的问题
  if (!row.filePath) { ElMessage.error('预览失败：文件路径缺失'); return }
  // 先记录一次浏览（累计"浏览次数"），记录失败不阻断预览
  try { await viewStudyResource(row.id) } catch (e) { /* 忽略统计失败 */ }
  window.open(row.filePath, '_blank')
}

async function handleDownload(row) {
  try {
    const res = await downloadStudyResource(row.id)
    saveBlob(res.data || res, row.resourceName)
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

// ---------- 导出 ----------
async function handleExport() {
  try {
    const res = await exportStudyResources({ courseId: query.courseId, keyword: query.keyword || undefined })
    saveBlob(res.data || res, `学习资料清单_${Date.now()}.xlsx`)
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
  }
}

onMounted(async () => {
  courseOptions.value = (await getCourseList()).data || []
  // 嵌入课程信息页时以父组件传入的 courseId 为准；独立页面时读路由 ?courseId=xx
  const cid = Number(props.courseId ?? route.query.courseId)
  if (cid) query.courseId = cid
  load() // 进入页面即加载全部资料，课程仅作为筛选条件
})
</script>

<style scoped>
.study-resource-page {
  padding: 16px;
}
.card {
  border-radius: 6px;
}
.filter-card {
  margin-bottom: 12px;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.list-card .toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.list-card .toolbar .title {
  font-size: 16px;
  font-weight: 600;
}
.upload-tip {
  padding: 12px;
  color: #909399;
  text-align: center;
}
/* 表单项下方的小字提示（外部链接地址格式说明等） */
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.course-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.course-code {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

/* ---------- 学习资料表格：表头/内容居中 + 浅色格线 ---------- */
.resource-table {
  /* 更浅的格线颜色 */
  --el-table-border-color: #ececec;
  --el-table-header-bg-color: #fafafa;
}
/* 表头与单元格统一居中 */
.resource-table :deep(th.el-table__cell),
.resource-table :deep(td.el-table__cell) {
  text-align: center;
}
</style>
