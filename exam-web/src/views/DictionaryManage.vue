<template>
  <div class="dict-manage-page">
    <!-- 左侧：字典类型列表 -->
    <el-card shadow="never" class="type-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">字典类型</span>
          <el-button type="primary" size="small" :icon="Plus" @click="handleAddType">新增</el-button>
        </div>
      </template>

      <el-input
        v-model="typeSearch"
        placeholder="搜索字典类型"
        clearable
        size="small"
        :prefix-icon="Search"
        style="margin-bottom: 12px"
        @keyup.enter="loadTypes"
        @clear="loadTypes"
      />

      <el-table
        :data="typeList"
        v-loading="typeLoading"
        highlight-current-row
        border
        size="small"
        :header-cell-style="{ textAlign: 'center', background: '#f5f7fa' }"
        :cell-style="{ textAlign: 'center' }"
        height="calc(100vh - 260px)"
        @current-change="handleTypeSelect"
        empty-text="暂无字典类型"
      >
        <!-- 名称是唯一弹性列，吃掉卡片剩余宽度（约 138px）；
             编码 120：比上一版收窄，超长编码（exam_question_type）由 tooltip 补全；
             内置 70：给足宽度，「内置」标签不再被挤掉一半 -->
        <el-table-column label="名称" prop="typeName" min-width="100" show-overflow-tooltip />
        <el-table-column label="编码" prop="typeCode" width="120" show-overflow-tooltip />
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isBuiltin === 1" type="danger" size="small">内置</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="ops">
              <el-button type="primary" link size="small" :icon="Edit" @click="handleEditType(row)">编辑</el-button>
              <el-button
                v-if="row.isBuiltin !== 1"
                type="danger"
                link
                size="small"
                :icon="Delete"
                @click="handleDeleteType(row)"
              >删除</el-button>
              <span v-else class="text-muted">🚫 禁删</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 右侧：字典子项列表 -->
    <el-card shadow="never" class="item-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            字典子项
            <span v-if="currentType" class="subtitle">（{{ currentType.typeName }}）</span>
          </span>
          <el-button
            type="primary"
            size="small"
            :icon="Plus"
            :disabled="!currentType"
            @click="handleAddItem"
          >新增子项</el-button>
        </div>
      </template>

      <div v-if="!currentType" class="empty-hint">
        <el-empty description="请在左侧选择一个字典类型" :image-size="80" />
      </div>

      <el-table
        v-else
        :data="itemList"
        v-loading="itemLoading"
        border
        stripe
        size="small"
        :header-cell-style="{ textAlign: 'center', background: '#f5f7fa' }"
        :cell-style="{ textAlign: 'center' }"
        empty-text="暂无子项，点击「新增子项」添加"
      >
        <el-table-column label="显示标签" prop="itemLabel" width="120" />
        <el-table-column label="存储值" prop="itemValue" width="100" />
        <el-table-column label="排序" prop="sortOrder" width="70" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :active-value="1"
              :inactive-value="0"
              @change="toggleItemEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div class="ops">
              <el-button type="primary" link size="small" :icon="Edit" @click="handleEditItem(row)">编辑</el-button>
              <el-button type="danger" link size="small" :icon="Delete" @click="handleDeleteItem(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 字典类型新增/编辑弹窗 -->
    <el-dialog
      v-model="typeFormVisible"
      :title="typeFormMode === 'add' ? '新增字典类型' : '编辑字典类型'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeFormRules" label-width="100px" size="default">
        <el-form-item label="类型名称" prop="typeName">
          <el-input v-model="typeForm.typeName" placeholder="如：题型、难度" maxlength="32" />
        </el-form-item>
        <el-form-item v-if="typeFormMode === 'add'" label="字典编码" prop="typeCode">
          <el-input v-model="typeForm.typeCode" placeholder="如：question_type、difficulty" maxlength="64" />
        </el-form-item>
        <el-form-item v-else label="字典编码">
          <el-input :model-value="typeForm.typeCode" disabled />
          <div class="form-tip">编码创建后不可修改</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSubmitting" @click="submitTypeForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典子项新增/编辑弹窗 -->
    <el-dialog
      v-model="itemFormVisible"
      :title="itemFormMode === 'add' ? '新增字典子项' : '编辑字典子项'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemFormRules" label-width="100px" size="default">
        <el-form-item label="显示标签" prop="itemLabel">
          <el-input v-model="itemForm.itemLabel" placeholder="下拉框显示的文本，如：单选题" maxlength="64" />
        </el-form-item>
        <el-form-item label="存储值" prop="itemValue">
          <el-input v-model="itemForm.itemValue" placeholder="下拉框提交的值，如：1" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" />
          <span class="form-tip">数值越小越靠前</span>
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="itemForm.enabled" :active-value="1" :inactive-value="0" />
          <span class="form-tip">禁用后业务页面下拉框看不到该选项</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitting" @click="submitItemForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getDictTypeList, createDictType, updateDictType, deleteDictType,
  getDictItemList, createDictItem, updateDictItem, deleteDictItem
} from '@/api/dict'

// ===== 字典类型 =====
const typeSearch = ref('')
const typeLoading = ref(false)
const typeList = ref([])
const currentType = ref(null)

// ===== 字典子项 =====
const itemLoading = ref(false)
const itemList = ref([])

onMounted(loadTypes)

async function loadTypes() {
  typeLoading.value = true
  try {
    const res = await getDictTypeList(typeSearch.value || undefined)
    typeList.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  finally { typeLoading.value = false }
}

function handleTypeSelect(row) {
  currentType.value = row
  if (row) loadItems(row.id)
  else itemList.value = []
}

async function loadItems(typeId) {
  itemLoading.value = true
  try {
    const res = await getDictItemList(typeId)
    itemList.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  finally { itemLoading.value = false }
}

// ===== 类型弹窗 =====
const typeFormVisible = ref(false)
const typeFormMode = ref('add')
const typeFormRef = ref()
const typeSubmitting = ref(false)
const typeForm = reactive({ id: null, typeName: '', typeCode: '', remark: '' })

const typeFormRules = {
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  typeCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }]
}

function handleAddType() {
  typeFormMode.value = 'add'
  typeForm.id = null
  typeForm.typeName = ''
  typeForm.typeCode = ''
  typeForm.remark = ''
  typeFormVisible.value = true
}

function handleEditType(row) {
  typeFormMode.value = 'edit'
  typeForm.id = row.id
  typeForm.typeName = row.typeName
  typeForm.typeCode = row.typeCode
  typeForm.remark = row.remark || ''
  typeFormVisible.value = true
}

async function submitTypeForm() {
  if (!typeFormRef.value) return
  await typeFormRef.value.validate(async (valid) => {
    if (!valid) return
    typeSubmitting.value = true
    try {
      if (typeFormMode.value === 'add') {
        const res = await createDictType({
          typeName: typeForm.typeName,
          typeCode: typeForm.typeCode,
          remark: typeForm.remark,
          isBuiltin: 0
        })
        if (res.code === 200) {
          ElMessage.success('新增成功')
          typeFormVisible.value = false
          loadTypes()
        }
      } else {
        const res = await updateDictType({
          id: typeForm.id,
          typeName: typeForm.typeName,
          remark: typeForm.remark
        })
        if (res.code === 200) {
          ElMessage.success('更新成功')
          typeFormVisible.value = false
          loadTypes()
          // 刷新当前选中的类型
          if (currentType.value?.id === typeForm.id) {
            currentType.value = { ...currentType.value, typeName: typeForm.typeName, remark: typeForm.remark }
          }
        }
      }
    } catch (e) { /* 拦截器已提示 */ }
    finally { typeSubmitting.value = false }
  })
}

async function handleDeleteType(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除字典类型「${row.typeName}」？该操作会同时删除该类型下的所有子项。`,
      '删除确认', { type: 'warning' }
    )
  } catch { return }
  try {
    const res = await deleteDictType(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (currentType.value?.id === row.id) {
        currentType.value = null
        itemList.value = []
      }
      loadTypes()
    }
  } catch { /* 拦截器已提示 */ }
}

// ===== 子项弹窗 =====
const itemFormVisible = ref(false)
const itemFormMode = ref('add')
const itemFormRef = ref()
const itemSubmitting = ref(false)
const itemForm = reactive({
  id: null, typeId: null, itemLabel: '', itemValue: '',
  sortOrder: 0, enabled: 1, remark: ''
})

const itemFormRules = {
  itemLabel: [{ required: true, message: '请输入显示标签', trigger: 'blur' }],
  itemValue: [{ required: true, message: '请输入存储值', trigger: 'blur' }]
}

function handleAddItem() {
  if (!currentType.value) return
  itemFormMode.value = 'add'
  itemForm.id = null
  itemForm.typeId = currentType.value.id
  itemForm.itemLabel = ''
  itemForm.itemValue = ''
  itemForm.sortOrder = itemList.value.length   // 默认排到最后
  itemForm.enabled = 1
  itemForm.remark = ''
  itemFormVisible.value = true
}

function handleEditItem(row) {
  itemFormMode.value = 'edit'
  itemForm.id = row.id
  itemForm.typeId = row.typeId
  itemForm.itemLabel = row.itemLabel
  itemForm.itemValue = row.itemValue
  itemForm.sortOrder = row.sortOrder ?? 0
  itemForm.enabled = row.enabled ?? 1
  itemForm.remark = row.remark || ''
  itemFormVisible.value = true
}

async function submitItemForm() {
  if (!itemFormRef.value) return
  await itemFormRef.value.validate(async (valid) => {
    if (!valid) return
    itemSubmitting.value = true
    try {
      if (itemFormMode.value === 'add') {
        const res = await createDictItem({
          typeId: itemForm.typeId,
          itemLabel: itemForm.itemLabel,
          itemValue: itemForm.itemValue,
          sortOrder: itemForm.sortOrder,
          enabled: itemForm.enabled,
          remark: itemForm.remark
        })
        if (res.code === 200) {
          ElMessage.success('新增成功')
          itemFormVisible.value = false
          loadItems(itemForm.typeId)
        }
      } else {
        const res = await updateDictItem({
          id: itemForm.id,
          typeId: itemForm.typeId,
          itemLabel: itemForm.itemLabel,
          itemValue: itemForm.itemValue,
          sortOrder: itemForm.sortOrder,
          enabled: itemForm.enabled,
          remark: itemForm.remark
        })
        if (res.code === 200) {
          ElMessage.success('更新成功')
          itemFormVisible.value = false
          loadItems(itemForm.typeId)
        }
      }
    } catch { /* 拦截器已提示 */ }
    finally { itemSubmitting.value = false }
  })
}

async function handleDeleteItem(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除子项「${row.itemLabel}」？`,
      '删除确认', { type: 'warning' }
    )
  } catch { return }
  try {
    const res = await deleteDictItem(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadItems(row.typeId)
    }
  } catch { /* 拦截器已提示 */ }
}

async function toggleItemEnabled(row) {
  // 直接保存 enabled 状态
  try {
    const res = await updateDictItem({
      id: row.id,
      typeId: row.typeId,
      itemLabel: row.itemLabel,
      itemValue: row.itemValue,
      sortOrder: row.sortOrder,
      enabled: row.enabled,
      remark: row.remark
    })
    if (res.code === 200) {
      ElMessage.success(row.enabled === 1 ? '已启用' : '已禁用')
    }
  } catch {
    // 失败回滚
    row.enabled = row.enabled === 1 ? 0 : 1
  }
}
</script>

<style scoped>
.dict-manage-page {
  display: grid;
  /* 左侧「字典类型」固定 510px：编码 120 + 内置 70 + 操作 140 是固定宽，
     名称是唯一的弹性列，吃掉剩余宽度（约 138px）；宽度定死，列宽不随窗口变化 */
  grid-template-columns: 510px minmax(0, 1fr);
  gap: 16px;
}

.type-card,
.item-card {
  border-radius: 8px;
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

.card-title .subtitle {
  font-size: 13px;
  font-weight: normal;
  color: #909399;
  margin-left: 8px;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

/* 操作列统一间距：flex + gap，按钮与文字提示间距一致 */
.ops {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ops :deep(.el-button + .el-button) {
  margin-left: 0;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.empty-hint {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}
</style>
