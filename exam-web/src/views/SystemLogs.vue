<template>
  <div class="system-logs-page">
    <!-- 顶部筛选查询区：已去掉「操作时间」区间筛选，
         四个条件 + 查询/重置按钮排在同一行（间距由 .filter-form 收紧） -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline :label-width="80" class="filter-form">
        <el-form-item label="操作人" :label-width="70">
          <el-input
            v-model="query.operatorName"
            placeholder="账号模糊搜索"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="操作模块" :label-width="80">
          <el-select
            v-model="query.module"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="m in moduleOptions"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型" :label-width="80">
          <el-select
            v-model="query.operationType"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="t in typeOptions"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作状态" :label-width="80">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 110px"
          >
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 + 工具栏 -->
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">操作日志列表</span>
          <el-tag type="info" size="small">审计数据 · 只读</el-tag>
        </div>
        <div class="toolbar-right">
          <el-button type="success" :icon="Download" :loading="exporting" @click="handleExport">
            导出 Excel
          </el-button>
          <el-button :icon="RefreshRight" @click="loadList">刷新</el-button>
        </div>
      </div>

      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        :header-cell-style="{ textAlign: 'center', background: '#f5f7fa' }"
        :cell-style="{ textAlign: 'center' }"
        empty-text="暂无日志记录"
      >
        <!-- 序号列：跨页连续编号（第 2 页第 1 行 = 11），和操作时间一起固定在左侧 -->
        <el-table-column type="index" label="序号" width="60" fixed="left" :index="indexMethod" />
        <!-- 下面各列都带 show-overflow-tooltip：内容一律单行、超出显示省略号，
             鼠标悬停看完整值，行高保持一致（不会再出现「题库管/理」这种折行） -->
        <el-table-column label="操作时间" prop="createTime" width="165" fixed="left" show-overflow-tooltip />
        <el-table-column label="操作人" prop="operatorName" width="110" show-overflow-tooltip />
        <el-table-column label="操作模块" prop="module" width="120" show-overflow-tooltip />
        <el-table-column label="操作类型" prop="operationType" width="140" show-overflow-tooltip />
        <el-table-column label="操作描述" prop="description" min-width="220" show-overflow-tooltip />
        <el-table-column label="业务ID" prop="businessId" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.businessId">{{ row.businessId }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="IP地址" prop="clientIp" width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="90" fixed="right">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
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
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="操作日志详情"
      width="680px"
      destroy-on-close
    >
      <el-descriptions v-loading="detailLoading" :column="2" border>
        <el-descriptions-item label="操作时间" :span="2">
          {{ detail?.createTime }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人账号">
          {{ detail?.operatorName }}
          <span v-if="detail?.operatorId" class="text-muted">（ID: {{ detail.operatorId }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="客户端IP">
          {{ detail?.clientIp || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="操作模块">
          {{ detail?.module }}
        </el-descriptions-item>
        <el-descriptions-item label="操作类型">
          {{ detail?.operationType }}
        </el-descriptions-item>
        <el-descriptions-item label="业务ID" :span="2">
          <span v-if="detail?.businessId">{{ detail.businessId }}</span>
          <span v-else class="text-muted">无</span>
        </el-descriptions-item>
        <el-descriptions-item label="操作描述" :span="2">
          {{ detail?.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">
          <code class="request-url">{{ detail?.requestUrl || '-' }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="请求方式">
          {{ detail?.requestMethod || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="耗时">
          <span v-if="detail?.costMs != null">{{ detail.costMs }} ms</span>
          <span v-else class="text-muted">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="操作状态" :span="2">
          <el-tag :type="detail?.status === 1 ? 'success' : 'danger'" size="small">
            {{ detail?.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item
          v-if="detail?.status === 0"
          label="异常信息"
          :span="2"
        >
          <pre class="error-msg">{{ detail?.errorMsg || '无异常信息' }}</pre>
        </el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, RefreshRight, View } from '@element-plus/icons-vue'
import {
  getOperationLogList,
  getOperationLogById,
  getOperationLogModules,
  getOperationLogTypes,
  exportOperationLogs
} from '@/api/systemLog'

// ===== 查询条件（timeRange/startTime/endTime 已随「操作时间」筛选一起移除） =====
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  operatorName: '',
  module: '',
  operationType: '',
  status: null
})

// ===== 下拉选项 =====
const moduleOptions = ref([])
const typeOptions = ref([])

// ===== 表格数据 =====
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// ===== 详情弹窗 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

// ===== 导出 =====
const exporting = ref(false)

onMounted(() => {
  loadOptions()
  loadList()
})

/** 加载下拉选项 */
async function loadOptions() {
  try {
    const [m, t] = await Promise.all([
      getOperationLogModules(),
      getOperationLogTypes()
    ])
    moduleOptions.value = m.data || []
    typeOptions.value = t.data || []
  } catch (e) {
    // 静默失败：选项加载失败不影响主流程
  }
}

/** 加载日志列表 */
async function loadList() {
  loading.value = true
  try {
    const res = await getOperationLogList({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      operatorName: query.operatorName || undefined,
      module: query.module || undefined,
      operationType: query.operationType || undefined,
      status: query.status === null || query.status === undefined ? undefined : query.status
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    // 拦截器已弹提示
  } finally {
    loading.value = false
  }
}

/**
 * 序号列的取值：表格自增索引只从 1 开始，翻到第 2 页会重新从 1 数，
 * 这里补上偏移量，让序号跨页连续（第 2 页第 1 行 = 11）。
 */
function indexMethod(index) {
  return (query.pageNum - 1) * query.pageSize + index + 1
}

/** 查询按钮：回到第一页 */
function handleSearch() {
  query.pageNum = 1
  loadList()
}

/** 重置按钮 */
function handleReset() {
  query.operatorName = ''
  query.module = ''
  query.operationType = ''
  query.status = null
  query.pageNum = 1
  loadList()
}

/** 详情弹窗 */
async function handleDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getOperationLogById(row.id)
    detail.value = res.data
  } catch (e) {
    // 拦截器已弹提示
  } finally {
    detailLoading.value = false
  }
}

/** 导出 Excel */
async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportOperationLogs({
      operatorName: query.operatorName || undefined,
      module: query.module || undefined,
      operationType: query.operationType || undefined,
      status: query.status === null || query.status === undefined ? undefined : query.status
    })
    // axios responseType:'blob' 时返回整个 response，blob 在 .data
    const blobData = blob?.data || blob
    const url = window.URL.createObjectURL(
      blobData instanceof Blob ? blobData : new Blob([blobData])
    )
    const link = document.createElement('a')
    link.href = url
    link.download = `operation_log_${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.system-logs-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card,
.table-card {
  border-radius: 8px;
}

/* 筛选区单行：Element Plus 内联表单默认每个表单项右间距 32px、且有下外边距，
   四项条件 + 查询/重置按钮会挤到第二行；这里收紧到 16px 并去掉下边距 */
.filter-form :deep(.el-form-item) {
  margin-right: 16px;
  margin-bottom: 0;
}

.filter-form :deep(.el-form-item.filter-actions) {
  margin-right: 0;
}

/* 工具栏：左右分布 + 16px 间距 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 16px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 表格内容一律单行：不换行 + 超出省略号。
   配合各列的 show-overflow-tooltip（悬停看完整值），
   彻底消除「题库管 / 理」这类折行导致的行高翻倍。 */
.table-card :deep(.el-table .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.text-muted {
  color: #909399;
}

.request-url {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #606266;
  word-break: break-all;
}

.error-msg {
  margin: 0;
  padding: 8px;
  background: #fef0f0;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #f56c6c;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}
</style>
