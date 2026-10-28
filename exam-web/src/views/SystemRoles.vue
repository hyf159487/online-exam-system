<template>
  <div class="system-roles-page">
    <!-- 顶部筛选 + 操作 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="角色名称">
          <el-input
            v-model="query.roleName"
            placeholder="角色名称模糊搜索"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 角色表格 -->
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">角色列表</span>
          <el-tag type="info" size="small">固定 3 种角色</el-tag>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增角色</el-button>
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
        empty-text="暂无角色数据"
      >
        <el-table-column label="角色ID" prop="id" width="80" />
        <el-table-column label="角色名称" prop="roleName" width="140" />
        <el-table-column label="角色标识" prop="roleKey" width="140">
          <template #default="{ row }">
            <el-tag :type="row.roleKey === 'admin' ? 'danger' : row.roleKey === 'teacher' ? 'primary' : 'success'" size="small">
              {{ row.roleKey }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="200" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link :icon="Setting" @click="handleAssignMenus(row)">分配菜单</el-button>
            <el-button
              v-if="row.roleKey !== 'admin'"
              type="danger"
              link
              :icon="Delete"
              @click="handleDelete(row)"
            >删除</el-button>
            <span v-else class="text-muted" style="margin-left: 8px">受保护</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑角色弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="formMode === 'add' ? '新增角色' : '编辑角色'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如：教师" maxlength="32" />
        </el-form-item>
        <el-form-item v-if="formMode === 'add'" label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="如：teacher（小写英文）" maxlength="64" />
        </el-form-item>
        <el-form-item v-else label="角色标识">
          <el-input :model-value="form.roleKey" disabled />
          <div class="form-tip">角色标识不可修改</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="角色职责描述"
            maxlength="255"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配菜单权限弹窗 -->
    <el-dialog
      v-model="assignVisible"
      :title="`分配菜单权限 - ${currentRole?.roleName || ''}`"
      width="640px"
      destroy-on-close
    >
      <div class="assign-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>勾选该角色可访问的菜单；父菜单联动勾选所有子菜单。</span>
        <el-tag v-if="currentRole?.roleKey === 'admin'" type="danger" size="small" style="margin-left: 8px">
          超级管理员：至少保留 1 个菜单
        </el-tag>
      </div>

      <el-tree
        ref="treeRef"
        :data="menuTree"
        :props="treeProps"
        node-key="id"
        show-checkbox
        :default-checked-keys="checkedKeys"
        :default-expand-all="true"
        :check-strictly="false"
        :expand-on-click-node="false"
        class="menu-tree"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.menuName }}</span>
            <el-tag
              v-if="data.layout === 'student'"
              type="success"
              size="small"
              style="margin-left: 8px"
            >学生端</el-tag>
            <el-tag
              v-else
              type="primary"
              size="small"
              style="margin-left: 8px"
            >教师端</el-tag>
            <span v-if="data.routePath" class="tree-route">{{ data.routePath }}</span>
          </span>
        </template>
      </el-tree>

      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleSubmitAssign">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, RefreshRight, Plus, Edit, Delete, Setting, InfoFilled
} from '@element-plus/icons-vue'
import {
  getRoleList, createRole, updateRole, deleteRole,
  getRoleMenus, assignRoleMenus
} from '@/api/role'
import { getMenuTree } from '@/api/menu'
import { useAuthStore } from '@/stores/auth'

// ===== 列表 =====
const query = reactive({ roleName: '' })
const loading = ref(false)
const tableData = ref([])

const authStore = useAuthStore()

onMounted(() => {
  loadList()
  loadMenuTree()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getRoleList(query.roleName || undefined)
    tableData.value = res.data || []
  } catch (e) {
    // 拦截器已弹提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadList()
}

function handleReset() {
  query.roleName = ''
  loadList()
}

// ===== 新增 / 编辑 =====
const formVisible = ref(false)
const formMode = ref('add')
const formRef = ref()
const submitting = ref(false)
const form = reactive({
  id: null,
  roleName: '',
  roleKey: '',
  remark: ''
})

const formRules = {
  roleName: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  roleKey: [{ required: true, message: '角色标识不能为空', trigger: 'blur' }]
}

function handleAdd() {
  formMode.value = 'add'
  form.id = null
  form.roleName = ''
  form.roleKey = ''
  form.remark = ''
  formVisible.value = true
}

function handleEdit(row) {
  formMode.value = 'edit'
  form.id = row.id
  form.roleName = row.roleName
  form.roleKey = row.roleKey
  form.remark = row.remark || ''
  formVisible.value = true
}

async function handleSubmitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (formMode.value === 'add') {
        const res = await createRole({
          roleName: form.roleName,
          roleKey: form.roleKey,
          remark: form.remark
        })
        if (res.code === 200) {
          ElMessage.success('新增成功')
          formVisible.value = false
          loadList()
        }
      } else {
        const res = await updateRole({
          id: form.id,
          roleName: form.roleName,
          remark: form.remark
        })
        if (res.code === 200) {
          ElMessage.success('更新成功')
          formVisible.value = false
          loadList()
        }
      }
    } catch (e) {
      // 拦截器已弹提示
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除角色「${row.roleName}」？该操作会同时清空该角色的菜单权限。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch (e) {
    return       // 用户取消
  }
  try {
    const res = await deleteRole(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadList()
    }
  } catch (e) {
    // 拦截器已弹提示
  }
}

// ===== 分配菜单权限 =====
const assignVisible = ref(false)
const assigning = ref(false)
const currentRole = ref(null)
const menuTree = ref([])
const checkedKeys = ref([])
const treeRef = ref()

const treeProps = {
  label: 'menuName',
  children: 'children'
}

async function loadMenuTree() {
  try {
    const res = await getMenuTree()
    menuTree.value = res.data || []
  } catch (e) {
    // 静默失败
  }
}

async function handleAssignMenus(row) {
  currentRole.value = row
  checkedKeys.value = []
  assignVisible.value = true
  // 加载该角色已勾选的菜单 ID
  try {
    const res = await getRoleMenus(row.id)
    // 注意：el-tree 的 default-checked-keys 是响应式初始值，
    // 弹窗已 destroy-on-close，每次打开都是新实例，直接赋值即可
    checkedKeys.value = res.data || []
  } catch (e) {
    // 静默失败
  }
}

async function handleSubmitAssign() {
  if (!treeRef.value) return
  // 取全部勾选 + 半选节点 ID（半选父菜单也要存，便于回显时父菜单显示半选状态）
  const checked = treeRef.value.getCheckedKeys()
  const halfChecked = treeRef.value.getHalfCheckedKeys()
  const allIds = [...checked, ...halfChecked]

  if (allIds.length === 0) {
    ElMessage.warning('至少保留 1 个菜单权限')
    return
  }
  if (currentRole.value?.roleKey === 'admin' && allIds.length < 3) {
    ElMessage.warning('超级管理员至少保留 3 个菜单，避免系统无可用菜单')
    return
  }

  assigning.value = true
  try {
    const res = await assignRoleMenus(currentRole.value.id, allIds)
    if (res.code === 200) {
      ElMessage.success('权限分配成功')
      assignVisible.value = false
      // 如果当前登录管理员修改了自己的角色权限，重新拉一次自己的菜单
      if (authStore.userInfo?.roleId === currentRole.value.id) {
        await authStore.loadMenus()
      }
    }
  } catch (e) {
    // 拦截器已弹提示
  } finally {
    assigning.value = false
  }
}
</script>

<style scoped>
.system-roles-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card,
.table-card {
  border-radius: 8px;
}

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

.text-muted {
  color: #909399;
  font-size: 12px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.assign-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #ecf5ff;
  border-radius: 4px;
  color: #606266;
  font-size: 13px;
  margin-bottom: 12px;
}

.menu-tree {
  max-height: 480px;
  overflow-y: auto;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-route {
  color: #909399;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
