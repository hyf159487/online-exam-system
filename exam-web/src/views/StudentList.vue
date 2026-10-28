<template>
  <div class="student-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query">
        <!-- 学号精确查询（整串相等）、姓名模糊查询（LIKE 关键字），两个条件可叠加 -->
        <el-form-item label="学号">
          <el-input
            v-model="query.username"
            placeholder="完整学号"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>

        <!-- 姓名是模糊查询：边输边查（watch + 防抖，见下方 nameSearchTimer），回车只是立即查一次 -->
        <el-form-item label="姓名">
          <el-input
            v-model="query.realName"
            placeholder="姓名关键字"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <!-- 管理员账号没有班级属性，该筛选只在学生/教师页显示 -->
        <el-form-item v-if="showClassField" label="班级">
          <el-select v-model="query.classId" placeholder="全部班级" clearable style="width: 160px" @clear="handleSearch">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="props.defaultRole === null" label="角色">
          <el-select v-model="query.role" placeholder="全部" clearable style="width: 130px">
            <el-option label="学生" :value="0" />
            <el-option label="教师" :value="1" />
            <el-option label="管理员" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
      <el-card style="margin-top: 16px">
      <div class="table-toolbar">
        <span class="total-info">共 {{ total }} 个{{ accountLabel }}</span>
        <div v-if="isAdmin" style="display: flex; gap: 10px; align-items: center">
          <!-- 系统只剩 1 个管理员时给出可见提示（勾选框会被整体禁用） -->
          <span v-if="lastAdminLocked" class="lock-hint">
            <el-icon><Lock /></el-icon> 仅剩 1 个管理员，已锁定勾选
          </span>
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增账号</el-button>
          <el-button type="success" @click="openBatchCreate">批量新增</el-button>
          <!-- 禁用态按钮不触发 hover，tooltip 必须挂在外层 span 上才有提示 -->
          <el-tooltip :disabled="!lastAdminLocked" content="系统需至少保留 1 个管理员，无法勾选删除" placement="bottom">
            <span style="display: inline-flex">
              <el-button
                type="danger"
                :disabled="!selectedRows.length || lastAdminLocked"
                :loading="batchDeleting"
                @click="handleBatchDelete"
              >批量删除{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}</el-button>
            </span>
          </el-tooltip>
        </div>
      </div>

      <el-table
        ref="tableRef"
        :data="tableData"
        v-loading="loading"
        border
        style="width: 100%"
        @selection-change="onSelectionChange"
      >
        <!-- selectable 返回 false 时该行勾选框置灰；全表都不可勾选时，表头全选框也会自动禁用 -->
        <el-table-column v-if="isAdmin" type="selection" width="42" align="center" :selectable="canSelectRow" />
        <!-- 序号列：跨页连续编号（第 2 页第 1 行 = 11），由 indexMethod 补上分页偏移量 -->
        <el-table-column type="index" label="序号" width="80" align="center" :index="indexMethod" />

        <el-table-column prop="username" label="学号/账号" min-width="130" align="center" header-align="center" />

        <el-table-column prop="realName" label="姓名" min-width="120" align="center" header-align="center" />

        <el-table-column prop="role" label="角色" width="100" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)" effect="light">
              {{ roleText(row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column v-if="showClassField" prop="className" label="班级" min-width="120" align="center" header-align="center">
          <template #default="{ row }">
            {{ classNameOf(row) }}
          </template>
        </el-table-column>

        <el-table-column prop="phone" label="手机号" min-width="120" align="center" header-align="center" />

        <el-table-column prop="status" label="状态" width="100" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" min-width="170" align="center" header-align="center">
          <template #default="{ row }">
            <span class="time-cell">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="320" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              link
              type="warning"
              :disabled="row.role === 2"
              @click="handleToggleStatus(row)"
            >
              <el-icon><Lock /></el-icon> 禁用
            </el-button>
            <el-button
              v-else
              link
              type="success"
              @click="handleToggleStatus(row)"
            >
              <el-icon><Unlock /></el-icon> 启用
            </el-button>
            <el-button link type="primary" @click="handleResetPwd(row)">
              <el-icon><Key /></el-icon> 重置密码
            </el-button>
            <!-- 管理员账号无班级概念，本页不展示「调班」（只在学生/教师页出现） -->
            <el-button
              v-if="isAdmin && showClassField"
              link
              type="warning"
              :disabled="row.role !== 0"
              @click="openClassDialog(row)"
            >
              <el-icon><Edit /></el-icon> 调班
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[5, 10, 15, 20, 30, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增账号对话框（仅管理员可见） -->
    <el-dialog v-model="createVisible" title="新增账号" width="480px" @closed="resetCreateForm">
      <el-form :model="form" :rules="createRules" ref="formRef" label-width="90px">
        <el-form-item label="学号/账号" prop="username">
          <el-input v-model="form.username" :placeholder="accountInputPlaceholder" maxlength="50" @input="onSingleUsernameInput" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" maxlength="50" />
        </el-form-item>
        <!-- 子菜单页（管理员/教师/学生账号）角色是固定的：直接展示，不再给下拉选择 -->
        <el-form-item v-if="roleLocked" label="角色">
          <el-tag :type="roleTag(form.role)" effect="light">{{ roleText(form.role) }}</el-tag>
          <span class="role-locked-tip">当前页面已固定账号角色</span>
        </el-form-item>
        <el-form-item v-else label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%" @change="onCreateRoleChange">
            <el-option label="学生" :value="0" />
            <el-option label="教师" :value="1" />
            <el-option label="管理员" :value="2" />
          </el-select>
        </el-form-item>
        <!-- 班级只对学生有意义：教师/管理员账号不显示该字段（与批量新增弹窗保持一致） -->
        <el-form-item v-if="form.role === 0" label="班级" prop="classId">
          <el-select v-model="form.classId" placeholder="请选择班级" clearable style="width: 100%" @change="onSingleClassChange">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
          <!-- 学号规则提示：选中班级后自动带出该班下一个学号（与批量新增同一套规则） -->
          <div v-if="createSeqHint" class="seq-hint" :class="{ 'is-warn': createSeqWarn }">
            <el-icon v-if="createSeqLoading" class="is-loading"><Loading /></el-icon>
            {{ createSeqHint }}
          </div>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" placeholder="留空则默认 123456" maxlength="100" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 调班对话框（仅管理员可见） -->
    <el-dialog v-model="classDialogVisible" title="调整班级" width="480px" @closed="resetClassForm">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="学号/账号">{{ classForm.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ classForm.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前班级">{{ classNameOf({ classId: classForm.currentClassId }) || '未分配' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="调整到" required>
          <el-select v-model="classForm.classId" placeholder="请选择新班级（当前班级已禁选）" style="width: 100%">
            <el-option
              v-for="c in classes"
              :key="c.id"
              :label="c.className"
              :value="c.id"
              :disabled="c.id === classForm.currentClassId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classSubmitting" @click="submitClassChange">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量新增账号对话框（仅管理员可见）：统一设置 + 动态行 -->
    <el-dialog v-model="batchCreateVisible" title="批量新增账号" width="640px" top="4vh" @closed="resetBatchCreate">
      <el-form label-width="90px">
        <div class="batch-unified">
          <el-form-item label="账号角色" required style="margin-bottom: 12px">
            <el-tag :type="roleTag(batchForm.role)" effect="light">{{ roleText(batchForm.role) }}</el-tag>
            <span style="margin-left: 10px; color: #909399; font-size: 12px">当前子菜单已固定账号角色</span>
          </el-form-item>
          <el-form-item v-if="batchForm.role === 0" label="所属班级" required style="margin-bottom: 12px">
            <el-select
              v-model="batchForm.classId"
              filterable
              allow-create
              default-first-option
              placeholder="选择已有班级，或直接输入纯数字班级号"
              style="width: 100%"
              @change="onBatchClassChange"
            >
              <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
            </el-select>
            <!-- 学号规则提示：选中班级后自动算出本批起始学号（新班级 01 起，已有班级接着最后一个学号） -->
            <div v-if="studentSeqHint" class="seq-hint" :class="{ 'is-warn': seqFetchFailed }">
              <el-icon v-if="seqLoading" class="is-loading"><Loading /></el-icon>
              {{ studentSeqHint }}
            </div>
          </el-form-item>
          <el-form-item label="初始密码" style="margin-bottom: 0">
            <el-input v-model="batchForm.password" placeholder="留空则默认 123456" maxlength="100" style="width: 240px" />
          </el-form-item>
        </div>

        <div class="batch-rows-header">
          <span>账号明细（共 {{ batchRows.length }} 条）</span>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-tooltip content="以第 1 行学号为起点按序编号：手工填写的不覆盖，系统自动生成的会重算（支持前缀与前导零，如 2300580501 → 2300580502）。学生选班级后已自动编号，这里用于手工指定起始号" placement="top">
              <el-button type="success" link @click="fillSequentialUsernames">按首行顺序填充学号</el-button>
            </el-tooltip>
            <el-button type="primary" size="small" @click="addBatchRows">添加</el-button>
            <el-input-number v-model="addRowsCount" :min="1" :max="500" size="small" style="width: 110px" controls-position="right" />
            <span style="color:#909399;font-size:12px">行</span>
            <el-button type="danger" size="small" plain @click="clearBatchRows">清空</el-button>
          </div>
        </div>
        <div class="batch-rows">
          <div v-for="(r, i) in batchRows" :key="i" class="batch-row">
            <span class="batch-row-idx">{{ i + 1 }}</span>
            <div class="batch-row-field">
              <el-input
                v-model="r.username"
                :class="{ 'is-invalid': !!r.usernameError }"
                placeholder="学号/账号"
                maxlength="50"
                @input="onUsernameInput(r)"
                @blur="checkRowUsername(r)"
              />
              <div v-if="r.usernameError" class="row-error">{{ r.usernameError }}</div>
            </div>
            <el-input v-model="r.realName" placeholder="姓名" maxlength="50" style="width: 160px" />
            <el-button
              type="danger"
              link
              :disabled="batchRows.length <= 1"
              @click="removeBatchRow(i)"
            >删除</el-button>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="batchCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchCreating" @click="submitBatchCreate">确定新增</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, h, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Lock, Unlock, Key, Plus, Edit, Loading } from '@element-plus/icons-vue'
import { getUserPage, updateUserStatus, updateUserPassword, getClassList, createUser, updateStudentClass, createUserBatch, deleteUserBatch, checkUsernamesExist, createClassQuick } from '@/api/sysUser'
import { getClassStudentList } from '@/api/sysClass'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
// 仅管理员（role=2）拥有新增账号权限
const isAdmin = computed(() => authStore.userInfo?.role === 2)

// 路由 props：defaultRole 由 /account/admins|teachers|students 传入，固定该页面只显示某一角色
const props = defineProps({ defaultRole: { type: Number, default: null } })
const accountLabel = computed(() =>
  ({ 0: '学生账号', 1: '教师账号', 2: '管理员账号' })[props.defaultRole] || '账号'
)

/**
 * 是否展示「班级」相关内容（筛选下拉 / 表格列 / 调班按钮）。
 * 管理员账号（defaultRole === 2）不带班级属性，本页整体隐藏；
 * 学生（0）、教师（1）以及混合列表页（null）保持原样。
 */
const showClassField = computed(() => props.defaultRole !== 2)

/**
 * 角色是否被页面锁定：/account/admins|teachers|students 这类子菜单页由路由 props 固定角色，
 * 新增账号时角色已经是确定的（管理员页新增的必然是管理员），所以不展示角色下拉；
 * 只有未传 defaultRole 的混合列表页才需要手选角色。
 */
const roleLocked = computed(() => props.defaultRole !== null)

/** 账号示例文案随页面角色变化，避免在管理员页提示「如 teacher04」 */
const accountInputPlaceholder = computed(() =>
  ({ 0: '如 2300580501', 1: '如 teacher04', 2: '如 admin01' })[props.defaultRole] ||
  '如 2300580501 或 teacher04'
)

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  // 学号精确匹配、姓名模糊匹配（与后端 UserQuery.username / realName 对应）
  username: '',
  realName: '',
  role: props.defaultRole,
  classId: null
})

/**
 * 序号列的取值：表格自增索引只从 1 开始，翻到第 2 页会重新从 1 数，
 * 这里补上分页偏移量，让序号跨页连续（第 2 页第 1 行 = 11）。
 */
function indexMethod(index) {
  return (query.pageNum - 1) * query.pageSize + index + 1
}

const classes = ref([])

// 角色映射
const roleText = (r) => ({ 0: '学生', 1: '教师', 2: '管理员' })[r] || '未知'
const roleTag = (r) => ({ 0: 'primary', 1: 'success', 2: 'danger' })[r] || 'info'

// 班级中文名（根据 classId 在已加载班级列表中查找）
function classNameOf(row) {
  if (!row || !row.classId) return '-'
  const c = classes.value.find((x) => x.id === row.classId)
  return c ? c.className : '-'
}

// 123456 的 BCrypt 哈希值（系统默认密码，与数据库一致）
const DEFAULT_PWD_HASH = '$2a$10$E1xhrlZIiORPxpKHffnBAODR56fF2DLKbPPBPjMe3PTUUdVHfeBRq'

// ============ 批量新增账号（管理员） ============
const batchCreateVisible = ref(false)
const batchCreating = ref(false)
const batchForm = reactive({ role: props.defaultRole ?? 0, classId: null, password: '' })

/**
 * 明细行：autoUsername 标记该行学号是「系统按规则自动生成」的。
 * 自动生成的行允许被后续重算覆盖（改了班级/起始号后不留旧号），
 * 用户手输过的行只占用序号、不覆盖。
 * usernameError 保存该行的学号错误文案（红框 + 行内提示）。
 */
function newBatchRow() {
  return { username: '', realName: '', autoUsername: false, usernameError: '' }
}

const batchRows = ref([newBatchRow()])

function openBatchCreate() {
  batchForm.role = props.defaultRole ?? 0
  batchCreateVisible.value = true
}

function resetBatchCreate() {
  batchForm.role = props.defaultRole ?? 0
  batchForm.classId = null
  batchForm.password = ''
  batchRows.value = [newBatchRow()]
  resetSeqState()
}

const addRowsCount = ref(1)

function addBatchRows() {
  const n = Math.max(1, Math.min(500, Number(addRowsCount.value) || 1))
  // 单次总量保护：明细行数 + 新增行数不超过 200（与后端批量上限一致）
  if (batchRows.value.length + n > 200) {
    ElMessage.warning(`明细最多 200 行，当前 ${batchRows.value.length} 行，还能添加 ${200 - batchRows.value.length} 行`)
    return
  }
  for (let i = 0; i < n; i++) {
    batchRows.value.push(newBatchRow())
  }
  // 学生模式下新行直接按学号规则编号，省去再点一次「按首行顺序填充」
  fillStudentRows()
}

/** 删除一行明细：学生模式下剩余行自动重排学号，避免中间留空号 */
function removeBatchRow(i) {
  if (batchRows.value.length <= 1) return
  batchRows.value.splice(i, 1)
  fillStudentRows()
}

/** 清空全部明细行：已填内容的行需确认，纯空行直接清 */
function clearBatchRows() {
  const hasContent = batchRows.value.some(r => r.username.trim() || r.realName.trim())
  if (!hasContent) {
    batchRows.value = [newBatchRow()]
    fillStudentRows()
    return
  }
  ElMessageBox.confirm(`确定清空全部 ${batchRows.value.length} 行明细吗？`, '清空确认', {
    type: 'warning',
    confirmButtonText: '清空',
    cancelButtonText: '取消'
  })
    .then(() => {
      batchRows.value = [newBatchRow()]
      fillStudentRows()
    })
    .catch(() => {})
}

// ===== 学生学号规则：班级号 + 两位序号（新班级 01 起，已有班级接着最后一个学号） =====
const SEQ_DIGITS = 2

/** 序号游标：普通长度用 number；超长学号（超安全整数）用 BigInt，避免 +1 原地打转 */
function parseSeq(tail) {
  return tail.length > 15 ? BigInt(tail) : Number(tail)
}
function seqPlusOne(c) {
  return typeof c === 'bigint' ? c + BigInt(1) : c + 1
}

/** 由班级号 + 序号拼学号：('23005801', 1) → '2300580101'（序号上千时自然变 3 位，不截断） */
function buildStudentUsername(className, seq) {
  return className + String(seq).padStart(SEQ_DIGITS, '0')
}

// 班级已有学号信息（决定本批学号的起始序号）；lastSeq = 0 表示该班还没有学生
const classLastSeq = ref(0)
const classLastUsername = ref('')
const classStudentCount = ref(0)
const seqLoading = ref(false)
const seqFetchFailed = ref(false)
/** 上述数据对应的班级名：异步返回时用于判断是否已过期（用户又改了班级） */
const seqFetchedFor = ref('')
/** 并发令牌：班级快速切换时丢弃旧响应 */
let seqToken = 0

function resetSeqState() {
  seqToken++
  classLastSeq.value = 0
  classLastUsername.value = ''
  classStudentCount.value = 0
  seqLoading.value = false
  seqFetchFailed.value = false
  seqFetchedFor.value = ''
}

/**
 * 从一批学号里算出属于该班级的最大序号。
 * 只认「班级名 + 纯数字」形式的学号，手填的异常学号直接忽略，避免推算出离谱序号。
 */
function maxSeqOfClass(className, usernames) {
  let max = 0
  let last = ''
  usernames.forEach((u) => {
    const name = (u || '').trim()
    if (!name.startsWith(className)) return
    const tail = name.slice(className.length)
    if (!/^\d+$/.test(tail)) return
    const n = Number(tail)
    if (Number.isFinite(n) && n > max) {
      max = n
      last = name
    }
  })
  return { max, last }
}

/**
 * 按「前缀 + 序号」把明细行编号：空行、以及上一次自动生成的行都会重算，
 * 用户手输过的行只占用序号、不被覆盖。
 * 候选序号若已被占用（手输撞号）则继续往后找空位，因此重复点击不会产生重复学号。
 * @param {string} prefix 学号前缀（学生=班级号）
 * @param {number|bigint} startSeq 起始序号
 * @param {number} width 序号补零位数
 * @returns {number} 实际写入的行数
 */
function fillRowsWithSeq(prefix, startSeq, width) {
  const rows = batchRows.value
  // ① 手输的学号视为已占用：去前导零后用字符串比对（001 与 1 视为同号）
  const used = new Set()
  rows.forEach((r) => {
    if (r.autoUsername) return
    const u = (r.username || '').trim()
    if (!u.startsWith(prefix)) return
    const tail = u.slice(prefix.length)
    if (/^\d+$/.test(tail)) used.add(tail.replace(/^0+(?=\d)/, ''))
  })
  // ② 按行序编号
  let cursor = startSeq
  let assigned = 0
  rows.forEach((row) => {
    const cur = (row.username || '').trim()
    if (cur && !row.autoUsername) return // 手工填写的行不动
    while (used.has(String(cursor))) cursor = seqPlusOne(cursor) // 序号被占了 → 往后找空位
    used.add(String(cursor))
    row.username = prefix + String(cursor).padStart(width, '0')
    row.autoUsername = true
    cursor = seqPlusOne(cursor)
    assigned++
  })
  return assigned
}

/**
 * 本批学号应有的长度 = 班级号位数 + 序号位数。
 * 序号位数取 max(2, 本批最后序号位数)，与自动编号的补零位数完全一致：
 * 本批跨到 100 号时整批变 3 位序号，长度校验也随之跟着变，无需人工设置。
 * 非学生角色或未选班级时返回 0，表示不做长度校验。
 */
const expectedStudentUsernameLength = computed(() => {
  if (batchForm.role !== 0) return 0
  const className = currentBatchClassName()
  if (!className) return 0
  const lastSeq = classLastSeq.value + Math.max(1, batchRows.value.length)
  return className.length + Math.max(SEQ_DIGITS, String(lastSeq).length)
})

/**
 * 学生模式：按班级学号规则把明细空行补满。
 * 起始序号 = 该班已有最大序号 + 1（新班级/空班 → 01）。
 */
function fillStudentRows() {
  const className = currentBatchClassName()
  if (batchForm.role !== 0 || !className || seqFetchFailed.value) return 0
  const startSeq = classLastSeq.value + 1
  // 本批最后一个序号决定补零位数：本批跨到 100 时整批统一 3 位，避免同学号长度不一致
  const lastSeq = startSeq + batchRows.value.length - 1
  const width = Math.max(SEQ_DIGITS, String(lastSeq).length)
  const assigned = fillRowsWithSeq(className, startSeq, width)
  return assigned
}

/**
 * 选择/输入班级后重算起始序号：
 * 已有班级 → 查该班学生，取最大学号序号的下一号；新班级（库里还没有）→ 从 01 开始。
 * 取不到已有学号时不做自动编号，避免与库里学号撞号。
 */
async function refreshStudentSeq() {
  const className = currentBatchClassName()
  const token = ++seqToken
  classLastSeq.value = 0
  classLastUsername.value = ''
  classStudentCount.value = 0
  seqFetchFailed.value = false
  seqFetchedFor.value = ''
  if (batchForm.role !== 0 || !className) return

  // 换班级后，手输的旧班学号必然不合规（前缀对不上），清掉交给规则统一重排
  batchRows.value.forEach((r) => {
    const u = (r.username || '').trim()
    if (u && !u.startsWith(className)) {
      r.username = ''
      r.autoUsername = false
    }
  })

  const cls = classes.value.find((c) => c.className === className)
  if (!cls) {
    // 新班级：提交时才会创建，序号从 01 开始
    seqFetchedFor.value = className
    fillStudentRows()
    return
  }
  seqLoading.value = true
  try {
    const res = await getClassStudentList(cls.id)
    if (token !== seqToken) return // 期间又换了班级，丢弃过期结果
    const usernames = (res.data || []).map((s) => s.username)
    const { max, last } = maxSeqOfClass(className, usernames)
    classLastSeq.value = max
    classLastUsername.value = last
    classStudentCount.value = usernames.length
    seqFetchedFor.value = className
    fillStudentRows()
  } catch (e) {
    if (token !== seqToken) return
    seqFetchFailed.value = true
    seqFetchedFor.value = className
    ElMessage.warning('读取该班级已有学号失败，请手动填写学号')
  } finally {
    if (token === seqToken) seqLoading.value = false
  }
}

/** 学号规则提示：新班级从 01 开始 / 已有班级接着最后一个学号 */
const studentSeqHint = computed(() => {
  if (batchForm.role !== 0) return ''
  const className = currentBatchClassName()
  if (!className) return ''
  if (seqLoading.value) return `正在读取班级「${className}」已有学号…`
  if (seqFetchFailed.value && seqFetchedFor.value === className) return '未能读取该班级已有学号，请手动填写'
  if (seqFetchedFor.value !== className) return ''
  const start = buildStudentUsername(className, classLastSeq.value + 1)
  if (classLastSeq.value === 0) {
    const isNew = !classes.value.some((c) => c.className === className)
    return isNew ? `新建班级：学号从 ${start} 开始` : `该班暂无学生：学号从 ${start} 开始`
  }
  return `该班已有 ${classStudentCount.value} 名学生，最后学号 ${classLastUsername.value} → 本次起始学号 ${start}`
})

/**
 * 手动按钮：以第 1 行学号为起点，按「起始号 + 序号」填充明细行。
 * 学生模式下选班级时已自动编号，这里主要供手工指定起始号，或教师/管理员账号使用。
 * 支持前缀 + 数字尾（teacher01 → teacher02），保留前导零位数（001 → 002）。
 */
function fillSequentialUsernames() {
  const first = (batchRows.value[0]?.username || '').trim()
  if (!first) {
    ElMessage.warning('请先在第 1 行填写起始学号')
    return
  }
  // 拆分「前缀 + 数字尾」：2300580501 → ('', '2300580501')；teacher01 → ('teacher', '01')
  const m = first.match(/^(.*?)(\d+)$/)
  if (!m) {
    ElMessage.warning('起始学号需要以数字结尾才能按顺序填充（如 2300580501）')
    return
  }
  const prefix = m[1]
  const width = m[2].length
  // 第 1 行自己是起始号：先标记为手输，避免被当成"自动行"重算成别的号
  batchRows.value[0].autoUsername = false
  const assigned = fillRowsWithSeq(prefix, parseSeq(m[2]), width)
  if (assigned > 0) ElMessage.success(`已按顺序填充 ${assigned} 行学号`)
  else ElMessage.info('明细行均为手工填写，未做修改')
}

/**
 * 解析批量新增选择的班级：支持已有班级（id/名称匹配）和新输入的班级名（自动创建）
 * @returns {Promise<{id:number, name:string}>}
 */
async function resolveBatchClass() {
  const cv = batchForm.classId
  if (cv == null || String(cv).trim() === '') return null
  const input = String(cv).trim()
  // 按班级名称匹配（用户手输了一个已存在的班级名）
  const byName = classes.value.find(c => c.className === input)
  if (byName) return { id: byName.id, name: byName.className }
  // 按 id 匹配（下拉选择的已有班级）
  const byId = classes.value.find(c => String(c.id) === input)
  if (byId) return { id: byId.id, name: byId.className }
  // 新班级名规则兜底：只能由数字组成
  if (!/^\d+$/.test(input)) {
    ElMessage.warning('班级号只能由数字组成，请修正后重试')
    return 'invalid'
  }
  // 都没匹配上 → 视为新班级名，先创建班级再取 id
  const res = await createClassQuick(input)
  const created = res.data
  classes.value.push(created) // 加入下拉，下次可直接选
  ElMessage.success(`已自动创建班级「${created.className}」`)
  return { id: created.id, name: created.className }
}

/**
 * 班级选择/输入变化：
 * ① 实时校验：新建输入的班级名必须是纯数字，否则清空重填；
 * ② 按学生学号规则重算起始序号，并自动把明细空行编号。
 */
async function onBatchClassChange(val) {
  const input = val == null ? '' : String(val).trim()
  if (input) {
    const exists = classes.value.some(c => String(c.id) === input || c.className === input)
    if (!exists && !/^\d+$/.test(input)) {
      ElMessage.warning('班级号只能由数字组成，请重新输入')
      batchForm.classId = null
    }
  }
  await refreshStudentSeq()
}

async function submitBatchCreate() {
  // 组装完整账号列表，过滤空行
  const filled = batchRows.value.filter(r => r.username.trim() || r.realName.trim())
  if (!filled.length) {
    ElMessage.warning('请至少填写一条账号信息（学号 + 姓名）')
    return
  }
  const missing = filled.filter(r => !r.username.trim() || !r.realName.trim())
  if (missing.length) {
    ElMessage.warning(`有 ${missing.length} 行信息不完整，请补齐学号和姓名`)
    return
  }
  if (batchForm.role === 0 && (batchForm.classId == null || String(batchForm.classId).trim() === '')) {
    ElMessage.warning('批量新增学生请先选择所属班级')
    return
  }
  // 行内重复账号：标红到具体行。此处只提示不代改，避免提交前静默改掉用户输入
  const names = filled.map(r => r.username.trim())
  const dupNames = []
  filled.forEach((r, i) => {
    const first = names.indexOf(r.username.trim())
    if (first !== i) {
      r.usernameError = `与第 ${first + 1} 行重复`
      if (!dupNames.includes(r.username.trim())) dupNames.push(r.username.trim())
    }
  })
  if (dupNames.length) {
    ElMessage.warning(`以下账号在明细中重复填写，请修正：${dupNames.join('、')}`)
    return
  }

  try {
    // ① 解析班级（含新班级自动创建）
    let cls = null
    if (batchForm.role === 0) {
      cls = await resolveBatchClass()
      if (cls === 'invalid') return // 班级号格式不合法，已提示
    }

    // ② 学号长度校验：全部必须等于「班级号位数 + 序号位数」（长度由班级规则自动推导，页面无需设置）
    const len = expectedStudentUsernameLength.value
    if (len > 0) {
      const badLen = filled
        .map((r, i) => ({ idx: i + 1, username: r.username.trim(), row: r }))
        .filter(r => r.username.length !== len)
      badLen.forEach(r => { r.row.usernameError = `学号应为 ${len} 位，当前 ${r.username.length} 位` })
      if (badLen.length) {
        ElMessageBox.alert(
          `学号应由「班级号 + 序号」共 ${len} 位组成，以下 ${badLen.length} 行不合规：\n${badLen.map(r => `第 ${r.idx} 行：${r.username}（${r.username.length} 位）`).join('\n')}`,
          '学号长度校验不通过',
          { type: 'error', customStyle: { whiteSpace: 'pre-line' } }
        ).catch(() => {})
        return
      }
    }

    // ③ 学号前缀校验：学号前几位必须与班级名一致
    if (cls) {
      const badRows = filled
        .map((r, i) => ({ idx: i + 1, username: r.username.trim(), row: r }))
        .filter(r => !r.username.startsWith(cls.name))
      badRows.forEach(r => { r.row.usernameError = `学号应以班级号「${cls.name}」开头` })
      if (badRows.length) {
        ElMessageBox.alert(
          `学号前几位必须与班级名「${cls.name}」一致，以下 ${badRows.length} 行不合规：\n${badRows.map(r => `第 ${r.idx} 行：${r.username}`).join('\n')}`,
          '学号前缀校验不通过',
          { type: 'error', customStyle: { whiteSpace: 'pre-line' } }
        ).catch(() => {})
        return
      }
    }

    // ④ 数据库重复校验：已存在的学号直接提醒并中止
    const existingRes = await checkUsernamesExist(names)
    const exists = existingRes.data || []
    if (exists.length) {
      filled.forEach((r) => {
        if (exists.includes(r.username.trim())) r.usernameError = '该学号在系统中已存在'
      })
      ElMessage.warning(`以下学号已存在，请修改后重试：${exists.join('、')}`)
      return
    }

    batchCreating.value = true
    const users = filled.map(r => ({
      username: r.username.trim(),
      realName: r.realName.trim(),
      role: batchForm.role,
      classId: cls ? cls.id : null,
      password: batchForm.password || null,
      status: 1
    }))
    const res = await createUserBatch(users)
    const { success, skipped } = res.data || {}
    if (skipped > 0) ElMessage.warning(`批量新增完成：成功 ${success} 条，跳过 ${skipped} 条（账号已存在）`)
    else ElMessage.success(`批量新增成功（${success} 条）`)
    batchCreateVisible.value = false
    query.pageNum = 1
    loadData()
  } catch (err) {
    console.error('批量新增失败:', err)
  } finally {
    batchCreating.value = false
  }
}

/** 当前批量新增选择的班级名（同步解析，不触发建班请求；新建班级名直接用输入值） */
function currentBatchClassName() {
  if (batchForm.role !== 0) return ''
  const cv = batchForm.classId
  if (cv == null || String(cv).trim() === '') return ''
  const input = String(cv).trim()
  const byName = classes.value.find(c => c.className === input)
  if (byName) return byName.className
  const byId = classes.value.find(c => String(c.id) === input)
  if (byId) return byId.className
  return input
}

/** 学号每次输入：标记为手工输入（后续自动重排不再覆盖它），并清掉上一次的红字 */
function onUsernameInput(row) {
  row.autoUsername = false
  row.usernameError = ''
}

/**
 * 学号重复处理：提示「修改失败」+ 把这一行按班级学号规则恢复成一个未被占用的号码。
 *
 * 只对「重复」这种确定性错误动手：用户本意就是按班级规则编号，只是号码被占了，
 * 所以直接给他换下一个可用号（等于回到自动生成的那个学号）；
 * 前缀/长度这类可能只是"还没输完"的错误只标红提醒，不覆盖用户输入。
 * 取不到班级规则（非学生角色 / 班级学号读取失败）时只标红、不动用户输入。
 */
function repairDuplicateRow(row, reason) {
  const clsName = currentBatchClassName()
  if (!clsName || seqFetchFailed.value) {
    row.usernameError = reason
    ElMessage.warning(`修改失败，${reason}，请更换其他学号`)
    return
  }
  // 腾空后交给规则重排：该行按行序拿到当前可用的最小序号，其余自动行同步重算
  row.username = ''
  row.autoUsername = false
  fillStudentRows()
  row.usernameError = ''
  ElMessage.warning(`修改失败，${reason}，已恢复为「${row.username}」`)
}

/**
 * 学号输入框失焦时实时校验：
 * 前缀是否等于班级名 → 长度是否符合规则 → 是否与明细其它行重复 → 是否已存在于系统。
 * 重复的一律提示 + 按规则改回一个可用学号（不能只提示、把错误值留在明细里）。
 */
async function checkRowUsername(row) {
  const name = (row.username || '').trim()
  row.usernameError = ''
  if (!name) return
  // ① 前缀校验：学号前几位必须与班级名一致
  const clsName = currentBatchClassName()
  if (clsName && !name.startsWith(clsName)) {
    row.usernameError = `学号应以班级号「${clsName}」开头`
    ElMessage.warning(`学号应以班级号「${clsName}」开头，当前输入的是「${name.slice(0, clsName.length)}」`)
    return
  }
  // ② 长度校验：应为「班级号位数 + 序号位数」（长度由班级规则自动推导）
  const len = expectedStudentUsernameLength.value
  if (len > 0 && name.length !== len) {
    row.usernameError = `学号应为 ${len} 位，当前 ${name.length} 位`
    ElMessage.warning(`学号应由「班级号 + 序号」共 ${len} 位组成，当前输入的是 ${name.length} 位`)
    return
  }
  // ③ 明细内重复：先查本地，省一次请求
  const dupIdx = batchRows.value.findIndex(r => r !== row && (r.username || '').trim() === name)
  if (dupIdx > -1) {
    repairDuplicateRow(row, `学号「${name}」与第 ${dupIdx + 1} 行重复`)
    return
  }
  // ④ 系统内重复（数据库里已存在）
  try {
    const res = await checkUsernamesExist([name])
    // 请求期间用户又改了这一行 → 不再按旧值处理
    if ((row.username || '').trim() !== name) return
    if ((res.data || []).length) repairDuplicateRow(row, `学号「${name}」已存在`)
  } catch (e) {
    // 静默失败：实时提醒仅为辅助，提交时还有兜底校验
  }
}

// ============ 批量删除账号（管理员） ============
const selectedRows = ref([])
const batchDeleting = ref(false)
const tableRef = ref(null)

/**
 * 「最后一个管理员不可删」的前端锁定条件。
 * 只在管理员账号页（defaultRole === 2）且列表未做任何筛选时成立：
 * 此时 total 就是系统里管理员的总数，等于 1 说明只剩最后一个，禁止勾选，
 * 避免误删后无人能登录后台（后端也有保护，这里只是提前拦住误操作）。
 * 一旦输入了关键词或选了班级，total 只是命中条数、判断不准，就交回后端保护。
 */
const lastAdminLocked = computed(() =>
  isAdmin.value &&
  props.defaultRole === 2 &&
  !query.username.trim() &&
  !query.realName.trim() &&
  query.classId == null &&
  total.value === 1
)

/** 勾选框是否可用：锁定态整表禁用 */
function canSelectRow() {
  return !lastAdminLocked.value
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  const ids = selectedRows.value.map(r => r.id)
  if (!ids.length) return
  const hasAdmin = selectedRows.value.some(r => r.username === 'admin')
  try {
    await ElMessageBox.confirm(
      h('div', null, [
        h('p', { style: 'margin:0 0 8px' }, `确定删除选中的 ${ids.length} 个账号吗？删除后不可恢复！`),
        h('p', { style: 'margin:0;color:#e6a23c;font-size:12px' },
          hasAdmin
            ? '选中的 admin 超级管理员账号受系统保护，将自动跳过不删除'
            : '系统需至少保留一个管理员账号，若本次删除会导致没有管理员，相关账号将自动跳过')
      ]),
      '批量删除确认',
      {
        type: 'error',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true
      }
    )
  } catch (e) {
    return
  }
  batchDeleting.value = true
  try {
    const res = await deleteUserBatch(ids)
    const result = res.data || { success: 0, skipped: 0, skippedReasons: [] }
    const deleted = result.success || 0
    const skipped = result.skipped || 0
    if (skipped > 0) {
      const reasons = (result.skippedReasons || []).slice(0, 8)
      ElMessageBox.alert(
        h('div', null, [
          h('p', { style: 'margin:0 0 8px' }, `成功删除 ${deleted} 个，跳过 ${skipped} 个受保护账号：`),
          h('ul', { style: 'margin:0;padding-left:18px;color:#e6a23c;font-size:12px' },
            reasons.map(r => h('li', { style: 'margin:2px 0' }, r)))
        ]),
        '删除结果',
        { type: 'warning', confirmButtonText: '知道了' }
      ).catch(() => {})
    } else {
      ElMessage.success(`批量删除成功（${deleted} 个）`)
    }
    loadData()
  } catch (err) {
    console.error('批量删除失败:', err)
  } finally {
    batchDeleting.value = false
  }
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

/**
 * 查询序号：姓名实时查询会连着发请求，网络乱序返回时用它丢弃过期结果，
 * 避免先发的旧结果盖掉后发的新结果。
 */
let loadToken = 0

async function loadData() {
  cancelNameSearch() // 本次查询已涵盖待触发的实时查询，取消它避免重复请求
  const token = ++loadToken
  loading.value = true
  try {
    const res = await getUserPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username.trim() || undefined,
      realName: query.realName.trim() || undefined,
      role: query.role == null ? undefined : query.role,
      classId: query.classId == null ? undefined : query.classId
    })
    if (token !== loadToken) return // 已有更新的查询在跑，这次结果作废
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
    // 若本次加载后进入锁定态（例如刚删到只剩 1 个管理员），清掉遗留勾选，
    // 否则「批量删除」按钮会带着一个已勾选但不可删的账号亮着
    if (lastAdminLocked.value) {
      selectedRows.value = []
      await nextTick()
      tableRef.value?.clearSelection()
    }
  } catch (err) {
    if (token !== loadToken) return
    console.error('加载用户列表失败:', err)
    ElMessage.error('加载失败：' + (err.message || '网络异常'))
  } finally {
    if (token === loadToken) loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.username = ''
  query.realName = ''
  query.role = props.defaultRole
  query.classId = null
  query.pageNum = 1
  loadData()
}

/**
 * 姓名边输边查：敲一个字下面表格就跟着更新（300ms 防抖，连续输入只在停顿后发一次请求）。
 * 学号是精确查询，必须输完整才有意义，所以只走回车 /「查询」按钮，不做实时。
 */
let nameSearchTimer = null

/** 取消尚未触发的实时查询（点查询/重置/翻页时用，避免和防抖撞成两次请求） */
function cancelNameSearch() {
  if (nameSearchTimer) {
    clearTimeout(nameSearchTimer)
    nameSearchTimer = null
  }
}

watch(
  () => query.realName,
  () => {
    cancelNameSearch()
    nameSearchTimer = setTimeout(() => {
      nameSearchTimer = null
      handleSearch() // 条件变了回到第 1 页
    }, 300)
  }
)

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const actionText = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionText}账号「${row.realName}（${row.username}）」吗？`,
      `${actionText}确认`,
      { type: 'warning' }
    )
    const res = await updateUserStatus(row.id, newStatus)
    if (res.code === 200) {
      ElMessage.success(`${actionText}成功`)
      loadData()
    } else {
      ElMessage.error(res.msg || `${actionText}失败`)
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(`${actionText}失败：${e.message || '未知错误'}`)
    }
  }
}

async function handleResetPwd(row) {
  try {
    await ElMessageBox.confirm(
      `将「${row.realName}（${row.username}）」的密码重置为默认密码 123456 吗？`,
      '重置密码',
      { type: 'warning' }
    )
    const res = await updateUserPassword(row.id, DEFAULT_PWD_HASH)
    if (res.code === 200) {
      ElMessage.success('密码已重置为 123456')
    } else {
      ElMessage.error(res.msg || '重置失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('重置失败：' + (e.message || '未知错误'))
    }
  }
}

onMounted(() => {
  loadClasses()
  loadData()
})

// 加载班级列表（用于筛选下拉框）
async function loadClasses() {
  try {
    const res = await getClassList()
    classes.value = res.data || []
  } catch (err) {
    console.error('加载班级列表失败:', err)
  }
}

// ============ 新增账号（管理员） ============
const createVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  username: '',
  realName: '',
  role: null,
  classId: null,
  password: '',
  phone: ''
})
const createRules = {
  username: [{ required: true, message: '请输入学号/账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

// 选班级后自动填充学号（与批量新增同一套规则：班级号 + 两位序号）
/** 当前学号是否为系统自动填充；用户手改过就不再被班级选择覆盖 */
const usernameAutoFilled = ref(false)
const createSeqLoading = ref(false)
const createSeqHint = ref('')
const createSeqWarn = ref(false)
/** 并发令牌：快速换班时丢弃过期响应 */
let singleSeqToken = 0

function openCreateDialog() {
  resetCreateForm()
  // 页面已按班级筛选时带上该班级并顺手填出学号，省去再选一次班级
  if (form.role === 0 && query.classId) {
    form.classId = query.classId
    onSingleClassChange(query.classId)
  }
  createVisible.value = true
}

function resetCreateForm() {
  singleSeqToken++ // 作废进行中的班级学号请求，避免异步回来写脏表单
  usernameAutoFilled.value = false
  createSeqLoading.value = false
  createSeqHint.value = ''
  createSeqWarn.value = false
  form.username = ''
  form.realName = ''
  // 子菜单页角色固定，直接预置为当前页角色（同时满足 createRules 里 role 必填校验）
  form.role = props.defaultRole ?? null
  form.classId = null
  form.password = ''
  form.phone = ''
  formRef.value?.clearValidate?.()
}

/** 用户手动动过学号 → 视为手填，之后选班级不再覆盖他输入的内容 */
function onSingleUsernameInput() {
  usernameAutoFilled.value = false
}

/**
 * 学生模式下选择班级后自动填充学号：
 * 读该班已有学号 → 取最大序号 + 1（空班从 01 起）→ 写进学号输入框。
 * 只在「学号为空」或「还是上次自动填的值」时覆盖，不覆盖用户手输的学号；
 * 清空班级时同步清掉按该班规则填的学号，避免留着一个不属于任何班的号。
 */
async function onSingleClassChange(classId) {
  const token = ++singleSeqToken
  createSeqHint.value = ''
  createSeqWarn.value = false
  const cls = classes.value.find((c) => c.id === classId)
  if (!cls) {
    if (usernameAutoFilled.value) {
      form.username = ''
      usernameAutoFilled.value = false
    }
    return
  }
  createSeqLoading.value = true
  try {
    const res = await getClassStudentList(cls.id)
    if (token !== singleSeqToken) return // 期间又换了班级，丢弃过期结果
    const usernames = (res.data || []).map((s) => s.username)
    const { max, last } = maxSeqOfClass(cls.className, usernames)
    const next = buildStudentUsername(cls.className, max + 1)
    const canFill = !form.username.trim() || usernameAutoFilled.value
    if (canFill) {
      form.username = next
      usernameAutoFilled.value = true
      formRef.value?.clearValidate?.('username') // 自动填上了，清掉"请输入学号"的红字
    }
    if (!canFill) {
      createSeqHint.value = `该班已有 ${usernames.length} 名学生，最后学号 ${last || '-'}（学号已手动填写，未覆盖）`
    } else if (max === 0) {
      createSeqHint.value = `该班暂无学生：学号从 ${next} 开始`
    } else {
      createSeqHint.value = `该班已有 ${usernames.length} 名学生，最后学号 ${last} → 本次学号 ${next}`
    }
  } catch (e) {
    if (token !== singleSeqToken) return
    createSeqWarn.value = true
    createSeqHint.value = '未能读取该班级已有学号，请手动填写学号'
  } finally {
    if (token === singleSeqToken) createSeqLoading.value = false
  }
}

/**
 * 切换角色时清掉已选班级：
 * 班级字段只在「学生」下显示，若用户先选了班级再改成教师/管理员，
 * classId 会残留在表单里被一起提交，这里显式清空。
 */
function onCreateRoleChange(role) {
  if (role !== 0) form.classId = null
}

async function submitCreate() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        username: form.username.trim(),
        realName: form.realName.trim(),
        role: form.role,
        classId: form.classId == null ? null : form.classId,
        password: form.password ? form.password : undefined,
        phone: form.phone ? form.phone.trim() : null
      }
      await createUser(payload)
      ElMessage.success('账号创建成功')
      createVisible.value = false
      loadData()
    } catch (e) {
      // 业务错误已由响应拦截器弹窗提示，这里仅兜底
      console.error('创建账号失败:', e)
    } finally {
      submitting.value = false
    }
  })
}

// ============ 调班（管理员，双入口统一接口） ============
const classDialogVisible = ref(false)
const classSubmitting = ref(false)
const classForm = reactive({
  id: null,
  username: '',
  realName: '',
  classId: null,
  currentClassId: null
})

function openClassDialog(row) {
  classForm.id = row.id
  classForm.username = row.username
  classForm.realName = row.realName
  classForm.classId = null
  classForm.currentClassId = row.classId ?? null
  classDialogVisible.value = true
}

function resetClassForm() {
  classForm.id = null
  classForm.username = ''
  classForm.realName = ''
  classForm.classId = null
  classForm.currentClassId = null
}

async function submitClassChange() {
  if (!classForm.id) return
  // 边界校验：调班必须选择目标班级，且不能与当前班级相同
  if (!classForm.classId) {
    ElMessage.warning('请选择目标班级')
    return
  }
  if (classForm.classId === classForm.currentClassId) {
    ElMessage.warning('不能调班到当前所在班级')
    return
  }
  classSubmitting.value = true
  try {
    // 复用批量接口：单人调班传单元素数组
    await updateStudentClass([classForm.id], classForm.classId)
    ElMessage.success('调班成功')
    classDialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('调班失败:', e)
  } finally {
    classSubmitting.value = false
  }
}
</script>

<style scoped>
.student-list {
  padding: 0;
}

.search-card :deep(.el-form-item) {
  margin-bottom: 0;
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

/* 子菜单页「角色已固定」提示 */
.role-locked-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

/* 「仅剩 1 个管理员」锁定提示 */
.lock-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #e6a23c;
  font-size: 12px;
}

.time-cell {
  color: #606266;
  font-family: Consolas, monospace;
  font-size: 13px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
/* ===== 批量新增弹窗 ===== */
.batch-unified {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 16px 16px 4px;
  margin-bottom: 16px;
}
.batch-rows-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  color: #606266;
  font-size: 13px;
}
.batch-rows {
  max-height: 300px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}
.batch-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.batch-row-idx {
  min-width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  background: #909399;
  color: #fff;
  border-radius: 4px;
  font-size: 12px;
}
/* 学号列：输入框 + 下方错误提示（红字） */
.batch-row-field {
  width: 220px;
  flex: none;
}
.row-error {
  margin-top: 2px;
  color: #f56c6c;
  font-size: 12px;
  line-height: 16px;
}
.batch-row-field :deep(.is-invalid .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
/* 学号规则提示（班级下拉框下方）：width:100% 让它在 el-form-item 的 flex 容器里换行显示 */
.seq-hint {
  width: 100%;
  margin-top: 4px;
  color: #409eff;
  font-size: 12px;
  line-height: 18px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.seq-hint.is-warn {
  color: #e6a23c;
}
</style>
