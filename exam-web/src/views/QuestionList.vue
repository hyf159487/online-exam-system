<template>
  <div class="question-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="学科">
          <el-select v-model="query.courseId" placeholder="全部学科" clearable filterable style="width: 180px">
            <el-option
              v-for="c in courseList"
              :key="c.id"
              :label="c.courseName"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="题型">
          <el-select v-model="query.questionType" placeholder="全部" clearable style="width: 120px">
            <el-option
              v-for="item in questionTypeOptions"
              :key="item.itemValue"
              :label="item.itemLabel"
              :value="Number(item.itemValue)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="难度">
          <el-select v-model="query.difficulty" placeholder="全部" clearable style="width: 120px">
            <el-option
              v-for="item in difficultyOptions"
              :key="item.itemValue"
              :label="item.itemLabel"
              :value="Number(item.itemValue)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="题干关键词">
          <el-input v-model="query.keyword" placeholder="输入题干文字模糊检索" clearable
            style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据范围说明：本页 = 审核已通过的题库；待审核 / 已驳回的题目在「待审核试题」里处理 -->
      <div class="scope-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>
          本页只显示<b>审核已通过</b>的题目，新增或编辑后题目会重新进入待审核，通过后才回到这里。
          待审核 / 已驳回的题目请在「待审核试题」中查看处理。
        </span>
      </div>
  </el-card>

  <!-- 表格 -->
  <el-card style="margin-top: 16px">
      <!-- 顶部操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增题目</el-button>
        <!-- 批量操作入口：进入/退出批量选择模式 -->
        <el-button v-if="!batchMode" :icon="Operation" @click="enterBatchMode">批量操作</el-button>
        <template v-else>
          <el-button type="danger" :icon="Delete" @click="handleBatchDelete">
            批量删除
            <!-- 角标用 selectedIds.size 而非 selectedRows.length，后者仅含当前页可找到的行，跨页其他行仍在 Map 中保留但不计入长度 → 会导致"选了 20 只显示 10" -->
            <span v-if="selectedIds.size">（{{ selectedIds.size }}）</span>
          </el-button>
          <el-button plain @click="cancelBatchMode">退出批量模式</el-button>
          <el-button type="primary" plain @click="openBatchKnowledge">批量设知识点</el-button>
          <el-button type="warning" plain @click="openAiMatch">🤖 AI 匹配知识点</el-button>
        </template>
        <el-dropdown split-button type="default" @command="handleSortChange">
          {{ sortLabel }}
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="createTime_desc">创建时间（最新在前）</el-dropdown-item>
              <el-dropdown-item command="createTime_asc">创建时间（最早在前）</el-dropdown-item>
              <el-dropdown-item command="course_asc">学科（A-Z）</el-dropdown-item>
              <el-dropdown-item command="questionType_asc">题型</el-dropdown-item>
              <el-dropdown-item command="difficulty_asc">难度（简单→困难）</el-dropdown-item>
              <el-dropdown-item command="score_desc">分值（高→低）</el-dropdown-item>
              <el-dropdown-item command="score_asc">分值（低→高）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button :icon="Refresh" :loading="loading" @click="handleRefresh">刷新</el-button>
      </div>

      <el-table
        ref="tableRef"
        :data="tableData"
        v-loading="loading"
        border
        style="width: 100%"
        row-key="id"
        :header-cell-style="{ textAlign: 'center' }"
        :row-class-name="rowClassName"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="batchMode" type="selection" width="50" reserve-selection />
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (query.pageNum - 1) * query.pageSize + $index + 1 }}
          </template>
        </el-table-column>

        <el-table-column prop="courseName" label="学科" width="170" align="center">
          <template #default="{ row }">
            <el-tag type="primary" effect="plain">{{ row.courseName || '未分配' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="knowledgeName" label="知识点" min-width="160">
          <template #default="{ row }">
            <el-tooltip
              v-if="kpNames(row).length"
              :content="kpNames(row).join('、')"
              placement="top"
              :show-after="300"
            >
              <div class="kp-cell">
                <el-tag
                  v-for="n in kpNames(row)"
                  :key="n"
                  size="small"
                  type="info"
                  effect="plain"
                  class="kp-tag"
                >{{ n }}</el-tag>
              </div>
            </el-tooltip>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="questionType" label="题型" width="80">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.questionType)">
              {{ typeText(row.questionType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="content" label="题干" min-width="280" show-overflow-tooltip />

        <el-table-column prop="difficulty" label="难度" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="diffTag(row.difficulty)" effect="plain">
              {{ diffText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="score" label="分值" width="70" align="center" />

        <el-table-column prop="submitterName" label="提交者" width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.submitterName || '-' }}</template>
        </el-table-column>

        <el-table-column prop="createTime" label="提交时间" width="165" />

        <!-- 本页只有审核已通过的题目，审核人 / 审核时间必然存在（兜底 - 防止历史脏数据） -->
        <el-table-column prop="auditorName" label="审核人" width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditorName || '-' }}</template>
        </el-table-column>

        <el-table-column prop="auditTime" label="审核时间" width="165">
          <template #default="{ row }">{{ row.auditTime || '-' }}</template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; justify-content: center; flex-wrap: nowrap; white-space: nowrap;">
              <el-button type="success" link size="small" @click="handleDetail(row)">详情</el-button>
              <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[5, 10, 15, 20, 30, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 编辑对话框（新增已统一走批量界面） -->
    <QuestionFormDialog
      v-model="dialogVisible"
      :question="currentQuestion"
      :preset="addPreset"
      @success="handleFormSuccess"
    />

    <!-- 批量添加对话框 -->
    <QuestionBatchDialog
      v-model="batchDialogVisible"
      :preset="addPreset"
      @success="handleBatchSuccess"
    />

    <!-- 批量设置知识点对话框 -->
    <QuestionKnowledgeBatchDialog
      v-model="kbDialogVisible"
      :questions="selectedRows"
      @success="handleKnowledgeSuccess"
    />

    <!-- AI 智能匹配知识点对话框 -->
    <QuestionAiMatchDialog
      v-model="aiMatchDialogVisible"
      :questions="selectedRows"
      @success="handleKnowledgeSuccess"
    />

    <!-- 详情对话框（只读） -->
    <el-dialog v-model="detailVisible" title="题目详情" width="640px">
      <div v-if="detailData" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ detailData.id }}</el-descriptions-item>
          <el-descriptions-item label="学科">{{ detailData.courseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="题型">{{ typeText(detailData.questionType) }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ diffText(detailData.difficulty) }}</el-descriptions-item>
          <el-descriptions-item label="分值">{{ detailData.score }}</el-descriptions-item>
          <el-descriptions-item label="提交者">{{ detailData.submitterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detailData.createTime }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag v-if="detailData.auditStatus === 0" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="detailData.auditStatus === 1" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="detailData.auditStatus === 2" type="danger" size="small">已驳回</el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="审核人">{{ detailData.auditorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ detailData.auditTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间" :span="2">{{ detailData.updateTime || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-descriptions :column="1" border style="margin-top: 12px">
          <el-descriptions-item label="题干">
            <div class="detail-content-text">{{ detailData.content }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.auditStatus === 2" label="驳回意见">
            <div class="detail-reject">{{ detailData.auditOpinion || '无驳回意见' }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.questionType === 1 || detailData.questionType === 2" label="选项">
            <div class="detail-options">
              <div
                v-for="(opt, idx) in parseOptions(detailData.options)"
                :key="idx"
                class="detail-option-item"
              >
                <span class="detail-option-label">{{ String.fromCharCode(65 + idx) }}.</span>
                <span>{{ opt }}</span>
              </div>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="参考答案">
            <div class="detail-content-text">{{ detailData.answer }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.analysis" label="解析">
            <div class="detail-content-text">{{ detailData.analysis }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailData.knowledgeId || kpNames(detailData).length" label="知识点">
            <div v-if="kpNames(detailData).length" class="kp-cell-wrap">
              <el-tag
                v-for="n in kpNames(detailData)"
                :key="n"
                size="small"
                type="info"
                effect="plain"
                class="kp-tag"
              >{{ n }}</el-tag>
            </div>
            <span v-else>未绑定</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Refresh, Plus, Delete, Operation, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQuestionList, getQuestionById, deleteQuestion, batchDeleteQuestions } from '@/api/question'
import { getCourseList } from '@/api/course'
import { getDictItemsByCode } from '@/api/dict'
import QuestionFormDialog from './QuestionFormDialog.vue'
import QuestionBatchDialog from './QuestionBatchDialog.vue'
import QuestionKnowledgeBatchDialog from './QuestionKnowledgeBatchDialog.vue'
import QuestionAiMatchDialog from './QuestionAiMatchDialog.vue'

const route = useRoute()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

/** 学科下拉数据 */
const courseList = ref([])

/** 字典下拉数据（从字典接口动态获取） */
const questionTypeOptions = ref([])
const difficultyOptions = ref([])

async function loadDictOptions() {
  try {
    const [typesRes, diffRes] = await Promise.all([
      getDictItemsByCode('question_type'),
      getDictItemsByCode('difficulty')
    ])
    questionTypeOptions.value = typesRes.data || []
    difficultyOptions.value = diffRes.data || []
  } catch (e) {
    // 字典接口失败不阻塞业务：兜底用原硬编码值
    questionTypeOptions.value = [
      { itemLabel: '单选题', itemValue: '1' },
      { itemLabel: '多选题', itemValue: '2' },
      { itemLabel: '判断题', itemValue: '3' },
      { itemLabel: '填空题', itemValue: '4' },
      { itemLabel: '简答题', itemValue: '5' },
      { itemLabel: '名词解释', itemValue: '6' }
    ]
    difficultyOptions.value = [
      { itemLabel: '简单', itemValue: '1' },
      { itemLabel: '中等', itemValue: '2' },
      { itemLabel: '困难', itemValue: '3' }
    ]
  }
}

/**
 * 本页（我的试题）固定只查「审核已通过」的题目：
 * 待审核(0) / 已驳回(2) 的题目归「试题审核 → 待审核试题」处理，因此这里不再提供审核状态筛选，
 * 查询参数也不由用户控制（新增/编辑后题目会被重置为待审核，审核通过后才会回到本页）。
 */
const ONLY_PASSED = 1

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  id: null,
  courseId: null,
  questionType: null,
  difficulty: null,
  keyword: '',
  sortBy: 'createTime_desc'
})

/** 排序标签映射 */
const sortLabelMap = {
  createTime_desc: '创建时间（最新在前）',
  createTime_asc: '创建时间（最早在前）',
  course_asc: '学科（A-Z）',
  questionType_asc: '题型',
  difficulty_asc: '难度（简单→困难）',
  score_desc: '分值（高→低）',
  score_asc: '分值（低→高）'
}
const sortLabel = computed(() => sortLabelMap[query.sortBy] || '排序')

// 对话框状态
const dialogVisible = ref(false)
const currentQuestion = ref(null)
/** 从其他页面跳转过来时，新增弹窗的预填信息（如科目、题型） */
const addPreset = ref(null)

/** 批量设置知识点弹窗 */
const kbDialogVisible = ref(false)
/** AI 智能匹配知识点弹窗 */
const aiMatchDialogVisible = ref(false)

/** 详情弹窗 */
const detailVisible = ref(false)
const detailData = ref(null)

/** 表格实例（用于清空多选） */
const tableRef = ref(null)
/** 是否处于批量删除模式（勾选列在此模式下才显示） */
const batchMode = ref(false)
/**
 * 跨页全局选中的题目 ID 集合（单一事实源 source of truth）。
 * - 所有勾选变化必须通过 handleSelectionChange 基于"本页 delta"更新此 Set
 * - 翻页 / 刷新 / 筛选后：restoreSelectedOnPage 按此 Set 恢复本页 UI 勾选
 * - 避免了 Element Plus selection-change 返回"仅当前页"rows 直接覆盖导致跨页勾选丢失
 */
const selectedIds = ref(new Set())
/**
 * 跨页缓存：勾选题目的完整 row 对象快照（id => 浅克隆行）。
 * 用户在某一页勾选题目时，当场把 row 存进此 Map，即使切到其他页，selectedRows 仍能取出完整题目
 * 用于：①跨页合计勾选数量；②批量删除时汇总 ids；③批量设置知识点 / AI 匹配时提供完整题目对象
 * Map 中可能有不存在于 selectedIds 的条目（用户曾经选过又取消），以 selectedIds 为准。
 */
const selectedRowMap = ref(new Map())
/**
 * 当前勾选题目的完整对象集合（跨页）：
 * - 先按 selectedIds 遍历所有选中 id（保证数量正确）
 * - 对每个 id，优先取 selectedRowMap 中缓存的完整快照；其次在当前页 tableData 里兜底找；
 *   都没有就至少保留 {id}（批量删除接口只要 id，比"跨页选了删不掉"强）
 * - 这个 computed 的长度 = selectedIds.size，绝不会出现"选了 20 只显示 10"
 */
const selectedRows = computed(() => Array.from(selectedIds.value).map((id) => {
  if (selectedRowMap.value.has(id)) return selectedRowMap.value.get(id)
  const inPage = tableData.value.find((r) => r.id === id)
  if (inPage) return inPage
  return { id, title: `题目#${id}（已缓存缺失）` }
}))

/** 批量添加弹窗 */
const batchDialogVisible = ref(false)

/** 题目的知识点名称数组：优先后端聚合串 knowledgeNames（顿号分隔），兜底主知识点名 */
function kpNames(row) {
  if (!row) return []
  if (row.knowledgeNames) {
    const arr = String(row.knowledgeNames)
      .split('、')
      .filter((s) => s)
    if (arr.length) return arr
  }
  return row.knowledgeName ? [row.knowledgeName] : []
}

// 题型映射
function typeText(t) {
  return { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }[t] || '未知'
}
function typeTag(t) {
  return { 1: '', 2: 'success', 3: 'warning', 4: 'info', 5: 'danger' }[t] || ''
}

// 难度映射
function diffText(d) {
  return { 1: '简单', 2: '中等', 3: '困难' }[d] || '未知'
}
function diffTag(d) {
  return { 1: 'success', 2: 'warning', 3: 'danger' }[d] || ''
}

async function fetchData() {
  loading.value = true
  try {
    // auditStatus 由页面固定为「已通过」，不接受用户输入，保证本页只出现审核通过的题目
    const res = await getQuestionList({ ...query, auditStatus: ONLY_PASSED })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
    // 关键：数据 DOM 渲染完成后，根据全局 selectedIds 恢复本页的勾选 UI
    await nextTick()
    restoreSelectedOnPage()
  } catch (err) {
    console.error('查询失败:', err)
  } finally {
    loading.value = false
  }
}

/**
 * 根据全局 selectedIds 恢复本页所有需要勾选行的 UI 状态。
 * 注意：使用表格自带的 toggleRowSelection，因为 el-table + reserve-selection 本身会按 row-key(id)
 * 保留已选条目，但在 tableData 数组引用整体替换后需要手动勾一次，确保与 selectedIds 完全一致。
 */
function restoreSelectedOnPage() {
  if (!tableRef.value) return
  if (!batchMode.value) return
  if (!tableData.value.length) return
  const set = selectedIds.value
  try {
    tableData.value.forEach((row) => {
      const shouldSelect = set.has(row.id)
      tableRef.value.toggleRowSelection(row, shouldSelect)
    })
  } catch (e) { /* ignore */ }
}

/**
 * 全局清空 selectedIds + 同步清空表格 UI 勾选
 * 用在"进入批量模式（全新一组勾选起点）/退出批量模式/更换筛选条件"语义。
 */
function clearSelectionGlobal() {
  selectedIds.value = new Set()
  selectedRowMap.value = new Map()
  try {
    tableRef.value?.clearSelection?.()
  } catch { /* ignore */ }
}

function handleSearch() {
  query.pageNum = 1
  clearSelectionGlobal()
  fetchData()
}

function handleReset() {
  query.id = null
  query.courseId = null
  query.questionType = null
  query.difficulty = null
  query.keyword = ''
  query.pageNum = 1
  clearSelectionGlobal()
  fetchData()
}

/** 按 ID 定位时高亮目标行 */
function rowClassName({ row }) {
  return query.id && row.id === query.id ? 'located-row' : ''
}

/** 新增：打开新增界面（与批量添加统一，支持手动录入单题/多题和文件导入） */
function handleAdd() {
  addPreset.value = null
  batchDialogVisible.value = true
}

/** 批量添加成功：刷新列表 */
function handleBatchSuccess() {
  fetchData()
}

/** 编辑：先拉详情再打开表单 */
async function handleEdit(row) {
  try {
    const res = await getQuestionById(row.id)
    currentQuestion.value = res.data
    dialogVisible.value = true
  } catch (err) {
    ElMessage.error('获取题目详情失败')
    console.error(err)
  }
}

/** 详情：拉取完整题目信息展示 */
async function handleDetail(row) {
  try {
    const res = await getQuestionById(row.id)
    detailData.value = res.data
    detailVisible.value = true
  } catch (err) {
    ElMessage.error('获取题目详情失败')
    console.error(err)
  }
}

/** 解析选项 JSON 数组字符串为数组，兼容异常格式 */
function parseOptions(options) {
  if (!options) return []
  try {
    const arr = JSON.parse(options)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

/**
 * 勾选变化（Element Plus 的 selection-change 返回"当前页可见行"中的选中集）。
 *
 * 跨页勾选保活策略：
 * 1. 取当前页所有行 id → pageIds
 * 2. 先从全局 selectedIds 中移除「本页所有 id」（干净起点）
 * 3. 再把「本页 rows 中用户勾选的 id」加回 Set
 *    => 这样「当前页勾选 delta」被合并到全局 Set 中，非当前页的勾选永远保留
 * 4. 额外：对本页当前勾选的 rows，把**完整 row 对象浅克隆**写入 selectedRowMap
 *    保证用户切页后，全局 selectedRows 仍能拿到跨页题目的完整 payload（批量操作需要完整字段）
 * 5. 同步从 Map 清理本页取消勾选且不在 selectedIds 里的条目（降低脏数据）
 */
function handleSelectionChange(rows) {
  const pageRows = tableData.value || []
  if (!pageRows.length) return

  // 1. 先把本页所有 id 从全局 Set 移除（干净起点）
  const nextSet = new Set(selectedIds.value)
  const nextMap = new Map(selectedRowMap.value)
  pageRows.forEach((r) => {
    nextSet.delete(r.id)
    // 本页重新选过后 Map 可以清掉（rows 中选中的会重新加回来），避免缓存过期
    nextMap.delete(r.id)
  })

  // 2. 把本页当前勾选的 id 加回 Set，并把完整 row 对象缓存进 Map（浅克隆，切断 reactive 引用）
  rows.forEach((r) => {
    if (r && r.id != null) {
      nextSet.add(r.id)
      nextMap.set(r.id, { ...r })
    }
  })

  selectedIds.value = nextSet
  selectedRowMap.value = nextMap
}

/** 删除：弹确认框 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      '确定要删除这道题吗？删除后不可恢复。',
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )

    await deleteQuestion(row.id)
    ElMessage.success('删除成功')

    // 如果删的是当前页最后一条，回到上一页
    if (tableData.value.length === 1 && query.pageNum > 1) {
      query.pageNum--
    }
    fetchData()
  } catch (err) {
    // 用户点取消时 err = 'cancel'，不报错
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(err)
    }
  }
}

/** 退出批量删除模式 */
function cancelBatchMode() {
  batchMode.value = false
  clearSelectionGlobal()
  ElMessage.info('已退出批量操作')
}

/** 进入批量选择模式（可批量删除 / 批量设知识点 / AI 匹配知识点） */
function enterBatchMode() {
  batchMode.value = true
  // 进入全新的批量任务，清空之前的勾选（防止筛选条件/缓存残留串入本次操作）
  clearSelectionGlobal()
  ElMessage.info('请勾选需要操作的题目（支持跨页勾选：第一页勾完点下一页再勾，不会丢失）')
}

/** 打开批量设置知识点弹窗 */
function openBatchKnowledge() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先在列表中勾选需要设置知识点的题目（支持跨页勾选）')
    return
  }
  kbDialogVisible.value = true
}

/** 打开 AI 智能匹配知识点弹窗 */
function openAiMatch() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先在列表中勾选需要匹配的题目（支持跨页勾选）')
    return
  }
  aiMatchDialogVisible.value = true
}

/**
 * 批量设置知识点 / AI 匹配知识点应用成功后的回调。
 *
 * 这批题的知识点已经落库处理完毕，因此这里主动清空勾选（含跨页缓存）：
 * 旧勾选继续留着，容易被误认为"刚才没生效"，甚至把同一批题再匹配一次。
 * 注意：只清勾选、保留批量模式，方便用户接着勾选下一批题目继续操作。
 * 成功提示由弹窗自身给出（"已为 N 道题设置知识点"），这里不再重复弹提示。
 */
function handleKnowledgeSuccess() {
  clearSelectionGlobal()
  fetchData()
}

/** 批量删除：在批量选择模式下执行删除 */
async function handleBatchDelete() {
  // 未勾选时提醒（按 selectedIds.size 作为全局口径）
  const rows = selectedRows.value
  if (!rows.length) {
    ElMessage.warning('请勾选需要删除的题目')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${rows.length} 道题吗？删除后不可恢复。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )

    const idsToDelete = rows.map((r) => r.id)
    await batchDeleteQuestions(idsToDelete)

    // 从全局勾选 Set / Map 中移除已删除的 ID（显式清理更干净）
    const nextSet = new Set(selectedIds.value)
    const nextMap = new Map(selectedRowMap.value)
    idsToDelete.forEach((id) => { nextSet.delete(id); nextMap.delete(id) })
    selectedIds.value = nextSet
    selectedRowMap.value = nextMap

    // 退出批量模式并清空勾选
    batchMode.value = false
    clearSelectionGlobal()
    ElMessage.success(`批量删除成功，共 ${rows.length} 道`)

    // 如果删的是当前页所有数据，回到上一页
    if (tableData.value.length === rows.length && query.pageNum > 1) {
      query.pageNum--
    }
    fetchData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('批量删除失败')
      console.error(err)
    }
  }
}

/** 新增/编辑弹窗保存成功后：刷新列表 */
function handleFormSuccess() {
  fetchData()
}

/** 切换排序 */
function handleSortChange(command) {
  query.sortBy = command
  query.pageNum = 1
  fetchData()
}

/** 刷新：重新拉取当前页数据 */
async function handleRefresh() {
  try {
    await fetchData()
    ElMessage.success('刷新成功')
  } catch (err) {
    ElMessage.error('刷新失败')
    console.error(err)
  }
}

onMounted(async () => {
  // 我的试题页固定默认每页 10 条（不再读系统参数 default_page_size）

  // 支持从其他页面带参数跳转（如随机组卷"前往添加题目"）：自动套用学科/题型筛选
  if (route.query.courseId) {
    query.courseId = Number(route.query.courseId)
  }
  if (route.query.questionType) {
    query.questionType = Number(route.query.questionType)
  }
  // 支持从审核历史"跳转题库"定位：按试题ID精确筛选
  if (route.query.questionId) {
    query.id = Number(route.query.questionId)
  }
  // 支持从审核历史详情跳转：按题干关键词定位试题
  if (route.query.keyword) {
    query.keyword = String(route.query.keyword)
  }

  // 组件复用场景（已在题库页时再次携带定位参数跳转过来）：监听 query 变化重新定位
  watch(() => route.query.questionId, (val) => {
    if (val) {
      query.id = Number(val)
      query.pageNum = 1
      fetchData()
    }
  })
  // 学科下拉数据
  getCourseList()
    .then((res) => {
      courseList.value = res.data || []
    })
    .catch((err) => console.error('获取学科列表失败:', err))

  // 字典下拉数据
  loadDictOptions()

  fetchData()

  // 从随机组卷"前往添加题目"跳转过来：自动打开新增界面并预填科目/题型
  if (route.query.openAdd === '1') {
    addPreset.value = {
      courseId: route.query.courseId ? Number(route.query.courseId) : null,
      questionType: route.query.questionType ? Number(route.query.questionType) : null
    }
    batchDialogVisible.value = true
  }
})
</script>

<style scoped>
.search-card {
  margin-bottom: 0;
}
/* 数据范围提示：本页只展示审核已通过的题目 */
.scope-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
}
.scope-tip .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: #409eff;
}
.scope-tip b {
  color: #606266;
}

.text-muted { color: #909399; font-size: 12px; }
/* 知识点（列表）：一行展示，超出省略（保留最后一个可见 tag，多出部分由悬浮 tooltip 显示全部） */
.kp-cell {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  max-width: 100%;
}
/* 知识点（详情弹窗）：可换行完整展示 */
.kp-cell-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.kp-tag {
  flex-shrink: 0;
  max-width: 150px;
}
/* 允许 tag 内部文字省略，配合容器裁剪呈现单行省略效果 */
.kp-tag :deep(.el-tag__content) {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.table-toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.table-toolbar > * {
  margin: 0 !important;
}

/* 详情弹窗中的题干 / 选项 / 答案 / 解析块：保留换行并自动折行 */
.detail-content {
  flex: 1;
  word-break: break-all;
  white-space: pre-wrap;
}

.detail-content-text {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

/* 详情弹窗中的驳回意见红色块 */
.detail-reject {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
  color: #f56c6c;
  background: #fef0f0;
  border-radius: 4px;
  padding: 8px 12px;
}

.detail-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-option-item {
  display: flex;
  align-items: flex-start;
  line-height: 1.6;
}

.detail-option-label {
  font-weight: 600;
  margin-right: 6px;
  flex-shrink: 0;
}

/* 按 ID 定位的目标行高亮（审核历史"跳转题库"联动） */
:deep(.located-row) td {
  background-color: #f0f9eb !important;
}
</style>
