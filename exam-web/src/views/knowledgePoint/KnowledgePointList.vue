<template>
  <div class="knowledge-point-page">
    <!-- 筛选区：必须先选课程，才加载该课程的知识点 -->
    <div class="page-header">
      <el-form inline @submit.prevent>
        <!-- 嵌入「课程信息 → 课程知识点」抽屉时，课程由父组件锁定，不再给下拉 -->
        <el-form-item label="课程" v-if="!embedded">
          <el-select
            v-model="query.courseId"
            placeholder="请选择课程（支持名称/编码搜索）"
            style="width: 220px"
            filterable
            clearable
            :filter-method="filterCourseOptions"
            @change="handleCourseChange"
          >
            <el-option v-for="c in filteredCourseList" :key="c.id" :label="courseLabel(c)" :value="c.id">
              <div class="course-option">
                <span>{{ c.courseName }}</span>
                <span v-if="c.courseCode" class="course-code">{{ c.courseCode }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="名称/编码">
          <el-input
            v-model="query.keyword"
            placeholder="知识点名称或编码"
            style="width: 200px"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="header-actions">
        <!-- 只读模式（教师看课程信息页的知识点）：知识点写入统一由管理员维护，这里只保留查看/导出 -->
        <el-button v-if="!readonly" type="primary" :icon="Plus" @click="openDialog()">新增知识点</el-button>
        <el-upload
          v-if="!readonly"
          :show-file-list="false"
          accept=".xlsx,.xls"
          :http-request="handleImport"
        >
          <el-button :icon="Upload">批量导入</el-button>
        </el-upload>
        <el-button :icon="Download" @click="handleExport">导出清单</el-button>
        <el-button v-if="!readonly" :icon="Delete" :disabled="!selectedRows.length" @click="handleBatchDelete">
          批量删除
        </el-button>
        <el-button v-if="!readonly" text type="info" @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table
      v-loading="loading"
      :data="tableData"
      row-key="id"
      border
      stripe
      class="kp-table"
      @selection-change="(rows) => (selectedRows = rows)"
    >
      <el-table-column v-if="!readonly" type="selection" width="46" />
      <!-- 序号列：跨页连续编号（第 2 页第 1 行 = 11），由 indexMethod 补上分页偏移量 -->
      <el-table-column type="index" label="序号" width="70" align="center" :index="indexMethod" />
      <el-table-column prop="courseName" label="所属课程" width="160" show-overflow-tooltip />
      <el-table-column prop="pointName" label="知识点名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="knowledgeCode" label="知识点编码" width="130" align="center">
        <template #default="{ row }">{{ row.knowledgeCode || '—' }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序号" width="90" align="center" />
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column v-if="!readonly" label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="emptyDescription" :image-size="100" />
      </template>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingRow ? '编辑知识点' : '新增知识点'"
      width="520px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="归属课程" prop="courseId">
          <el-select
            v-model="form.courseId"
            placeholder="请选择课程（支持名称/编码搜索）"
            style="width: 100%"
            filterable
            :filter-method="filterDialogCourse"
          >
            <el-option v-for="c in dialogCourseOptions" :key="c.id" :label="courseLabel(c)" :value="c.id">
              <div class="course-option">
                <span>{{ c.courseName }}</span>
                <span v-if="c.courseCode" class="course-code">{{ c.courseCode }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="知识点名称" prop="pointName">
          <el-input v-model="form.pointName" placeholder="请输入知识点名称，如：函数的概念" maxlength="100" />
        </el-form-item>
        <el-form-item label="知识点编码" prop="knowledgeCode">
          <el-input v-model="form.knowledgeCode" placeholder="选填，如 FUNC-001" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
          <span class="form-tip">数值越小越靠前（用于题目录入时下拉排序）</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Edit, Plus, RefreshLeft, Search, Upload } from '@element-plus/icons-vue'
import { getCourseList } from '@/api/course'
import {
  getKnowledgePointPage,
  addKnowledgePoint,
  updateKnowledgePoint,
  deleteKnowledgePoint,
  batchDeleteKnowledgePoints,
  importKnowledgeExcel,
  downloadKnowledgeTemplate,
  exportKnowledgePoints
} from '@/api/knowledgePoint'

const route = useRoute()

/**
 * 两种复用入口：
 * 1) 独立页面（/knowledge-points）：课程从路由 ?courseId= 带入，用户可自行切换筛选；
 * 2) 嵌入「课程信息 → 课程知识点」抽屉：由父组件传入 courseId 锁定本课程（隐藏课程下拉）。
 */
const props = defineProps({
  courseId: { type: [Number, String], default: null },
  embedded: { type: Boolean, default: false },
  // 只读模式：嵌入课程信息页时，教师只能查看本课程知识点（写入权限仅管理员）
  readonly: { type: Boolean, default: false }
})

/** 浏览器下载 blob 文件 */
function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const selectedRows = ref([])

const courseList = ref([])
const emptyDescription = ref('暂无知识点数据')

/** 两个课程下拉各自的过滤关键字（Element Plus filterable 过滤后回填到 filter-method） */
const courseKeyword = ref('')
const dialogCourseKeyword = ref('')

/** 课程选项显示的文本：名称 + 编码 */
function courseLabel(c) {
  return c.courseCode ? `${c.courseName}（${c.courseCode}）` : c.courseName
}

/** 按名称或编码过滤课程（筛选区下拉） */
function filterCourseOptions(val) {
  courseKeyword.value = val || ''
}
const filteredCourseList = computed(() => {
  const kw = courseKeyword.value.trim().toLowerCase()
  if (!kw) return courseList.value
  return courseList.value.filter(
    (c) =>
      (c.courseName || '').toLowerCase().includes(kw) ||
      (c.courseCode || '').toLowerCase().includes(kw)
  )
})

/** 弹窗下拉同款过滤 */
function filterDialogCourse(val) {
  dialogCourseKeyword.value = val || ''
}
const dialogCourseOptions = computed(() => {
  const kw = dialogCourseKeyword.value.trim().toLowerCase()
  if (!kw) return courseList.value
  return courseList.value.filter(
    (c) =>
      (c.courseName || '').toLowerCase().includes(kw) ||
      (c.courseCode || '').toLowerCase().includes(kw)
  )
})

const query = reactive({
  courseId: null,
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

/**
 * 序号列的取值：表格自增索引只从 1 开始，翻到第 2 页会重新从 1 数，
 * 这里补上分页偏移量，让序号跨页连续（第 2 页第 1 行 = 11）。
 */
function indexMethod(index) {
  return (query.pageNum - 1) * query.pageSize + index + 1
}

function requireCourse() {
  if (!query.courseId) {
    ElMessage.warning('请先选择课程')
    return false
  }
  return true
}

/** 课程切换：重置页码并立即按新课程加载 */
function handleCourseChange() {
  query.pageNum = 1
  query.keyword = ''
  loadList()
}

/** 查询：回到第一页 */
function handleSearch() {
  query.pageNum = 1
  loadList()
}

/** 重置筛选 */
function handleReset() {
  // 嵌入抽屉时课程由父组件锁定，重置只清关键字
  if (!props.embedded) query.courseId = null
  query.keyword = ''
  query.pageNum = 1
  loadList() // 课程留空 = 查询全部知识点
}

/** 加载知识点分页数据（未选课程时不请求，展示空态提示） */
async function loadList() {
  loading.value = true
  try {
    const res = await getKnowledgePointPage({
      courseId: query.courseId,
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    /* 拦截器已统一弹错 */
  } finally {
    loading.value = false
  }
}

// ==================== 新增 / 编辑 ====================

const dialogVisible = ref(false)
const submitting = ref(false)
const editingRow = ref(null)
const formRef = ref()

const form = reactive({
  id: null,
  courseId: null,
  pointName: '',
  knowledgeCode: '',
  sortOrder: 0,
  remark: ''
})

const formRules = {
  courseId: [{ required: true, message: '请选择归属课程', trigger: 'change' }],
  pointName: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }]
}

function openDialog(row) {
  editingRow.value = row || null
  if (row) {
    Object.assign(form, {
      id: row.id,
      courseId: row.courseId,
      pointName: row.pointName,
      knowledgeCode: row.knowledgeCode || '',
      sortOrder: row.sortOrder ?? 0,
      remark: row.remark || ''
    })
  } else {
    form.id = null
    form.courseId = query.courseId // 默认带入选中的课程
    form.pointName = ''
    form.knowledgeCode = ''
    form.sortOrder = 0
    form.remark = ''
  }
  dialogVisible.value = true
}

function resetForm() {
  editingRow.value = null
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = { ...form }
    if (editingRow.value) {
      await updateKnowledgePoint(payload)
      ElMessage.success('修改成功')
    } else {
      await addKnowledgePoint(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    /* 拦截器已统一弹错 */
  } finally {
    submitting.value = false
  }
}

// ==================== 删除 ====================

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除知识点「${row.pointName}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        await deleteKnowledgePoint(row.id)
        ElMessage.success('删除成功')
        // 若删完当前页空了，回到上一页
        if (tableData.value.length === 1 && query.pageNum > 1) {
          query.pageNum -= 1
        }
        loadList()
      } catch (e) {
        /* 被试题引用时后端会返回提示：存在试题正在使用该知识点… */
      }
    })
    .catch(() => {})
}

function handleBatchDelete() {
  if (!selectedRows.value.length) return
  const names = selectedRows.value.map((r) => r.pointName).join('、')
  ElMessageBox.confirm(`确定删除选中的 ${selectedRows.value.length} 条知识点吗？\n（${names}）`, '批量删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        await batchDeleteKnowledgePoints(selectedRows.value.map((r) => r.id))
        ElMessage.success('删除成功')
        loadList()
      } catch (e) {
        /* 后端会提示具体被引用的知识点 */
      }
    })
    .catch(() => {})
}

// ==================== Excel 导入 / 导出 ====================

async function handleImport({ file }) {
  if (!requireCourse()) return
  try {
    const res = await importKnowledgeExcel(file, query.courseId)
    ElMessage.success(res.msg || '导入成功')
    query.pageNum = 1
    loadList()
  } catch (e) {
    /* 拦截器已统一弹错（含逐行错误明细） */
  }
}

function handleDownloadTemplate() {
  downloadKnowledgeTemplate()
    .then((res) => {
      saveBlob(res.data, '知识点导入模板.xlsx')
    })
    .catch((e) => {})
}

function handleExport() {
  const course = courseList.value.find((c) => c.id === query.courseId)
  exportKnowledgePoints({ courseId: query.courseId, keyword: query.keyword || undefined })
    .then((res) => {
      saveBlob(res.data, `知识点导出_${course?.courseName || '全部课程'}.xlsx`)
    })
    .catch((e) => {})
}

// ==================== 初始化 ====================
onMounted(() => {
  // 嵌入课程信息页时以父组件传入的 courseId 为准；独立页面时读路由 ?courseId=xx
  const cid = Number(props.courseId ?? route.query.courseId)
  if (cid) {
    query.courseId = cid
  }
  getCourseList()
    .then((res) => {
      courseList.value = res.data || []
    })
    .catch((err) => console.error('加载课程列表失败:', err))
  loadList()
})
</script>

<style scoped>
/* 表头与内容统一居中 */
.kp-table :deep(.el-table__header th.el-table__cell),
.kp-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}

.knowledge-point-page {
  padding: 4px;
}

.page-header {
  background: #fff;
  border-radius: 8px;
  padding: 16px 16px 0;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 18px;
  flex-wrap: wrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 10px;
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
</style>
