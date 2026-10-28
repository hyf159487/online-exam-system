<template>
  <div class="class-student-list">
    <!-- 顶部：返回 + 标题 -->
    <el-card class="header-card">
      <div class="header-bar">
        <el-button :icon="Back" @click="goBack">返回</el-button>
        <span class="page-title">班级：{{ classInfo?.className || ('#' + classId) }} - 学生列表</span>
        <el-tag v-if="classInfo?.grade" effect="plain" style="margin-left: 8px">{{ classInfo.grade }}</el-tag>
        <el-tag v-if="classInfo?.department" type="info" effect="plain" style="margin-left: 8px">{{ classInfo.department }}</el-tag>
      </div>
    </el-card>

    <!-- 学生表格 -->
    <el-card style="margin-top: 16px">
      <div class="table-toolbar">
        <span class="total-info">共 {{ tableData.length }} 名学生</span>
        <div v-if="isAdmin" class="toolbar-actions">
          <el-button type="primary" :icon="Plus" @click="openAddDialog">加入学生</el-button>
          <el-button
            type="warning"
            :icon="Switch"
            :disabled="selectedRows.length === 0"
            @click="openBatchClassDialog"
          >
            批量调班{{ selectedRows.length ? `（已选 ${selectedRows.length} 人）` : '' }}
          </el-button>
          <el-button
            type="danger"
            :icon="Remove"
            :disabled="selectedRows.length === 0"
            @click="handleBatchRemove"
          >
            批量移出本班
          </el-button>
        </div>
      </div>

      <el-table
        ref="tableRef"
        :data="tableData"
        v-loading="loading"
        border
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="isAdmin" type="selection" width="50" />

        <el-table-column type="index" label="#" width="50" />

        <el-table-column prop="username" label="学号" min-width="130" />

        <el-table-column prop="realName" label="姓名" min-width="120" />

        <el-table-column prop="phone" label="手机号" min-width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>

        <el-table-column prop="status" label="账号状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column v-if="isAdmin" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSingleClassDialog(row)">
              <el-icon><Edit /></el-icon> 调班
            </el-button>
            <el-button link type="danger" @click="handleSingleRemove(row)">
              <el-icon><Remove /></el-icon> 移出本班
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="该班级暂无学生" />
        </template>
      </el-table>
    </el-card>

    <!-- 调班对话框（单人/批量共用，仅管理员） -->
    <el-dialog
      v-model="classDialogVisible"
      :title="dialogMode === 'batch' ? `批量调班（已选 ${selectedRows.length} 人）` : `调整班级 - ${dialogStudent.realName || ''}`"
      width="480px"
      @closed="resetClassForm"
    >
      <el-descriptions v-if="dialogMode === 'batch'" :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="当前班级">{{ classInfo?.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="已选学生">
          {{ selectedRows.map((r) => r.realName).join('、') }}
        </el-descriptions-item>
      </el-descriptions>
      <el-descriptions v-else :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="学号">{{ dialogStudent.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前班级">{{ classInfo?.className || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="调整到" required>
          <el-select v-model="targetClassId" placeholder="请选择目标班级" style="width: 100%">
            <el-option
              v-for="c in otherClasses"
              :key="c.id"
              :label="c.className"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classSubmitting" @click="submitClassChange">确定</el-button>
      </template>
    </el-dialog>

    <!-- 加入学生对话框（从其他班级/未分配学生中选人调入本班，仅管理员） -->
    <el-dialog v-model="addDialogVisible" title="加入学生到本班" width="560px">
      <el-alert type="info" :closable="false" style="margin-bottom: 14px">
        <template #title>
          选择其他班级或暂未分班的学生加入「{{ classInfo?.className }}」，支持按学号/姓名搜索
        </template>
      </el-alert>
      <el-select
        v-model="addStudentIds"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        placeholder="搜索并选择学生（学号/姓名）"
        :loading="addLoading"
        style="width: 100%"
      >
        <el-option
          v-for="s in addableStudents"
          :key="s.id"
          :label="`${s.realName}（${s.username}）${s.classId ? ' - ' + classNameOf(s.classId) : ' - 未分班'}`"
          :value="s.id"
        />
      </el-select>
      <div style="margin-top: 8px; color: #909399; font-size: 12px">
        已选 {{ addStudentIds.length }} 人，加入后其原班级将变更为本班
      </div>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" :disabled="addStudentIds.length === 0" @click="submitAdd">
          确认加入（{{ addStudentIds.length }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Edit, Remove, Switch, Plus } from '@element-plus/icons-vue'
import { getClassList, getClassStudentList } from '@/api/sysClass'
import { updateStudentClass, removeStudentClass, getUserPage } from '@/api/sysUser'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
// 调班/移出属于管理员权限；教师仅可查看学生名单
const isAdmin = computed(() => authStore.userInfo?.role === 2)

const classId = ref(Number(route.params.classId))
const classInfo = ref(null)
const loading = ref(false)
const tableData = ref([])
const classes = ref([])
const tableRef = ref(null)

// 目标班级下拉：过滤掉当前班级（不能调班到当前所在班级）
const otherClasses = computed(() => classes.value.filter((c) => c.id !== classId.value))

// 多选
const selectedRows = ref([])
function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function loadData() {
  loading.value = true
  try {
    const [studentRes, classRes] = await Promise.all([
      getClassStudentList(classId.value),
      getClassList()
    ])
    tableData.value = studentRes.data || []
    classes.value = classRes.data || []
    classInfo.value = classes.value.find((c) => c.id === classId.value) || null
  } catch (err) {
    console.error('加载班级学生失败:', err)
    ElMessage.error('加载失败：' + (err.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/classes')
}

// ============ 调班（单人/批量共用一个弹窗，仅管理员） ============
const classDialogVisible = ref(false)
const classSubmitting = ref(false)
const dialogMode = ref('single') // single | batch
const dialogStudent = ref({})
const targetClassId = ref(null)

function openSingleClassDialog(row) {
  dialogMode.value = 'single'
  dialogStudent.value = row
  targetClassId.value = null
  classDialogVisible.value = true
}

function openBatchClassDialog() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要调班的学生')
    return
  }
  dialogMode.value = 'batch'
  dialogStudent.value = {}
  targetClassId.value = null
  classDialogVisible.value = true
}

function resetClassForm() {
  dialogStudent.value = {}
  targetClassId.value = null
}

async function submitClassChange() {
  // 边界校验：必须选择目标班级（下拉已过滤当前班级，双保险再校验一次）
  if (!targetClassId.value) {
    ElMessage.warning('请选择目标班级')
    return
  }
  if (targetClassId.value === classId.value) {
    ElMessage.warning('不能调班到当前所在班级')
    return
  }
  const ids = dialogMode.value === 'batch'
    ? selectedRows.value.map((r) => r.id)
    : [dialogStudent.value.id]
  classSubmitting.value = true
  try {
    // 单人/批量复用同一接口：studentIds 数组
    await updateStudentClass(ids, targetClassId.value)
    ElMessage.success(`调班成功，共迁移 ${ids.length} 名学生`)
    classDialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('调班失败:', e)
  } finally {
    classSubmitting.value = false
  }
}

// ============ 移出本班（单人/批量，仅管理员） ============
async function handleSingleRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.realName}（${row.username}）」移出本班吗？移出后学生班级为空，账号仍保留在系统账号管理中。`,
      '移出确认',
      { type: 'warning' }
    )
    await removeStudentClass([row.id])
    ElMessage.success('已移出本班')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error('移出失败:', e)
  }
}

async function handleBatchRemove() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要移出的学生')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定将选中的 ${selectedRows.value.length} 名学生全部移出本班吗？移出后学生班级为空，账号仍保留。`,
      '批量移出确认',
      { type: 'warning' }
    )
    await removeStudentClass(selectedRows.value.map((r) => r.id))
    ElMessage.success('批量移出成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error('批量移出失败:', e)
  }
}

// ============ 加入学生（从其他班级/未分班学生调入本班，仅管理员） ============
const addDialogVisible = ref(false)
const addLoading = ref(false)
const addSubmitting = ref(false)
const addStudentIds = ref([])
const allStudents = ref([])

// classId → 班级名（下拉项展示学生当前班级用）
function classNameOf(cid) {
  return classes.value.find(c => c.id === cid)?.className || '未知班级'
}

// 可加入的学生：排除已在本班的
const addableStudents = computed(() =>
  allStudents.value.filter(s => s.classId !== classId.value)
)

async function openAddDialog() {
  addStudentIds.value = []
  addDialogVisible.value = true
  addLoading.value = true
  try {
    // 拉取全部学生（大页容量一次取完），前端过滤掉本班学生
    const res = await getUserPage({ pageNum: 1, pageSize: 10000, role: 0 })
    allStudents.value = res.data?.records || res.data?.list || res.data || []
  } catch (err) {
    console.error('加载学生列表失败:', err)
    ElMessage.error('加载学生列表失败：' + (err.message || '网络异常'))
  } finally {
    addLoading.value = false
  }
}

async function submitAdd() {
  if (addStudentIds.value.length === 0) {
    ElMessage.warning('请先选择要加入的学生')
    return
  }
  addSubmitting.value = true
  try {
    await updateStudentClass(addStudentIds.value, classId.value)
    ElMessage.success(`成功加入 ${addStudentIds.value.length} 名学生`)
    addDialogVisible.value = false
    loadData()
  } catch (err) {
    console.error('加入学生失败:', err)
  } finally {
    addSubmitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.class-student-list {
  padding: 0;
}

.header-card :deep(.el-card__body) {
  padding: 14px 20px;
}

.header-bar {
  display: flex;
  align-items: center;
}

.page-title {
  margin-left: 12px;
  font-size: 16px;
  font-weight: 600;
}

.table-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.total-info {
  color: #909399;
  font-size: 14px;
}

.toolbar-actions {
  display: inline-flex;
  gap: 10px;
}
</style>
