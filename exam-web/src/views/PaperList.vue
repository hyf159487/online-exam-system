<template>
  <div class="paper-list">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="试卷名称">
          <el-input v-model="query.paperName" placeholder="试卷名称模糊搜索" clearable style="width: 200px" @keyup.enter="loadList" />
        </el-form-item>
        <!-- 嵌入「课程信息 → 课程试卷」抽屉时，学科由父组件锁定，不再给下拉 -->
        <el-form-item label="学科" v-if="!embedded">
          <el-select v-model="query.courseId" placeholder="全部学科" clearable style="width: 150px" @change="handleSearch">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已归档" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>试卷列表</span>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-tooltip :content="`请先在左侧勾选至少 1 张试卷`" :disabled="selectedIds.length > 0" placement="bottom">
              <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleDeleteBatch">批量删除（{{ selectedIds.length }}）</el-button>
            </el-tooltip>
            <el-button type="primary" @click="createDialogRef.open()">新增试卷</el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="list"
        v-loading="loading"
        stripe
        border
        @selection-change="onSelectionChange"
        ref="tableRef"
        :header-cell-style="{ textAlign: 'center' }"
        :cell-style="{ textAlign: 'center' }"
      >
        <el-table-column type="selection" width="44" :selectable="canWrite" />
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (query.pageNum - 1) * query.pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="paperName" label="试卷名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseName" label="学科" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.courseName || '未分配' }}</template>
        </el-table-column>
        <el-table-column label="组卷方式" width="110">
          <template #default="{ row }">
            <el-tag :type="paperTypeTagType(row.paperType)" size="small">
              {{ paperTypeLabel(row.paperType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题数" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.questionCount && row.questionCount > 0">{{ row.questionCount }}</span>
            <el-tag v-else size="small" type="danger" effect="plain">尚未组卷</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建者" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.creatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="可见人" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              v-if="row.visibleNames"
              :content="`共享给：${row.visibleNames}`"
              placement="top"
            >
              <span class="visible-names">{{ row.visibleNames }}</span>
            </el-tooltip>
            <span v-else class="visible-self">仅自己</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="70" align="center" />
        <el-table-column label="时长" width="80" align="center">
          <template #default="{ row }">{{ row.duration }} 分钟</template>
        </el-table-column>
        <el-table-column prop="passScore" label="及格分" width="75" align="center" />
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type" size="small">
              {{ statusMap[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="420" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openPreview(row)">查看</el-button>
            <template v-if="canWrite(row) && row.status !== 2">
              <el-button type="primary" link @click="goCompose(row)">组卷</el-button>
              <el-button type="warning" link @click="formDialogRef.open(row)">编辑</el-button>
              <el-button type="success" link :disabled="row.status !== 0" @click="handlePublish(row)">发布</el-button>
              <el-button type="warning" link :disabled="row.status !== 1" @click="handleRevoke(row)">撤回</el-button>
              <el-button type="info" link :disabled="row.status !== 1" @click="handleArchive(row)">归档</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else-if="canWrite(row) && row.status === 2">
              <el-tag size="small" type="warning" class="shared-tag">已归档 · 只读</el-tag>
            </template>
            <el-tag v-else size="small" type="info" class="shared-tag">共享 · 只读</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        class="pager"
        layout="total, prev, pager, next, sizes"
        :total="total"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[5, 10, 20]"
        @current-change="loadList"
        @size-change="loadList"
      />
    </el-card>

    <!-- 编辑对话框 -->
    <PaperFormDialog ref="formDialogRef" :saved="onSaved" :default-course-id="courseId" />

    <!-- 新增试卷对话框（普通创建 / 批量创建） -->
    <PaperCreateDialog ref="createDialogRef" :saved="onSaved" />

    <!-- 试卷查看（预览）弹窗 -->
    <el-dialog v-model="previewVisible" title="查看试卷" width="880px" top="4vh" destroy-on-close>
      <div v-if="previewData.paper" class="preview">
        <div class="preview-head">
          <h3>{{ previewData.paper.paperName }}</h3>
          <div class="preview-meta">
            <span>学科：{{ courseNameOf(previewData.paper.courseId) }}</span>
            <span>组卷：{{ paperTypeLabel(previewData.paper.paperType) }}</span>
            <span>题目数：{{ (previewData.questions || []).length || previewData.paper.questionCount || 0 }}</span>
            <span>总分：{{ previewData.paper.totalScore || 0 }} 分</span>
            <span>时长：{{ previewData.paper.duration }} 分钟</span>
            <span>及格分：{{ previewData.paper.passScore || 0 }}</span>
            <span>
              状态：
              <el-tag :type="statusMap[previewData.paper.status]?.type" size="small">
                {{ statusMap[previewData.paper.status]?.label || '未知' }}
              </el-tag>
            </span>
          </div>
        </div>
        <div v-if="previewData.questions && previewData.questions.length" class="preview-body">
          <div v-for="(q, idx) in previewData.questions" :key="q.questionId || idx" class="preview-q">
            <div class="q-title">
              <span class="q-index">{{ idx + 1 }}</span>
              <el-tag size="small" effect="plain">{{ questionTypeText(q.questionType) }}</el-tag>
              <span class="q-score">（{{ q.score }}分）</span>
            </div>
            <div class="q-content" v-html="highlightContent(q.content)"></div>
            <div v-if="showOptions(q)" class="q-options">
              <div v-for="(opt, oi) in splitOptions(q.options)" :key="oi">
                {{ letter(oi) }}. {{ opt }}
              </div>
            </div>
            <div v-if="isJudge(q.questionType)" class="q-options">
              <div>A. 正确</div>
              <div>B. 错误</div>
            </div>
            <div class="q-answer">
              <span class="ans-label">参考答案：</span>
              <code v-if="isChoice(q.questionType)">{{ answerLetters(q) }}</code>
              <code v-else-if="isJudge(q.questionType)">{{ q.answer == 1 || q.answer === '1' || q.answer === 'true' || q.answer === true ? '正确' : '错误' }}</code>
              <code v-else>{{ q.answer }}</code>
            </div>
          </div>
        </div>
        <el-empty v-else description="试卷尚未组卷，暂无题目" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPaperList, deletePaper, publishPaper, revokePaper, archivePaper, getPaperDetail, deletePaperBatch } from '@/api/examPaper'
import { getCourseList } from '@/api/course'
import { useDefaultPageSize } from '@/composables/useDefaultPageSize'
import { usePaperTypeOptions } from '@/composables/usePaperTypeOptions'
import { setComposeIntent } from '@/composables/composeIntent'
import { useAuthStore } from '@/stores/auth'
import PaperFormDialog from './PaperFormDialog.vue'
import PaperCreateDialog from './PaperCreateDialog.vue'

/**
 * 两种复用入口：
 * 1) 独立页面（/papers）：学科从路由 ?courseId= 带入，用户可自行切换筛选；
 * 2) 嵌入「课程信息 → 课程试卷」抽屉：由父组件传入 courseId 锁定本学科（隐藏学科下拉，新增试卷默认归属本学科）。
 */
const props = defineProps({
  courseId: { type: [Number, String], default: null },
  embedded: { type: Boolean, default: false }
})

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/** 组卷方式文案与配色（字典 paper_type，字典缺失时回退到 1 手动 / 2 随机） */
const { loadPaperTypeOptions, paperTypeLabel, paperTypeTagType } = usePaperTypeOptions()

/** 当前登录身份（教师只能操作自己创建的试卷；共享来的仅可查看） */
const myId = computed(() => authStore.userInfo?.userId)
const isAdmin = computed(() => authStore.userInfo?.role === 2)
/** 该行是否可写（管理员 或 自己创建的） */
function canWrite(row) {
  return isAdmin.value || row.creatorId === myId.value
}

const list = ref([])
const total = ref(0)
const loading = ref(false)
const formDialogRef = ref(null)
const createDialogRef = ref(null)
const tableRef = ref(null)
const courses = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  paperName: '',
  status: null,
  courseId: null
})

const statusMap = {
  0: { label: '草稿', type: 'info' },
  1: { label: '已发布', type: 'success' },
  2: { label: '已归档', type: 'warning' }
}

// 批量操作
const selectedIds = ref([])
function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

// 批量删除
async function handleDeleteBatch() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.length} 张试卷吗？\n（包括试卷下所有题目关联，不可恢复）`,
      '批量删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await deletePaperBatch(selectedIds.value)
    ElMessage.success(res.message || `删除成功（${res.data} 张）`)
    if (tableRef.value) tableRef.value.clearSelection()
    loadList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

// 查看（预览）
const previewVisible = ref(false)
const previewData = reactive({ paper: null, questions: [] })
async function openPreview(row) {
  previewData.paper = null
  previewData.questions = []
  previewVisible.value = true
  try {
    const res = await getPaperDetail(row.id)
    const d = res.data || {}
    previewData.paper = d.paper || row
    previewData.questions = d.questions || []
  } catch (e) {
    // 拦截器已处理
  }
}

const QUESTION_TYPE = {
  SINGLE: 1, MULTI: 2, JUDGE: 3, FILL: 4, SUBJECTIVE: 5
}
function questionTypeText(t) {
  const m = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
  return m[t] || '未知'
}
function isChoice(t) { return t === QUESTION_TYPE.SINGLE || t === QUESTION_TYPE.MULTI }
function isJudge(t) { return t === QUESTION_TYPE.JUDGE }
function hasJsonOptions(t) { return isChoice(t) }

function letter(i) { return 'ABCDEFGHIJ'[i] || '' }
function splitOptions(str) {
  if (str === undefined || str === null || str === '') return []
  if (typeof str !== 'string') {
    return Array.isArray(str) ? str : []
  }
  const s = str.trim()
  // JSON 数组：["选项A","选项B"] 或 [{key:"A",content:"..."}]
  if (s.startsWith('[')) {
    try {
      const arr = JSON.parse(s)
      if (Array.isArray(arr)) {
        return arr.map(x => typeof x === 'object' && x !== null ? (x.content ?? x.value ?? x.text ?? x.label ?? x.key ?? String(x)) : String(x))
      }
    } catch { /* ignore */ }
  }
  // 英文 A.xxx / 中文 A、xxx / 编号 1.xxx 自动切分
  const reg = /(?:^|\n)\s*([A-Z][.、．）)\s]+|\d+[.、．）)\s]+|第[一二三四五六七八九十0-9]+空[：:、．)\s]*)/
  if (reg.test(s)) {
    return s.split(/(?:^|\n)\s*(?:[A-Z][.、．）)\s]+|\d+[.、．）)\s]+|第[一二三四五六七八九十0-9]+空[：:、．)\s]*)/g)
      .map(x => (x || '').trim())
      .filter(Boolean)
  }
  // 普通分隔
  return s.split(/[;；\n]/).map(x => x.trim()).filter(Boolean)
}

/** 把填空题中 "___" 用蓝色下划线下标出来，以及 < 转义 */
function highlightContent(raw) {
  if (raw === undefined || raw === null) return ''
  const escaped = String(raw)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped.replace(/_{3,}/g, '<span style="display:inline-block;min-width:80px;border-bottom:2px solid #409EFF;color:transparent">___</span>')
}

/** 题目答案字母化："A,B" / "AB" / "1,0" 都归化成 A、B 顺序串 */
function answerLetters(q) {
  const ans = q.answer == null ? '' : String(q.answer).trim()
  if (!ans) return '—'
  // 0/1 判断题：直接交给调用者判断，这里不处理
  // 多/单选常见格式："A,B,C" / "ABC" / "1,2,3"(题号索引) / "[\"A\",\"B\"]"(JSON 数组字符串)
  let arr = []
  // 纯字母串：ABC
  if (/^[A-Z]+$/.test(ans)) {
    arr = ans.split('')
  } else {
    try {
      const parsed = JSON.parse(ans)
      if (Array.isArray(parsed)) arr = parsed.map(x => String(x))
    } catch {
      arr = ans.split(/[,，;；\s\/|]+/).filter(Boolean)
    }
  }
  if (!arr.length) return ans
  return arr.map(x => {
    const s = String(x).trim()
    if (/^[A-Z]$/.test(s)) return s
    // 可能是数字索引（0-based / 1-based 混合，尽量兼容：0→A, 1→B）
    if (/^\d+$/.test(s)) {
      const n = Number(s)
      // 经验：选项数字一般 >=1 且 <= 10 才是索引，不然可能是真实答案（如123）直接原样
      if (n >= 0 && n <= 9) return letter(n >= 1 ? n - 1 : 0)
      return s
    }
    return s
  }).join('、')
}

function courseNameOf(id) {
  if (id == null) return '未分配'
  const c = courses.value.find(c => c.id === id || String(c.id) === String(id))
  return c ? c.courseName : '未分配'
}

function showOptions(q) {
  if (!q) return false
  return hasJsonOptions(q.questionType) || (q.options && String(q.options).trim())
}

async function loadList() {
  loading.value = true
  try {
    const res = await getPaperList({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      paperName: query.paperName || undefined,
      status: query.status ?? undefined,
      courseId: query.courseId ?? undefined
    })
    list.value = res.data.records
    total.value = Number(res.data.total)
  } catch (e) {
    // 拦截器已弹错误提示，这里不用重复处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, paperName: '', status: null, courseId: null })
  loadList()
}

/**
 * 跳转组卷页，并登记「进入后自动打开哪个弹窗」的意图：
 *   2 随机组卷   → 自动打开随机组卷弹窗
 *   3 AI智能组卷 → 自动打开 AI 智能组卷弹窗
 *   1 手动组卷   → 无意图，只打开组卷面板，由教师逐题挑选
 * 意图走内存（不写 URL）：Layout 的路由出口绑了 :key="route.fullPath"，
 * 一旦改 query，组卷页会被销毁重建，弹窗刚打开就消失。
 * @param {number} paperId 试卷 id
 * @param {number|string} paperType 组卷方式：1手动 2随机 3AI智能
 */
function goComposePage(paperId, paperType) {
  const type = Number(paperType)
  if (type === 2 || type === 3) setComposeIntent(paperId, type)
  router.push(`/papers/${paperId}/compose`)
}

/** 新增/编辑成功回调：新增时带新 id 直接跳组卷页，编辑时只刷新列表 */
function onSaved(newPaperId, paperType) {
  loadList()
  if (newPaperId) {
    goComposePage(newPaperId, paperType)
  }
}

/** 去组卷页：
 *  - 已归档：直接禁止（不可能再组卷）
 *  - 已发布：弹窗确认撤回后再组卷
 *  - 进行中考试占用：后端组卷接口会阻断（这里前端不再预校验，失败时拦截器会显示后端原因）
 *  - 草稿：直接进入 */
async function goCompose(row) {
  if (row.status === 2) {
    ElMessage.warning('已归档试卷不能重新组卷')
    return
  }
  if (row.status === 1) {
    try {
      await ElMessageBox.confirm(
        '该试卷已发布，无法组卷。是否先撤回（恢复为草稿）后再组卷？\n（若该试卷存在正在进行的考试，撤回后实际组卷仍会被后端拦截）',
        '试卷已发布',
        { confirmButtonText: '撤回并重新组卷', cancelButtonText: '取消组卷', type: 'warning' }
      )
    } catch (e) {
      return // 取消组卷
    }
    try {
      await revokePaper(row.id)
      ElMessage.success('试卷已撤回，可继续组卷')
    } catch (e) {
      return
    }
  }
  goComposePage(row.id, row.paperType)
}

async function handlePublish(row) {
  // 无题目试卷不能发布：弹窗引导前往组卷
  if (!row.questionCount || row.questionCount <= 0) {
    try {
      await ElMessageBox.confirm(
        `试卷「${row.paperName}」还没有题目，不能发布。是否前往组卷？`,
        '试卷尚未组卷',
        { confirmButtonText: '前往组卷', cancelButtonText: '取消发布', type: 'warning' }
      )
      goComposePage(row.id, row.paperType)
    } catch (e) {
      // 取消发布，不做任何操作
    }
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定发布「${row.paperName}」吗？发布后该试卷才能用于考试。`,
      '发布确认',
      { confirmButtonText: '确定发布', cancelButtonText: '取消', type: 'info' }
    )
    await publishPaper(row.id)
    ElMessage.success('发布成功')
    loadList()
  } catch (e) {
    // 用户点了取消就不提示
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

/** 撤回试卷：已发布 → 草稿（撤回后不可被发布考试选用，可重新编辑再发布） */
async function handleRevoke(row) {
  try {
    await ElMessageBox.confirm(
      `确定撤回「${row.paperName}」吗？撤回后试卷恢复为草稿状态，将无法用于发布新考试。`,
      '撤回确认',
      { confirmButtonText: '确定撤回', cancelButtonText: '取消', type: 'warning' }
    )
    await revokePaper(row.id)
    ElMessage.success('撤回成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

/** 归档试卷：已发布 → 已归档（归档后不可改/不可组卷/不可用于新建考试，只保留查看） */
async function handleArchive(row) {
  try {
    await ElMessageBox.confirm(
      `确定归档「${row.paperName}」吗？\n归档后该试卷将不能撤回、不能重新组卷、不能用于新建考试，仅保留查看。\n通常在考试全部结束后再归档。`,
      '归档确认',
      { confirmButtonText: '确定归档', cancelButtonText: '取消', type: 'warning' }
    )
    await archivePaper(row.id)
    ElMessage.success('归档成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除试卷「${row.paperName}」吗？试卷下的题目关联也会一起删除。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deletePaper(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

// 加载学科下拉数据
async function loadCourses() {
  try {
    const res = await getCourseList()
    courses.value = res.data || []
  } catch (e) {
    console.error('获取学科列表失败:', e)
  }
}

onMounted(async () => {
  try {
    query.pageSize = await useDefaultPageSize()
  } catch {}
  // 嵌入课程信息页时以父组件传入的 courseId 为准；独立页面时读路由 ?courseId=xx
  const cid = Number(props.courseId ?? route.query.courseId)
  if (cid) {
    query.courseId = cid
  }
  loadPaperTypeOptions()
  loadCourses()
  loadList()
})
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }

/* 可见人列 */
.visible-names { color: var(--el-color-primary); font-size: 13px; cursor: default; }
.visible-self { color: #909399; font-size: 13px; }
.shared-tag { margin-left: 6px; }
.pager { margin-top: 16px; justify-content: flex-end; }
.preview-head { padding: 12px 14px; border-radius: 8px; background: #f5f7fa; margin-bottom: 12px; }
.preview-head h3 { margin: 0 0 6px; font-size: 18px; }
.preview-meta { display: flex; flex-wrap: wrap; gap: 12px 18px; color: #606266; font-size: 13px; }
.preview-body { max-height: 55vh; overflow-y: auto; padding: 4px 4px 4px 0; }
.preview-q { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px 16px; margin-bottom: 12px; }
.q-title { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-weight: 600; }
.q-index { display: inline-flex; width: 28px; height: 28px; justify-content: center; align-items: center; background: #409EFF; color: #fff; border-radius: 50%; font-size: 13px; }
.q-score { color: #909399; font-size: 12px; font-weight: 400; }
.q-content { white-space: pre-wrap; word-break: break-all; line-height: 1.7; margin-bottom: 8px; }
.q-options { padding-left: 4px; line-height: 2; color: #303133; }
.q-answer { margin-top: 8px; padding: 8px 12px; background: #f0f9eb; border-radius: 6px; font-size: 13px; }
.ans-label { color: #67C23A; font-weight: 600; margin-right: 6px; }
.q-answer code { padding: 2px 6px; background: #fff; border: 1px solid #e1f3d8; border-radius: 4px; color: #3c9c4a; }
</style>
