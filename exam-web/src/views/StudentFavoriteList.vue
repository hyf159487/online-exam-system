<template>
  <div class="my-favorite">
    <div class="page-title">
      <h2>我的收藏</h2>
      <span class="tip">自主练习过程中收藏标记的试题，可集中复习</span>
    </div>

    <!-- 统计卡片：收藏总题数 / 各学科收藏分布 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-num">{{ overview.total }}</div>
        <div class="stat-label">收藏总题数</div>
      </div>
      <div class="stat-card dist">
        <div class="stat-label">各学科收藏分布</div>
        <div class="dist-tags">
          <el-tag v-for="c in overview.courses" :key="c.courseId ?? 'x'" size="small" effect="plain">
            {{ c.courseName || '未归学科' }} · {{ c.count }}
          </el-tag>
          <span v-if="!overview.courses.length" class="muted">暂无收藏，去练习页点「收藏」试试</span>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="学科">
          <el-select
            v-model="query.courseId"
            placeholder="全部学科"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="query.questionType" placeholder="全部题型" clearable style="width: 130px" @change="handleSearch">
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干关键词">
          <el-input
            v-model="query.keyword"
            placeholder="输入题干关键词"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button :icon="Download" :loading="exporting" @click="handleExport">导出 Excel</el-button>
          <el-button type="success" :icon="VideoPlay" @click="openBatchPracticeDialog">开始练习</el-button>
        </el-form-item>
        <el-form-item class="summary">
          共 <b>{{ total }}</b> 道收藏
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <!-- 批量操作条：勾选后出现 -->
      <div v-if="selection.length" class="batch-bar">
        <span>已选 <b>{{ selection.length }}</b> 道收藏</span>
        <el-button size="small" type="danger" plain :icon="Delete" @click="handleBatchRemove">批量取消收藏</el-button>
        <el-button size="small" link @click="clearSelection">取消选择</el-button>
      </div>

      <el-table ref="tableRef" class="fav-table" :data="tableData" v-loading="loading" stripe border @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">{{ (query.pageNum - 1) * query.pageSize + $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="content" label="题干" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseName" label="学科" width="150" show-overflow-tooltip />
        <el-table-column label="题型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeText(row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="difficultyTagType(row.difficulty)" effect="plain">
              {{ difficultyText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收藏时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="success" @click="openPractice(row)">开始练习</el-button>
            <el-button link type="danger" @click="handleRemove(row)">取消收藏</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="⭐ 还没有收藏的试题，在练习页点「收藏」即可加入这里" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情弹窗：完整题干、选项、答案、解析 -->
    <el-dialog v-model="detailVisible" title="收藏题目详情" width="640px">
      <div v-if="current" class="detail">
        <div class="d-row">
          <span class="d-label">题型</span>{{ typeText(current.questionType) }}（{{ current.courseName || '未归学科' }}）
          <el-tag size="small" class="diff-tag" :type="difficultyTagType(current.difficulty)" effect="plain">
            难度 {{ difficultyText(current.difficulty) }}
          </el-tag>
          <el-tag size="small" class="diff-tag" type="info" effect="plain" v-if="current.score != null">
            分值 {{ current.score }}
          </el-tag>
        </div>
        <div class="d-row"><span class="d-label">题干</span>{{ current.content }}</div>
        <div v-if="optionsList.length" class="d-row">
          <span class="d-label">选项</span>
          <div class="opts">
            <div v-for="o in optionsList" :key="o.key" class="opt-item">{{ o.key }}. {{ o.text }}</div>
          </div>
        </div>
        <div class="d-row">
          <span class="d-label">正确答案</span><span class="ans right">{{ current.answer || '-' }}</span>
        </div>
        <div v-if="current.analysis" class="d-row">
          <span class="d-label">解析</span>
          <div class="analysis">{{ current.analysis }}</div>
        </div>
        <div class="d-row">
          <span class="d-label">收藏时间</span>{{ fmtTime(current.createTime) }}
        </div>
      </div>
      <!-- AI 智能讲解（多轮对话）：与「我的错题」详情的讲解完全一致 -->
      <div class="ai-explain">
        <div class="ai-header">
          <span class="ai-title">🤖 AI 智能讲解</span>
          <span v-if="explainSource" class="ai-tag" :class="explainSource === 'llm' ? 'llm' : 'local'">
            {{ explainSource === 'llm' ? '大模型生成' : '本地解析引擎' }}
          </span>
          <el-button
            class="ai-restart"
            type="primary"
            size="small"
            plain
            :disabled="chatLoading"
            @click="handlePrimaryChat"
          >
            {{ chatMsgs.length ? '重新开始' : '生成讲解' }}
          </el-button>
        </div>

        <div ref="chatBodyRef" class="chat-body">
          <div v-if="!chatMsgs.length" class="chat-empty">
            <p>点击「生成讲解」，AI 会结合本题知识点给出解题思路、完整解析与学习建议。</p>
            <p class="chat-empty-sub">之后可以继续追问，例如：为什么选 B？换个角度讲讲 / 出 2 道同类题…</p>
          </div>
          <template v-else>
            <div v-for="(m, i) in chatMsgs" :key="i" class="msg" :class="m.role">
              <div class="msg-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
              <div
                class="msg-bubble"
                :class="{ markdown: m.role === 'assistant' }"
                v-html="m.role === 'assistant' ? renderMarkdown(m.content) : m.content"
              ></div>
            </div>
            <div v-if="chatLoading" class="msg assistant">
              <div class="msg-role">AI</div>
              <div class="msg-bubble markdown typing">正在思考…</div>
            </div>
          </template>
        </div>

        <div class="chat-input">
          <el-input
            v-model="chatInput"
            type="textarea"
            :rows="2"
            resize="none"
            :disabled="chatLoading"
            placeholder="输入你的问题，例如：为什么选 B？这道题的知识点怎么理解？"
            @keydown.enter.exact.prevent="sendChat()"
          />
          <div class="chat-actions">
            <span class="chat-tip">Enter 发送 / Shift+Enter 换行</span>
            <el-button size="small" :disabled="chatLoading || !chatMsgs.length" @click="clearChat">清空对话</el-button>
            <el-button type="primary" size="small" :loading="chatLoading" :disabled="!chatInput.trim()" @click="sendChat()">
              发送
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="startPracticeFromDetail">开始练习</el-button>
      </template>
    </el-dialog>

    <!-- 练习弹窗：单题作答，复用练习判分接口（答错自动进错题本） -->
    <el-dialog v-model="practiceVisible" title="练习收藏题" width="640px" @close="resetPractice">
      <div v-if="practiceRow" class="redo-body">
        <div class="d-row">
          <span class="d-label">题型</span>{{ typeText(practiceRow.questionType) }}（{{ practiceRow.courseName || '未归学科' }}）
          <el-tag size="small" class="diff-tag" :type="difficultyTagType(practiceRow.difficulty)" effect="plain">
            难度 {{ difficultyText(practiceRow.difficulty) }}
          </el-tag>
        </div>
        <div class="d-row"><span class="d-label">题干</span>{{ practiceRow.content }}</div>

        <!-- 单选 -->
        <el-radio-group
          v-if="practiceRow.questionType === 1"
          v-model="practiceAnswer"
          :disabled="practiceSubmitted"
          class="opt-group"
        >
          <el-radio v-for="(o, i) in practiceOptions" :key="i" :value="letter(i)" class="opt-item">
            {{ letter(i) }}. {{ o }}
          </el-radio>
        </el-radio-group>
        <!-- 多选 -->
        <el-checkbox-group
          v-else-if="practiceRow.questionType === 2"
          v-model="practiceMulti"
          :disabled="practiceSubmitted"
          class="opt-group"
        >
          <el-checkbox v-for="(o, i) in practiceOptions" :key="i" :value="letter(i)" class="opt-item">
            {{ letter(i) }}. {{ o }}
          </el-checkbox>
        </el-checkbox-group>
        <!-- 判断 -->
        <el-radio-group
          v-else-if="practiceRow.questionType === 3"
          v-model="practiceAnswer"
          :disabled="practiceSubmitted"
          class="opt-group"
        >
          <el-radio value="T" class="opt-item">正确（T）</el-radio>
          <el-radio value="F" class="opt-item">错误（F）</el-radio>
        </el-radio-group>
        <!-- 填空 / 简答 -->
        <el-input
          v-else
          v-model="practiceAnswer"
          :disabled="practiceSubmitted"
          :type="practiceRow.questionType === 4 ? 'text' : 'textarea'"
          :rows="4"
          placeholder="请输入你的答案"
          style="max-width: 460px"
        />

        <template v-if="practiceSubmitted">
          <div class="result-banner" :class="practiceResultClass">
            <template v-if="isSubjective(practiceRow.questionType)">
              📝 主观题不自动判分，请对照参考答案与解析自行评估
            </template>
            <template v-else-if="practiceResult.correct">✅ 回答正确！</template>
            <template v-else>❌ 回答错误，本题已自动收录进「我的错题」</template>
          </div>
          <div class="ans-line">
            <span class="ans-label">我的答案：</span>
            <span :class="practiceResult.correct ? 'ans-ok' : 'ans-bad'">{{ practiceMyAnswer || '（未作答）' }}</span>
          </div>
          <div class="ans-line">
            <span class="ans-label">{{ isSubjective(practiceRow.questionType) ? '参考答案：' : '正确答案：' }}</span>
            <span class="ans-ok">{{ practiceResult.correctAnswer || practiceRow.answer || '-' }}</span>
          </div>
          <div v-if="practiceResult.analysis" class="analysis-line">
            <span class="ans-label">解析：</span>{{ practiceResult.analysis }}
          </div>
        </template>
      </div>
      <template #footer>
        <el-button v-if="!practiceSubmitted" @click="practiceVisible = false">关闭</el-button>
        <el-button v-if="!practiceSubmitted" type="primary" :disabled="!practiceHasAnswer" @click="handlePracticeSubmit">
          提交答案
        </el-button>
        <template v-else>
          <el-button @click="practiceVisible = false">完成</el-button>
          <el-button type="primary" @click="nextPractice">再练一遍</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 批量练习入口：选条件后进入完整做题页，不用在操作列一题一题练 -->
    <el-dialog v-model="batchPracticeVisible" title="收藏题练习" width="520px">
      <el-form label-width="88px">
        <el-form-item label="学科">
          <el-select v-model="batchForm.courseId" placeholder="全部学科" clearable style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select
            v-model="batchForm.questionTypes"
            multiple
            collapse-tags
            placeholder="全部题型"
            style="width: 100%"
          >
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="练习题量">
          <el-input-number
            v-model="batchForm.count"
            :min="1"
            :max="50"
            :step="5"
            controls-position="right"
            style="width: 140px"
          />
          <span class="practice-hint">最多 50 题，符合条件的收藏题不足时按实际数量出题</span>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="系统会从符合条件的收藏题中随机抽题组成一轮练习，做题时可随时切换「答题 / 背题」；答错的客观题仍会自动收录进错题本。"
      />
      <template #footer>
        <el-button @click="batchPracticeVisible = false">取消</el-button>
        <el-button type="success" :icon="VideoPlay" @click="startBatchPractice">开始练习</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Delete, VideoPlay } from '@element-plus/icons-vue'
import {
  getMyFavorites,
  getFavoriteOverview,
  removeFavorite,
  batchRemoveFavorites,
  exportFavorites
} from '@/api/studentFavorite'
import { submitPractice, chatPracticeQuestion } from '@/api/studentPractice'
import { getJoinedCourses } from '@/api/course'
import { useRouter } from 'vue-router'

const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const tableRef = ref(null)
const selection = ref([])
const total = ref(0)
const courses = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, courseId: null, questionType: null, keyword: '' })

// 统计总览：收藏总题数 + 各学科分布
const overview = reactive({ total: 0, courses: [] })

// 题型下拉选项（与 TYPE_MAP 保持一致）
const TYPE_OPTIONS = [
  { value: 1, label: '单选题' },
  { value: 2, label: '多选题' },
  { value: 3, label: '判断题' },
  { value: 4, label: '填空题' },
  { value: 5, label: '简答题' },
  { value: 6, label: '名词解释' }
]

const TYPE_MAP = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }
const typeText = (t) => TYPE_MAP[t] || '未知'
const letter = (i) => String.fromCharCode(65 + i)
const isSubjective = (t) => t === 5 || t === 6

/** 难度展示：1-2 简单 / 3 中等 / 4-5 困难 */
function difficultyText(d) {
  if (!d) return '-'
  if (d <= 2) return '简单'
  if (d === 3) return '中等'
  return '困难'
}
function difficultyTagType(d) {
  if (!d) return 'info'
  if (d <= 2) return 'success'
  if (d === 3) return 'warning'
  return 'danger'
}

const detailVisible = ref(false)
const current = ref(null)

// options 是 JSON 数组字符串，如 ["选项A","选项B"]，解析成带 A/B/C 编号的列表
const optionsList = computed(() => {
  if (!current.value?.options) return []
  try {
    const arr = JSON.parse(current.value.options)
    if (Array.isArray(arr)) {
      return arr.map((o, i) => ({ key: String.fromCharCode(65 + i), text: o }))
    }
  } catch (e) {}
  return []
})

async function loadData() {
  loading.value = true
  try {
    const res = await getMyFavorites({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      ...buildParams()
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (err) {
    console.error('加载收藏失败:', err)
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  try {
    const res = await getFavoriteOverview()
    const d = res.data || {}
    overview.total = d.total ?? 0
    overview.courses = d.courses || []
  } catch (e) {}
}

// 学科下拉只列【当前学生已加入的课程】：收藏题都来自练习范围内的课程，
// 用全量课程列表会让没加入的学科也能被选中
async function loadCourses() {
  try {
    const res = await getJoinedCourses()
    courses.value = res.data || []
  } catch (e) {}
}

function buildParams() {
  return {
    courseId: query.courseId ?? undefined,
    questionType: query.questionType ?? undefined,
    keyword: query.keyword?.trim() ? query.keyword.trim() : undefined
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.courseId = null
  query.questionType = null
  query.keyword = ''
  query.pageNum = 1
  loadData()
}

/* ---------- 批量练习（页面上方入口）：选条件 → 跳到完整做题页 ---------- */
const router = useRouter()
const batchPracticeVisible = ref(false)
const batchForm = reactive({ courseId: null, questionTypes: [], count: 10 })

// 打开弹窗：默认沿用当前列表的筛选条件，减少重复选择
function openBatchPracticeDialog() {
  if (!overview.total) {
    ElMessage.warning('还没有收藏的试题，先去练习页收藏几道吧')
    return
  }
  batchForm.courseId = query.courseId ?? null
  batchForm.questionTypes = query.questionType ? [query.questionType] : []
  // 默认 10 题，但不超出实际可用的收藏题数（当前筛选无结果时以收藏总数为参考）
  const usable = total.value || overview.total || 10
  batchForm.count = Math.min(10, Math.max(1, usable))
  batchPracticeVisible.value = true
}

// 开始练习：带上抽题条件跳到自由做题页（source=favorite 表示从收藏题里抽题）
function startBatchPractice() {
  const params = new URLSearchParams()
  params.set('source', 'favorite')
  if (batchForm.courseId) params.set('courseId', batchForm.courseId)
  if (batchForm.questionTypes.length) params.set('questionTypes', batchForm.questionTypes.join(','))
  params.set('count', batchForm.count)
  batchPracticeVisible.value = false
  router.push(`/student/practice/free?${params.toString()}`)
}

/* ---------- 详情 ---------- */
function openDetail(row) {
  current.value = row
  resetExplain()
  detailVisible.value = true
}

/* ---------- AI 智能讲解（多轮对话） ----------
   复用背题模式的题目讲解接口（按试题 ID 出讲解，与「我的错题」里的 AI 讲解同一套后端），
   收藏页没有「我的答案」概念，所以不走错题的错因分析提示词 */
const chatMsgs = ref([]) // [{ role: 'user' | 'assistant', content }]
const chatLoading = ref(false)
const chatInput = ref('')
const explainSource = ref('') // 最新一条 AI 回复的来源：llm / local
const chatBodyRef = ref(null)

// 换一题看详情时重置上一题的对话，避免串题
function resetExplain() {
  chatMsgs.value = []
  chatLoading.value = false
  chatInput.value = ''
  explainSource.value = ''
}

function scrollChatToBottom() {
  nextTick(() => {
    const el = chatBodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// 清空本轮对话
function clearChat() {
  chatMsgs.value = []
  chatInput.value = ''
  explainSource.value = ''
  scrollChatToBottom()
}

// 头部按钮：无对话时=「生成讲解」（发默认开场白）；有对话后=「重新开始」（清空重来）
function handlePrimaryChat() {
  if (chatLoading.value || !current.value) return
  if (!chatMsgs.value.length) {
    sendChat('请帮我讲解这道题：说明考查的核心知识点，给出正确的解题思路与完整解析，并指出常见易错点与学习建议。')
  } else {
    clearChat()
  }
}

// 发送一轮提问（首问或追问都走同一接口，带上前面的历史上下文）
// text 可选：传入则用之（「生成讲解」按钮发默认开场白），否则读取输入框内容
async function sendChat(text) {
  const q = (text ?? chatInput.value).trim()
  if (!q || chatLoading.value || !current.value) return
  if (!current.value.questionId) {
    ElMessage.warning('该收藏记录缺少试题 ID，暂时无法生成讲解')
    return
  }
  chatMsgs.value.push({ role: 'user', content: q })
  chatInput.value = ''
  chatLoading.value = true
  explainSource.value = ''
  scrollChatToBottom()
  // 历史 = 已 push 的去掉本轮，由后端作为上下文拼接到最新问题之前
  const history = chatMsgs.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
  try {
    const res = await chatPracticeQuestion(current.value.questionId, { question: q, history })
    const content = res.data?.content || '（AI 未返回讲解内容，请稍后重试）'
    chatMsgs.value.push({ role: 'assistant', content })
    explainSource.value = res.data?.source || ''
  } catch (err) {
    chatMsgs.value.push({ role: 'assistant', content: '讲解生成失败：' + (err.msg || err.message || '网络异常') })
  } finally {
    chatLoading.value = false
    scrollChatToBottom()
  }
}

// 简易 Markdown 渲染（标题/加粗/行内代码/列表/换行），内容来自后端 AI，转义后渲染
function renderMarkdown(text) {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/```([\s\S]*?)```/g, (m, code) => `<pre class="md-code">${code.trim()}</pre>`)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^###\s+(.+)$/gm, '<h4 class="md-h4">$1</h4>')
    .replace(/^##\s+(.+)$/gm, '<h3 class="md-h3">$1</h3>')
    .replace(/^#\s+(.+)$/gm, '<h2 class="md-h2">$1</h2>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/^- (.+)$/gm, '<li class="md-li">$1</li>')
    .replace(/(<li[\s\S]*?<\/li>)/g, '<ul>$1</ul>')
    .replace(/\n/g, '<br>')
  return html
}

function startPracticeFromDetail() {
  const row = current.value
  detailVisible.value = false
  if (row) openPractice(row)
}

/* ---------- 开始练习（单题作答，判分接口与自由刷题一致） ---------- */
const practiceVisible = ref(false)
const practiceRow = ref(null)
const practiceAnswer = ref('')
const practiceMulti = ref([])
const practiceSubmitted = ref(false)
const practiceResult = ref({})

const practiceOptions = computed(() => {
  if (!practiceRow.value?.options) return []
  try {
    const arr = JSON.parse(practiceRow.value.options)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
})

const practiceHasAnswer = computed(() => {
  if (!practiceRow.value) return false
  if (practiceRow.value.questionType === 2) return practiceMulti.value.length > 0
  return practiceAnswer.value.trim() !== ''
})

const practiceMyAnswer = computed(() => {
  if (!practiceRow.value) return ''
  if (practiceRow.value.questionType === 2) return practiceMulti.value.slice().sort().join('、')
  if (practiceRow.value.questionType === 3) {
    return practiceAnswer.value === 'T' ? '正确（T）' : practiceAnswer.value === 'F' ? '错误（F）' : ''
  }
  return practiceAnswer.value
})

const practiceResultClass = computed(() => {
  if (isSubjective(practiceRow.value?.questionType)) return 'neutral'
  return practiceResult.value?.correct ? 'ok' : 'bad'
})

function resetPractice() {
  practiceAnswer.value = ''
  practiceMulti.value = []
  practiceSubmitted.value = false
  practiceResult.value = {}
}

function openPractice(row) {
  practiceRow.value = row
  resetPractice()
  practiceVisible.value = true
}

async function handlePracticeSubmit() {
  if (!practiceHasAnswer.value || practiceSubmitted.value || !practiceRow.value) return
  const row = practiceRow.value
  const userAnswer = row.questionType === 2 ? practiceMulti.value.slice().sort().join(',') : practiceAnswer.value
  try {
    const res = await submitPractice({ questionId: row.questionId, userAnswer })
    practiceResult.value = res.data || {}
    practiceSubmitted.value = true
    // 答错的客观题会被自动归集错题本，统计卡片也同步刷新（收藏本身不因答错而减少）
    loadOverview()
  } catch (e) {}
}

/** 再练一遍：清空作答重新开始同一题 */
function nextPractice() {
  resetPractice()
}

/* ---------- 取消收藏 ---------- */
async function handleRemove(row) {
  try {
    await ElMessageBox.confirm('确定要取消收藏这道题吗？取消后可在练习页重新收藏。', '取消收藏', {
      type: 'warning',
      confirmButtonText: '确定取消收藏',
      cancelButtonText: '再想想'
    })
    await removeFavorite(row.id)
    ElMessage.success('已取消收藏')
    loadData()
    loadOverview()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('取消收藏失败：' + (err.message || '网络异常'))
  }
}

/* ---------- 批量取消收藏 ---------- */
function onSelectionChange(rows) {
  selection.value = rows
}

function clearSelection() {
  tableRef.value?.clearSelection()
  selection.value = []
}

async function handleBatchRemove() {
  if (!selection.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定要取消选中 ${selection.value.length} 道题的收藏吗？`,
      '批量取消收藏',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想' }
    )
    const res = await batchRemoveFavorites(selection.value.map((r) => r.id))
    ElMessage.success(res.msg || '批量取消收藏成功')
    clearSelection()
    loadData()
    loadOverview()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('批量取消失败：' + (err.msg || err.message || '网络异常'))
  }
}

/* ---------- 导出 Excel（当前筛选条件） ---------- */
async function handleExport() {
  if (!total.value) {
    ElMessage.warning('当前筛选条件下没有可导出的收藏')
    return
  }
  exporting.value = true
  try {
    const res = await exportFavorites(buildParams())
    const raw = res?.data || res
    const url = window.URL.createObjectURL(raw instanceof Blob ? raw : new Blob([raw]))
    const a = document.createElement('a')
    a.href = url
    a.download = `我的收藏_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.msg || e.message || '网络异常'))
  } finally {
    exporting.value = false
  }
}

/* ---------- 辅助 ---------- */
function fmtTime(t) {
  if (!t) return '-'
  return String(t).slice(0, 16).replace('T', ' ')
}

onMounted(() => {
  loadCourses()
  loadData()
  loadOverview()
})
</script>

<style scoped>
.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.page-title h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.tip {
  font-size: 13px;
  color: #909399;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
/* 统计卡片 */
.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  flex: 0 0 160px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}
.stat-card.dist {
  flex: 1 1 320px;
  min-width: 280px;
}
.stat-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: #e6a23c;
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}
.dist-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
/* 操作列按钮居中：theme.css 为了让多按钮同排，把表格最后一列的 .cell 设成了 display:flex，
   flex 容器下单元格的 text-align 对子项不再生效，必须显式让主轴居中 */
.fav-table :deep(.el-table__header th:last-child .cell),
.fav-table :deep(.el-table__body td:last-child .cell) {
  justify-content: center;
}
/* 表头与内容统一居中（题干等长文本仍走 show-overflow-tooltip 截断） */
.fav-table :deep(.el-table__header th.el-table__cell),
.fav-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}
/* 批量操作条 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
  font-size: 13px;
  color: #e6a23c;
}
.batch-bar b {
  font-size: 15px;
}
.practice-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
.summary {
  margin-left: auto;
  color: #606266;
}
.summary b {
  color: #e6a23c;
  font-size: 16px;
}
.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
/* 详情弹窗 */
.detail .d-row {
  margin-bottom: 14px;
  line-height: 1.7;
  color: #303133;
}
.d-label {
  display: inline-block;
  width: 64px;
  color: #909399;
  font-size: 13px;
}
.diff-tag {
  margin-left: 8px;
}
.opts {
  display: inline-block;
  vertical-align: top;
}
.opt-item {
  margin-bottom: 4px;
}
.analysis {
  display: inline-block;
  vertical-align: top;
  max-width: calc(100% - 70px);
  white-space: pre-wrap;
  line-height: 1.7;
}
.muted {
  color: #909399;
}
.ans {
  font-weight: 600;
}
.ans.right {
  color: #67c23a;
}
.ans-ok {
  color: #67c23a;
  font-weight: 600;
}
.ans-bad {
  color: #f56c6c;
  font-weight: 600;
}
/* 练习弹窗 */
.opt-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  margin: 6px 0 4px 64px;
  width: calc(100% - 64px);
}
.ans-line {
  margin-top: 10px;
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
}
.analysis-line {
  margin-top: 10px;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
}
.ans-label {
  display: inline-block;
  min-width: 72px;
  color: #909399;
}
.result-banner {
  margin-top: 18px;
  padding: 12px 16px;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
}
.result-banner.ok {
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #e1f3d8;
}
.result-banner.bad {
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fde2e2;
}
.result-banner.neutral {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #faecd8;
}
/* AI 讲解（多轮对话），与「我的错题」详情保持同一套样式 */
.ai-explain {
  margin-top: 20px;
  border-top: 1px dashed #dcdfe6;
  padding-top: 16px;
}
.ai-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.ai-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.ai-tag {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 10px;
}
.ai-tag.llm {
  color: #409eff;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
}
.ai-tag.local {
  color: #e6a23c;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
}
.ai-restart {
  margin-left: auto;
}
.chat-body {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 14px 16px;
  max-height: 360px;
  overflow-y: auto;
}
.chat-empty {
  color: #909399;
  font-size: 13px;
  text-align: center;
  padding: 10px 0 6px;
  line-height: 1.7;
}
.chat-empty-sub {
  color: #c0c4cc;
  margin-top: 4px;
  font-size: 12px;
}
.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.msg.user {
  flex-direction: row-reverse;
}
.msg-role {
  flex: none;
  width: 32px;
  height: 32px;
  line-height: 32px;
  text-align: center;
  border-radius: 50%;
  font-size: 12px;
  color: #fff;
}
.msg.user .msg-role {
  background: #409eff;
}
.msg.assistant .msg-role {
  background: #67c23a;
}
.msg-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.75;
  color: #303133;
  word-break: break-word;
}
.msg.user .msg-bubble {
  background: #409eff;
  color: #fff;
}
.msg.assistant .msg-bubble {
  background: #fff;
  border: 1px solid #e4e7ed;
}
.msg-bubble.typing {
  color: #909399;
  font-style: italic;
}
.chat-input {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-tip {
  font-size: 12px;
  color: #c0c4cc;
  margin-right: auto;
}
/* Markdown 渲染样式 */
.markdown :deep(h2),
.markdown :deep(h3),
.markdown :deep(h4) {
  margin: 10px 0 6px;
  color: #303133;
}
.markdown :deep(p) {
  margin: 4px 0;
}
.markdown :deep(code) {
  background: #ebeef5;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  color: #c45656;
}
.markdown :deep(pre.md-code) {
  background: #303133;
  color: #f0f6fc;
  padding: 10px 12px;
  border-radius: 6px;
  overflow-x: auto;
}
.markdown :deep(strong) {
  color: #409eff;
}
.markdown :deep(ul) {
  margin: 4px 0;
  padding-left: 20px;
}
.markdown :deep(li) {
  margin: 2px 0;
}
</style>
