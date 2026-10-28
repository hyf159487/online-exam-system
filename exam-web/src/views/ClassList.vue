<template>
  <div class="class-list">
    <!-- 搜索筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="班级名称">
          <el-input v-model="query.keyword" placeholder="输入班级名称搜索" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="query.grade" placeholder="全部年级" clearable style="width: 150px">
            <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="院系">
          <el-select v-model="query.department" placeholder="全部院系" clearable style="width: 200px">
            <el-option v-for="d in departmentOptions" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-toolbar">
        <span class="total-info">共 {{ filteredData.length }} 个班级</span>
        <div v-if="isAdmin" class="toolbar-actions">
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增班级</el-button>
          <el-button type="success" :icon="Operation" @click="openBatchDialog">批量新增</el-button>
          <el-button type="warning" :icon="Upload" @click="openImportDialog">Excel 导入</el-button>
        </div>
      </div>

      <el-table :data="pagedData" v-loading="loading" border style="width: 100%" :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">{{ (pageNum - 1) * pageSize + $index + 1 }}</template>
        </el-table-column>

        <el-table-column prop="className" label="班级名称" min-width="140" />

        <el-table-column prop="grade" label="年级" min-width="100">
          <template #default="{ row }">{{ row.grade || '-' }}</template>
        </el-table-column>

        <el-table-column prop="department" label="院系" min-width="160">
          <template #default="{ row }">{{ row.department || '-' }}</template>
        </el-table-column>

        <el-table-column prop="major" label="专业" min-width="160">
          <template #default="{ row }">{{ row.major || '-' }}</template>
        </el-table-column>

        <el-table-column prop="teacherName" label="班主任" min-width="110">
          <template #default="{ row }">
            <el-tag v-if="row.teacherName" type="warning" effect="plain">{{ row.teacherName }}</el-tag>
            <span v-else style="color: #c0c4cc">未绑定</span>
          </template>
        </el-table-column>

        <el-table-column prop="studentCount" label="学生人数" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.studentCount > 0 ? 'success' : 'info'" effect="plain">
              {{ row.studentCount || 0 }} 人
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">
            <span class="time-cell">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="handleViewStudents(row)">
              <el-icon><View /></el-icon> 查看
            </el-button>
            <el-button v-if="isAdmin" link type="primary" @click="openEditDialog(row)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-tooltip
              v-if="isAdmin"
              :content="row.studentCount > 0 ? '班级下仍有学生，需先移出/调走全部学生才能删除' : '删除班级'"
              placement="top"
            >
              <el-button link type="danger" :disabled="row.studentCount > 0" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无班级数据，请点击右上角「新增班级」或「Excel 导入」" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="filteredData.length"
          :page-sizes="[5, 10, 15, 20, 30, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handlePageChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑班级对话框（仅管理员） -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑班级' : '新增班级'" width="480px" @closed="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" placeholder="如 23005801" maxlength="50" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="form.grade" placeholder="如 2023级" maxlength="50" />
        </el-form-item>
        <el-form-item label="院系" prop="department">
          <el-input v-model="form.department" placeholder="如 商学院" maxlength="100" />
        </el-form-item>
        <el-form-item label="专业" prop="major">
          <el-input v-model="form.major" placeholder="如 信息管理与信息系统" maxlength="100" />
        </el-form-item>
        <el-form-item label="班主任" prop="teacherId">
          <el-select v-model="form.teacherId" placeholder="选择班主任（可选）" clearable filterable style="width: 100%">
            <el-option
              v-for="t in teacherOptions"
              :key="t.id"
              :label="`${t.realName}（${t.username}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量新增对话框 -->
    <el-dialog v-model="batchVisible" title="批量新增班级" width="560px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <template #title>
          每行一个班级，格式：班级名称,年级,院系,专业（年级、院系、专业可省略，逗号支持中英文）
        </template>
      </el-alert>
      <el-input
        v-model="batchText"
        type="textarea"
        :rows="9"
        maxlength="5000"
        show-word-limit
        placeholder="23005805,2023级,信息管理与信息系统,专业名称&#10;23005806,2023级,信息管理与信息系统,专业名称&#10;23005807"
      />
      <div class="batch-summary">
        <el-tag type="info" effect="plain">共 {{ batchLines.length }} 行</el-tag>
        <el-tag type="success" effect="plain">有效 {{ batchValid.length }} 个</el-tag>
        <el-tag v-if="batchDupCount > 0" type="danger" effect="plain">批内重复 {{ batchDupCount }} 个</el-tag>
      </div>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" :disabled="batchValid.length === 0" @click="submitBatch">
          批量提交（{{ batchValid.length }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- Excel 导入对话框 -->
    <el-dialog v-model="importVisible" title="Excel 批量导入班级" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <template #title>Excel 列顺序：A 班级名称（必填）、B 年级、C 院系、D 专业；第一行表头自动跳过</template>
      </el-alert>
      <div class="import-actions">
        <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
          :on-exceed="handleExceed"
          :show-file-list="false"
        >
          <el-button type="success" :icon="Upload">选择 Excel 文件</el-button>
        </el-upload>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">开始导入</el-button>
      </div>
      <div v-if="importFile" class="file-info">
        <el-icon><Document /></el-icon>
        <span>{{ importFile.name }}</span>
        <el-button link type="danger" size="small" @click="clearImportFile">移除</el-button>
      </div>
      <div v-if="importResult" class="import-result">
        <el-divider />
        <el-result
          :icon="importResult.failed === 0 ? 'success' : 'warning'"
          :title="`成功 ${importResult.success} 个，失败 ${importResult.failed} 个`"
        />
        <div v-if="importResult.failedReasons && importResult.failedReasons.length" class="failed-list">
          <el-table :data="importResult.failedReasons.slice(0, 10).map(r => ({ reason: r }))" border size="small" max-height="220">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="reason" label="失败原因" />
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, View, Search, RefreshLeft, Operation, Upload, Download, Document } from '@element-plus/icons-vue'
import { getClassList, createClass, updateClass, deleteClass, batchCreateClasses, importClassesExcel } from '@/api/sysClass'
import { getUserListByRole } from '@/api/sysUser'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.userInfo?.role === 2)

function handleViewStudents(row) {
  router.push(`/classes/${row.id}/students`)
}

const loading = ref(false)
const tableData = ref([])
const teacherOptions = ref([])

// 分页状态
const pageNum = ref(1)
const pageSize = ref(10)

// 筛选条件
const query = reactive({ keyword: '', grade: '', department: '' })

// 年级/院系下拉选项（从现有数据动态提取）
const gradeOptions = computed(() =>
  [...new Set(tableData.value.map(c => c.grade).filter(Boolean))].sort()
)
const departmentOptions = computed(() =>
  [...new Set(tableData.value.map(c => c.department).filter(Boolean))].sort()
)

// 前端筛选（班级数据量小，无需后端分页）
const filteredData = computed(() => {
  return tableData.value.filter(c => {
    if (query.keyword && !c.className?.toLowerCase().includes(query.keyword.trim().toLowerCase())) return false
    if (query.grade && c.grade !== query.grade) return false
    if (query.department && c.department !== query.department) return false
    return true
  })
})

// 当前页数据（前端分页切片）
const pagedData = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

function handlePageChange() {
  // 页码/每页条数变化时若超出总页数则回退到最后一页
  const maxPage = Math.max(1, Math.ceil(filteredData.value.length / pageSize.value))
  if (pageNum.value > maxPage) pageNum.value = maxPage
}

function handleSearch() {
  // 前端实时筛选，查询按钮仅作交互闭环
}
function handleReset() {
  query.keyword = ''
  query.grade = ''
  query.department = ''
}

const formatTime = (t) => {
  if (!t) return '-'
  try {
    const d = new Date(t)
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return t
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getClassList()
    tableData.value = res.data || []
  } catch (err) {
    console.error('加载班级列表失败:', err)
    ElMessage.error('加载失败：' + (err.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

async function loadTeachers() {
  try {
    const res = await getUserListByRole(1)
    teacherOptions.value = res.data || []
  } catch (err) {
    console.error('加载教师列表失败:', err)
  }
}

onMounted(() => {
  loadData()
  if (isAdmin.value) loadTeachers()
})

// ============ 新增/编辑（管理员） ============
const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null,
  className: '',
  grade: '',
  department: '',
  major: '',
  teacherId: null
})
const rules = {
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  grade: [{ required: true, message: '请输入年级', trigger: 'blur' }],
  department: [{ required: true, message: '请输入院系', trigger: 'blur' }]
}

function openCreateDialog() {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  editing.value = true
  form.id = row.id
  form.className = row.className
  form.grade = row.grade || ''
  form.department = row.department || ''
  form.major = row.major || ''
  form.teacherId = row.teacherId || null
  dialogVisible.value = true
}

function resetForm() {
  form.id = null
  form.className = ''
  form.grade = ''
  form.department = ''
  form.major = ''
  form.teacherId = null
  formRef.value?.clearValidate?.()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        className: form.className.trim(),
        grade: form.grade.trim(),
        department: form.department.trim(),
        major: form.major.trim(),
        teacherId: form.teacherId || null
      }
      if (editing.value) {
        await updateClass(form.id, payload)
        ElMessage.success('班级更新成功')
      } else {
        await createClass(payload)
        ElMessage.success('班级创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {
      console.error('保存班级失败:', e)
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除班级「${row.className}」吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true
      }
    )
    await deleteClass(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('删除班级失败:', e)
    }
  }
}

// ============ 批量新增（textarea） ============
const batchVisible = ref(false)
const batchText = ref('')
const batchSubmitting = ref(false)

const batchLines = computed(() =>
  batchText.value.split('\n').map(l => l.trim()).filter(Boolean)
)
const batchParsed = computed(() =>
  batchLines.value.map(line => {
    const parts = line.split(/[,，]/).map(p => p.trim())
    return {
      className: parts[0] || '',
      grade: parts[1] || '',
      department: parts[2] || '',
      major: parts[3] || ''
    }
  })
)
const batchValid = computed(() => batchParsed.value.filter(c => c.className))
const batchDupCount = computed(() => {
  const names = batchValid.value.map(c => c.className)
  return names.length - new Set(names).size
})

function openBatchDialog() {
  batchText.value = ''
  batchVisible.value = true
}

async function submitBatch() {
  if (batchValid.value.length === 0) {
    ElMessage.warning('没有可提交的有效班级')
    return
  }
  batchSubmitting.value = true
  try {
    const res = await batchCreateClasses(batchValid.value)
    const result = res.data || { success: 0, failed: 0, failedReasons: [] }
    if (result.failed > 0) {
      ElMessageBox.alert(
        `成功 ${result.success} 个，失败 ${result.failed} 个：\n` +
        (result.failedReasons || []).slice(0, 10).join('\n'),
        '批量新增结果',
        { type: 'warning', confirmButtonText: '知道了' }
      ).catch(() => {})
    } else {
      ElMessage.success(`批量新增成功（${result.success} 个）`)
      batchVisible.value = false
    }
    loadData()
  } catch (err) {
    console.error('批量新增失败:', err)
  } finally {
    batchSubmitting.value = false
  }
}

// ============ Excel 导入 ============
const importVisible = ref(false)
const importFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

function openImportDialog() {
  importFile.value = null
  importResult.value = null
  importVisible.value = true
}

function handleFileChange(file) {
  importFile.value = file.raw
  importResult.value = null
}

function handleExceed() {
  ElMessage.warning('每次只能上传一个文件，请先移除已选文件')
}

function clearImportFile() {
  importFile.value = null
  importResult.value = null
  uploadRef.value?.clearFiles?.()
}

function downloadTemplate() {
  const content = '﻿班级名称,年级,院系,专业\n23005805,2023级,信息管理与信息系统,专业名称\n23005806,2023级,信息管理与信息系统,专业名称'
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '班级导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('模板已下载，用 Excel 编辑后另存为 .xlsx 再上传')
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importing.value = true
  importResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    const res = await importClassesExcel(formData)
    importResult.value = res.data
    if (importResult.value.failed === 0) {
      ElMessage.success(`导入成功（${importResult.value.success} 个）`)
    } else {
      ElMessage.warning(`成功 ${importResult.value.success} 个，失败 ${importResult.value.failed} 个`)
    }
    loadData()
  } catch (err) {
    ElMessage.error(err?.message || '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.class-list {
  padding: 0;
}

.filter-card {
  margin-bottom: 12px;
}
.filter-card :deep(.el-card__body) {
  padding: 14px 18px 0;
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.table-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-actions {
  display: inline-flex;
  gap: 10px;
}

.total-info {
  color: #909399;
  font-size: 14px;
}

.time-cell {
  color: #606266;
  font-family: Consolas, monospace;
  font-size: 13px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.batch-summary {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.import-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 12px 0;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #f0f9ff;
  border-radius: 8px;
  border: 1px solid #bae6fd;
  color: #0369a1;
}

.import-result {
  margin-top: 8px;
}
.import-result :deep(.el-result) {
  padding: 12px 0;
}
.failed-list {
  margin-top: 8px;
}
</style>
