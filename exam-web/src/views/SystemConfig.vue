<template>
  <div class="system-config-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">系统参数配置</span>
          <div class="card-actions">
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
            <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存配置</el-button>
          </div>
        </div>
      </template>

      <div class="config-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>修改参数后点击「保存配置」生效。系统会实时读取数据库中的最新值，无需重启服务。</span>
      </div>

      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        :header-cell-style="{ textAlign: 'center', background: '#f5f7fa' }"
        :cell-style="{ textAlign: 'center' }"
        empty-text="暂无系统参数"
      >
        <!-- 序号列：type="index" 由表格自增，居中显示（全局 cell-style 已设 textAlign: center） -->
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column label="参数名称" prop="configName" width="200" />
        <el-table-column label="参数 Key" prop="configKey" width="220">
          <template #default="{ row }">
            <span class="mono">{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <!-- 参数值：收窄到 190（减掉单元格内边距后仍有 174px，放得下默认 150px 宽的输入框）；
             改成固定宽，剩余宽度全部让给「说明」列，避免它被截断 -->
        <el-table-column label="参数值" width="190">
          <template #default="{ row }">
            <!-- 数字类型：数字输入框 -->
            <el-input-number
              v-if="row.configType === 'number'"
              v-model="row._editValue"
              :min="0"
              :precision="0"
              :step="1"
              controls-position="right"
              @change="markDirty(row)"
            />
            <!-- 开关类型 -->
            <el-switch
              v-else-if="row.configType === 'boolean'"
              v-model="row._editValue"
              @change="markDirty(row)"
            />
            <!-- 文本类型 -->
            <el-input
              v-else
              v-model="row._editValue"
              placeholder="请输入参数值"
              @change="markDirty(row)"
            />
          </template>
        </el-table-column>
        <!-- 类型 80 → 100：标签和表头不再贴边 -->
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.configType === 'number' ? 'primary' : row.configType === 'boolean' ? 'success' : 'info'"
              size="small"
            >
              {{ configTypeLabel(row.configType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="remark" min-width="200" show-overflow-tooltip />
        <el-table-column label="修改状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row._dirty" type="warning" size="small">已修改</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Check, InfoFilled } from '@element-plus/icons-vue'
import { getConfigList, batchUpdateConfig } from '@/api/config'

const loading = ref(false)
const saving = ref(false)
const tableData = ref([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const res = await getConfigList()
    const list = res.data || []
    // 初始化 _editValue 和 _dirty
    tableData.value = list.map(item => ({
      ...item,
      _editValue: convertToEditValue(item),
      _dirty: false
    }))
  } catch (e) { /* 拦截器已提示 */ }
  finally { loading.value = false }
}

/** 根据 config_type 把 configValue 转换为适合编辑的值 */
function convertToEditValue(item) {
  if (item.configType === 'number') {
    const num = parseInt(item.configValue, 10)
    return isNaN(num) ? 0 : num
  }
  if (item.configType === 'boolean') {
    return item.configValue === 'true'
  }
  return item.configValue || ''
}

/** 把编辑值转回存储格式 */
function convertToStorageValue(row) {
  if (row.configType === 'boolean') {
    return row._editValue ? 'true' : 'false'
  }
  return String(row._editValue)
}

function configTypeLabel(type) {
  return { number: '数字', boolean: '开关', string: '文本' }[type] || type
}

function markDirty(row) {
  row._dirty = true
}

async function handleSave() {
  const dirtyRows = tableData.value.filter(r => r._dirty)
  if (dirtyRows.length === 0) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认保存 ${dirtyRows.length} 项修改？保存后立即生效。`,
      '保存确认', { type: 'warning' }
    )
  } catch { return }

  saving.value = true
  try {
    const payload = dirtyRows.map(r => ({
      id: r.id,
      configValue: convertToStorageValue(r)
    }))
    const res = await batchUpdateConfig(payload)
    if (res.code === 200) {
      ElMessage.success('保存成功')
      dirtyRows.forEach(r => {
        r.configValue = convertToStorageValue(r)
        r._dirty = false
      })
    }
  } catch { /* 拦截器已提示 */ }
  finally { saving.value = false }
}

async function handleReset() {
  const hasDirty = tableData.value.some(r => r._dirty)
  if (hasDirty) {
    try {
      await ElMessageBox.confirm(
        '将放弃所有未保存的修改，恢复为数据库中的原始值？',
        '重置确认', { type: 'warning' }
      )
    } catch { return }
  }
  await loadData()
  ElMessage.success('已重置')
}
</script>

<style scoped>
.system-config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.config-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #ecf5ff;
  border-radius: 4px;
  color: #606266;
  font-size: 13px;
  margin-bottom: 16px;
}

.mono {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #606266;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.card-actions {
  display: flex;
  gap: 8px;
}
</style>
