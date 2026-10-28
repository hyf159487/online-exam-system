<template>
  <div class="my-wrong">
    <div class="page-title">
      <h2>我的错题</h2>
      <span class="tip">这里收录你考试与自主练习答错的题目，可设置自动移出策略，重做达标后自动移除错题</span>
    </div>

    <!-- 统计卡片：错题总数 / 已做对 / 累计答错次数 / 各学科分布 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-num">{{ overview.total }}</div>
        <div class="stat-label">错题总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-num ok">{{ overview.mastered }}</div>
        <div class="stat-label">已做对过</div>
      </div>
      <div class="stat-card">
        <div class="stat-num warn">{{ overview.wrongTotal }}</div>
        <div class="stat-label">累计答错次数</div>
      </div>
      <div class="stat-card dist">
        <div class="stat-label">各学科错题分布</div>
        <div class="dist-tags">
          <el-tag v-for="c in overview.courses" :key="c.courseId ?? 'x'" size="small" effect="plain">
            {{ c.courseName || '未归学科' }} · {{ c.count }}
          </el-tag>
          <span v-if="!overview.courses.length" class="muted">暂无数据</span>
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
        <el-form-item label="错误次数">
          <el-select v-model="query.wrongCount" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="错 1 次" value="eq1" />
            <el-option label="错 2 次以上" value="gte2" />
            <el-option label="错 3 次以上" value="gte3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button :icon="Download" :loading="exporting" @click="handleExport">导出 Excel</el-button>
          <el-button type="success" :icon="VideoPlay" @click="openPracticeDialog">开始练习</el-button>
        </el-form-item>
        <el-form-item class="summary">
          共 <b>{{ total }}</b> 道错题
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 自动移出策略 -->
    <el-card shadow="never" class="strategy-card">
      <div class="strategy-row">
        <span class="strategy-label">错题移除策略</span>
        <el-switch v-model="cfg.autoRemove" active-text="开启自动移出" @change="saveCfg" />
        <div class="threshold-box">
          连续答对
          <el-input-number
            v-model="cfg.threshold"
            :min="1"
            :max="10"
            :disabled="!cfg.autoRemove"
            size="small"
            controls-position="right"
            style="width: 100px"
            @change="saveCfg"
          />
          次后自动移出
        </div>
        <span class="strategy-tip">
          {{ cfg.autoRemove
            ? `开启后，该题连续答对 ${cfg.threshold} 次即自动从错题列表移除；中途答错会重新计数。`
            : '当前关闭：做对不会自动移除，需手动点击「移除」。' }}
        </span>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <!-- 批量操作条：勾选后出现 -->
      <div v-if="selection.length" class="batch-bar">
        <span>已选 <b>{{ selection.length }}</b> 道错题</span>
        <el-button size="small" type="primary" :icon="VideoPlay" @click="startBatchRedo">批量重做</el-button>
        <el-button size="small" type="danger" plain :icon="Delete" @click="handleBatchRemove">批量移除</el-button>
        <el-button size="small" link @click="clearSelection">取消选择</el-button>
      </div>
      <el-table ref="tableRef" class="wrong-table" :data="tableData" v-loading="loading" stripe border @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">{{ (query.pageNum - 1) * query.pageSize + $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="content" label="题干" min-width="220" show-overflow-tooltip />
        <el-table-column prop="courseName" label="学科" width="130" show-overflow-tooltip />
        <el-table-column label="题型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeText(row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的答案" width="120" align="center">
          <template #default="{ row }"><span class="ans wrong">{{ row.userAnswer || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="正确答案" width="120" align="center">
          <template #default="{ row }"><span class="ans right">{{ row.correctAnswer || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="wrongCount" label="错误次数" width="90" align="center" />
        <el-table-column label="连续答对次数" width="120" align="center">
          <template #default="{ row }">
            <span :class="row.correctCount > 0 ? 'ans right' : 'muted'">{{ row.correctCount ?? 0 }}</span>
            <el-tag v-if="cfg.autoRemove && row.correctCount >= cfg.threshold" size="small" type="success" effect="light">已达标</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近错误" width="150" align="center" class-name="nowrap-col">
          <template #default="{ row }">{{ fmtTime(row.lastWrongTime) }}</template>
        </el-table-column>
        <el-table-column label="上次作答" width="150" align="center" class-name="nowrap-col">
          <template #default="{ row }">{{ fmtTime(row.lastAnswerTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="openRedo(row)">重做</el-button>
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="danger" @click="handleRemove(row)">移除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="🎉 暂无错题，继续保持练习！" />
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="错题详情" width="640px">
      <div v-if="current" class="detail">
        <div class="d-row">
          <span class="d-label">题型</span>{{ typeText(current.questionType) }}（{{ current.courseName }}）
        </div>
        <div class="d-row"><span class="d-label">题干</span>{{ current.content }}</div>
        <div v-if="optionsList.length" class="d-row">
          <span class="d-label">选项</span>
          <div class="opts">
            <div v-for="o in optionsList" :key="o.key" class="opt-item">{{ o.key }}. {{ o.text }}</div>
          </div>
        </div>
        <div class="d-row">
          <span class="d-label">我的答案</span><span class="ans wrong">{{ current.userAnswer }}</span>
        </div>
        <div class="d-row">
          <span class="d-label">正确答案</span><span class="ans right">{{ current.correctAnswer }}</span>
        </div>
        <div class="d-row">
          <span class="d-label">做题情况</span>错误 {{ current.wrongCount ?? 0 }} 次，连续答对 {{ current.correctCount ?? 0 }} 次
        </div>
        <!-- 试题自带解析（列表接口直接带出，无需再请求） -->
        <div v-if="current.analysis" class="d-row">
          <span class="d-label">参考解析</span>
          <div class="analysis">{{ current.analysis }}</div>
        </div>
      </div>

      <!-- AI 智能讲解（多轮对话） -->
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
            <p>点击「生成讲解」，AI 会结合你的错误答案与本题知识点给出个性化解析、错因分析与学习建议。</p>
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
    </el-dialog>

    <!-- 重做弹窗 -->
    <el-dialog v-model="redoVisible" :title="redoTitle" width="640px" @close="onRedoClose">
      <div v-if="redoRow" class="redo-body">
        <div class="d-row">
          <span class="d-label">题型</span>{{ typeText(redoRow.questionType) }}（{{ redoRow.courseName }}）
          <el-tag v-if="redoQueue.length > 1" size="small" type="info" effect="plain" class="redo-count-tag">
            第 {{ redoIndex + 1 }} / {{ redoQueue.length }} 题
          </el-tag>
          <el-tag size="small" type="warning" effect="plain" class="redo-count-tag">
            连续答对 {{ redoRow.correctCount ?? 0 }} 次
          </el-tag>
        </div>
        <div class="d-row"><span class="d-label">题干</span>{{ redoRow.content }}</div>

        <!-- 单选 -->
        <el-radio-group
          v-if="redoRow.questionType === 1"
          v-model="redoAnswer"
          :disabled="redoSubmitted"
          class="opt-group"
        >
          <el-radio v-for="(o, i) in redoOptions" :key="i" :value="letter(i)" class="opt-item">
            {{ letter(i) }}. {{ o }}
          </el-radio>
        </el-radio-group>
        <!-- 多选 -->
        <el-checkbox-group
          v-else-if="redoRow.questionType === 2"
          v-model="redoMulti"
          :disabled="redoSubmitted"
          class="opt-group"
        >
          <el-checkbox v-for="(o, i) in redoOptions" :key="i" :value="letter(i)" class="opt-item">
            {{ letter(i) }}. {{ o }}
          </el-checkbox>
        </el-checkbox-group>
        <!-- 判断 -->
        <el-radio-group
          v-else-if="redoRow.questionType === 3"
          v-model="redoAnswer"
          :disabled="redoSubmitted"
          class="opt-group"
        >
          <el-radio value="T" class="opt-item">正确（T）</el-radio>
          <el-radio value="F" class="opt-item">错误（F）</el-radio>
        </el-radio-group>
        <!-- 填空 -->
        <el-input
          v-else
          v-model="redoAnswer"
          :disabled="redoSubmitted"
          placeholder="请输入你的答案"
          style="max-width: 420px"
        />

        <!-- 重做结果 -->
        <template v-if="redoSubmitted && redoResult">
          <div class="result-banner" :class="redoResult.correct ? 'ok' : 'bad'">
            <template v-if="redoResult.correct">
              ✅ 回答正确！当前连续答对 {{ redoResult.correctCount }} 次
              <template v-if="redoResult.removed">，达到移除条件，已自动移出错题本 🎉</template>
              <template v-else>（策略阈值：连续答对 {{ cfg.threshold }} 次）</template>
            </template>
            <template v-else>
              ❌ 又做错了，正确答案是：{{ redoResult.correctAnswer }}；连续答对次数已清零
            </template>
          </div>
        </template>

        <div class="actions">
          <el-button v-if="!redoSubmitted" type="primary" :disabled="!redoHasAnswer" @click="handleRedoSubmit">
            提交重做
          </el-button>
          <template v-else>
            <el-button v-if="!redoResult?.correct" :icon="MagicStick" @click="explainFromRedo">看看 AI 讲解</el-button>
            <el-button v-if="hasNextRedo" type="primary" @click="nextRedo">下一题</el-button>
            <el-button v-else type="primary" @click="finishRedo">完成</el-button>
          </template>
        </div>
      </div>
    </el-dialog>

    <!-- 批量练习入口：选条件后进入完整做题页，不用在操作列一题一题重做 -->
    <el-dialog v-model="practiceVisible" title="错题强化练习" width="560px">
      <el-form label-width="88px">
        <el-form-item label="学科">
          <el-select v-model="practiceForm.courseId" placeholder="全部学科" clearable style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select
            v-model="practiceForm.questionTypes"
            multiple
            collapse-tags
            placeholder="全部题型"
            style="width: 100%"
          >
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="错误次数">
          <el-select v-model="practiceForm.wrongCount" placeholder="全部" clearable style="width: 180px">
            <el-option label="错 1 次" value="eq1" />
            <el-option label="错 2 次以上" value="gte2" />
            <el-option label="错 3 次以上" value="gte3" />
          </el-select>
        </el-form-item>
        <el-form-item label="练习题量">
          <el-input-number
            v-model="practiceForm.count"
            :min="1"
            :max="50"
            :step="5"
            controls-position="right"
            style="width: 140px"
          />
          <span class="practice-hint">最多 50 题，符合条件的错题不足时按实际数量出题</span>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="系统会从符合条件的错题中随机抽题组成一轮练习，做题时可随时切换「答题 / 背题」；答对会累计「连续答对次数」，达到移除策略阈值时自动移出错题本。"
      />
      <template #footer>
        <el-button @click="practiceVisible = false">取消</el-button>
        <el-button type="success" :icon="VideoPlay" @click="startPractice">开始练习</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Delete, VideoPlay, MagicStick } from '@element-plus/icons-vue'
import {
  getMyWrongQuestions,
  getWrongOverview,
  removeWrongQuestion,
  batchRemoveWrongQuestions,
  redoWrongQuestion,
  exportWrongQuestions,
  chatWrongQuestion
} from '@/api/studentWrong'
import { getPracticeConfig, savePracticeConfig } from '@/api/studentPractice'
import { getJoinedCourses } from '@/api/course'
import { useRouter } from 'vue-router'

const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const tableRef = ref(null)
const selection = ref([])
const total = ref(0)
const courses = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, courseId: null, questionType: null, wrongCount: null })

// 统计总览：错题总数 / 已做对过 / 累计答错次数 / 各学科分布
const overview = reactive({ total: 0, mastered: 0, wrongTotal: 0, courses: [] })

// 题型下拉选项（与 TYPE_MAP 保持一致）
const TYPE_OPTIONS = [
  { value: 1, label: '单选题' },
  { value: 2, label: '多选题' },
  { value: 3, label: '判断题' },
  { value: 4, label: '填空题' },
  { value: 5, label: '简答题' },
  { value: 6, label: '名词解释' }
]

const detailVisible = ref(false)
const current = ref(null)

/* ---------- AI 智能讲解（多轮对话） ---------- */
const chatMsgs = ref([]) // [{ role: 'user' | 'assistant', content }]
const chatLoading = ref(false)
const chatInput = ref('')
const explainSource = ref('') // 最新一条 AI 回复的来源：llm / local
const chatBodyRef = ref(null)

// 关闭详情时重置 AI 对话状态
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
    sendChat('请帮我讲解这道错题：说明考查的核心知识点，给出正确的解题思路与完整解析，结合我提交的错误答案分析错因，最后给出易错点与学习建议。')
  } else {
    clearChat()
  }
}

// 发送一轮提问（首问或追问都走同一接口，带上前面的历史上下文）
// text 可选：传入则用之（「生成讲解」按钮发默认开场白），否则读取输入框内容
async function sendChat(text) {
  const q = (text ?? chatInput.value).trim()
  if (!q || chatLoading.value || !current.value) return
  chatMsgs.value.push({ role: 'user', content: q })
  chatInput.value = ''
  chatLoading.value = true
  explainSource.value = ''
  scrollChatToBottom()
  // 历史 = 已 push 的去掉本轮，由后端作为上下文拼接到最新问题之前
  const history = chatMsgs.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
  try {
    const res = await chatWrongQuestion(current.value.id, { question: q, history })
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

/* ---------- 批量练习（页面上方入口）：选条件 → 跳到完整做题页 ---------- */
const router = useRouter()
const practiceVisible = ref(false)
const practiceForm = reactive({ courseId: null, questionTypes: [], wrongCount: null, count: 10 })

// 打开弹窗：默认沿用当前列表的筛选条件，减少重复选择
function openPracticeDialog() {
  if (!overview.total) {
    ElMessage.warning('错题本是空的，先去练习积累错题吧')
    return
  }
  practiceForm.courseId = query.courseId ?? null
  practiceForm.questionTypes = query.questionType ? [query.questionType] : []
  practiceForm.wrongCount = query.wrongCount ?? null
  // 默认 10 题，但不超出实际可用的错题数（当前筛选无结果时以总错题数为参考）
  const usable = total.value || overview.total || 10
  practiceForm.count = Math.min(10, Math.max(1, usable))
  practiceVisible.value = true
}

// 开始练习：带上抽题条件跳到自由做题页（source=wrong 表示从错题本抽题，
// 做题页会按错题口径同步「连续答对次数 / 自动移出」）
function startPractice() {
  const params = new URLSearchParams()
  params.set('source', 'wrong')
  if (practiceForm.courseId) params.set('courseId', practiceForm.courseId)
  if (practiceForm.questionTypes.length) params.set('questionTypes', practiceForm.questionTypes.join(','))
  const range = wrongCountRange(practiceForm.wrongCount)
  Object.entries(range).forEach(([k, v]) => params.set(k, v))
  params.set('count', practiceForm.count)
  practiceVisible.value = false
  router.push(`/student/practice/free?${params.toString()}`)
}

/* ---------- 错题移除策略 ---------- */
const cfg = reactive({ autoRemove: false, threshold: 3 })

async function loadCfg() {
  try {
    const res = await getPracticeConfig()
    cfg.autoRemove = res.data?.wrongAutoRemove === 1
    // 阈值统一收敛到 1~10，后端默认 3
    const t = Number(res.data?.wrongRemoveThreshold)
    cfg.threshold = Number.isFinite(t) && t >= 1 ? Math.min(Math.round(t), 10) : 3
  } catch (e) {}
}

async function saveCfg() {
  try {
    await savePracticeConfig({
      wrongAutoRemove: cfg.autoRemove ? 1 : 0,
      wrongRemoveThreshold: cfg.threshold
    })
    ElMessage.success('移除策略已保存')
  } catch (e) {}
}

const TYPE_MAP = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }
const typeText = (t) => TYPE_MAP[t] || '未知'

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
    const res = await getMyWrongQuestions({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      ...buildParams()
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (err) {
    console.error('加载错题失败:', err)
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  current.value = row
  resetExplain()
  detailVisible.value = true
}

/* ---------- 错题重做（单题 + 批量队列） ---------- */
const redoVisible = ref(false)
const redoQueue = ref([])   // 待重做的错题快照：单题重做时长度 1，批量重做时多道
const redoIndex = ref(0)
const redoRow = computed(() => redoQueue.value[redoIndex.value] || null)
const redoAnswer = ref('')
const redoMulti = ref([])
const redoSubmitted = ref(false)
const redoResult = ref(null)
const redoStat = ref({ done: 0, correct: 0, removed: 0 })

// 批量重做时标题带进度，单题时就是「错题重做」
const redoTitle = computed(() =>
  redoQueue.value.length > 1 ? `错题重做（第 ${redoIndex.value + 1}/${redoQueue.value.length} 题）` : '错题重做'
)
const hasNextRedo = computed(() => redoIndex.value < redoQueue.value.length - 1)

const letter = (i) => String.fromCharCode(65 + i)

// 重做题目的选项（JSON 数组字符串）
const redoOptions = computed(() => {
  if (!redoRow.value?.options) return []
  try {
    const arr = JSON.parse(redoRow.value.options)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
})

const redoHasAnswer = computed(() => {
  if (!redoRow.value) return false
  if (redoRow.value.questionType === 2) return redoMulti.value.length > 0
  return redoAnswer.value.trim() !== ''
})

// 重置单题的作答状态（切题/关弹窗时调用）
function resetRedoState() {
  redoAnswer.value = ''
  redoMulti.value = []
  redoSubmitted.value = false
  redoResult.value = null
}

// 打开重做弹窗：单题 -> 队列只有 1 道；批量 -> 传入多道题，做完一题点「下一题」
function openRedo(row) {
  startRedo([row])
}

function startRedo(rows) {
  redoQueue.value = rows.slice()
  redoIndex.value = 0
  redoStat.value = { done: 0, correct: 0, removed: 0 }
  resetRedoState()
  redoVisible.value = true
}

// 批量重做：用当前勾选的错题组一套练习
function startBatchRedo() {
  if (!selection.value.length) return
  startRedo(selection.value.slice())
}

function nextRedo() {
  if (!hasNextRedo.value) return
  redoIndex.value += 1
  resetRedoState()
}

async function handleRedoSubmit() {
  if (!redoHasAnswer.value || redoSubmitted.value || !redoRow.value) return
  const userAnswer =
    redoRow.value.questionType === 2 ? redoMulti.value.slice().sort().join(',') : redoAnswer.value
  try {
    const res = await redoWrongQuestion(redoRow.value.id, { userAnswer })
    redoResult.value = res.data || {}
    redoSubmitted.value = true
    redoStat.value.done += 1
    if (redoResult.value.correct) redoStat.value.correct += 1
    if (redoResult.value.removed) {
      redoStat.value.removed += 1
      ElMessage.success('回答正确！已自动移出错题本')
    }
    // 数据联动：重做结果立刻反映到当前列表与统计卡片
    refreshRow(redoRow.value.id, redoResult.value)
    loadOverview()
  } catch (e) {}
}

// 单题重做后局部刷新该行，避免整表重载导致分页/勾选丢失
function refreshRow(id, result) {
  const row = tableData.value.find((r) => r.id === id)
  if (!row) return
  row.correctCount = result.correctCount ?? 0
  // 不管对错，本题刚作答过：前端直接按当前时间刷新「上次作答」
  row.lastAnswerTime = nowText()
  if (result.correct) {
    // 做对不刷新「最近错误」
  } else {
    row.wrongCount = (row.wrongCount ?? 0) + 1
    row.userAnswer = result.userAnswer ?? row.userAnswer
    row.lastWrongTime = row.lastAnswerTime
  }
  if (result.removed) {
    tableData.value = tableData.value.filter((r) => r.id !== id)
    total.value = Math.max(0, total.value - 1)
  }
}

// 完成重做：批量时给出汇总提示，再刷新列表与统计
function finishRedo() {
  const { done, correct, removed } = redoStat.value
  if (done > 1) {
    ElMessage.success(`本次共重做 ${done} 题，答对 ${correct} 题${removed ? `，自动移出 ${removed} 题` : ''}`)
  }
  redoVisible.value = false
}

// 做错后一键跳到「详情」弹窗里的 AI 智能讲解，省去再点一次详情
function explainFromRedo() {
  const row = redoRow.value
  redoVisible.value = false
  if (row) openDetail(row)
}

// 弹窗关闭后刷新列表（移除/次数变化都要反映出来）
function onRedoClose() {  resetRedoState()
  clearSelection()
  loadData()
  loadOverview()
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm('确定要手动移除这道错题吗？移除后不会保留本题的错题记录（已掌握时再点）。', '手动移除错题', {
      type: 'warning',
      confirmButtonText: '确定移除',
      cancelButtonText: '再想想'
    })
    await removeWrongQuestion(row.id)
    ElMessage.success('已移除')
    loadData()
    loadOverview()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('移除失败：' + (err.message || '网络异常'))
  }
}

/* ---------- 批量操作 ---------- */
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
      `确定要手动移除选中的 ${selection.value.length} 道错题吗？移除后不会保留这些题目的错题记录。`,
      '批量移除错题',
      { type: 'warning', confirmButtonText: '确定移除', cancelButtonText: '再想想' }
    )
    const res = await batchRemoveWrongQuestions(selection.value.map((r) => r.id))
    ElMessage.success(res.msg || '批量移除成功')
    clearSelection()
    loadData()
    loadOverview()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error('批量移除失败：' + (err.msg || err.message || '网络异常'))
  }
}

/* ---------- 统计总览 ---------- */
async function loadOverview() {
  try {
    const res = await getWrongOverview()
    const d = res.data || {}
    overview.total = d.total ?? 0
    overview.mastered = d.mastered ?? 0
    overview.wrongTotal = d.wrongTotal ?? 0
    overview.courses = d.courses || []
  } catch (e) {}
}

/* ---------- 导出 Excel（当前筛选条件） ---------- */
async function handleExport() {
  if (!total.value) {
    ElMessage.warning('当前筛选条件下没有可导出的错题')
    return
  }
  exporting.value = true
  try {
    const res = await exportWrongQuestions(buildParams())
    // axios responseType: 'blob' 时返回整个 response，blob 在 .data
    const raw = res?.data || res
    const url = window.URL.createObjectURL(raw instanceof Blob ? raw : new Blob([raw]))
    const a = document.createElement('a')
    a.href = url
    a.download = `我的错题_${new Date().toISOString().slice(0, 10)}.xlsx`
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
// 时间格式化：接口返回 "yyyy-MM-dd HH:mm:ss"，表格里只展示到分钟，过长则提示完整值
function fmtTime(t) {
  if (!t) return '-'
  return String(t).slice(0, 16).replace('T', ' ')
}

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// 错误次数下拉值 → 区间参数（列表筛选与批量练习共用，避免两处口径不一致）
function wrongCountRange(v) {
  if (v === 'eq1') return { wrongCountMin: 1, wrongCountMax: 1 }
  if (v === 'gte2') return { wrongCountMin: 2 }
  if (v === 'gte3') return { wrongCountMin: 3 }
  return {}
}

function getWrongCountRange() {
  return wrongCountRange(query.wrongCount)
}

function buildParams() {
  return {
    courseId: query.courseId ?? undefined,
    questionType: query.questionType ?? undefined,
    ...getWrongCountRange()
  }
}

function handleSearch() { query.pageNum = 1; loadData() }

function handleReset() {
  query.courseId = null
  query.questionType = null
  query.wrongCount = null
  query.pageNum = 1
  loadData()
}

// 学科下拉只列【当前学生已加入的课程】：错题都来自已加入课程，
// 用全量课程列表会让没加入的学科也能被选中，与「只能查看已加入课程」的约束相悖
async function loadCourses() {
  try {
    const res = await getJoinedCourses()
    courses.value = res.data || []
  } catch (e) {}
}

onMounted(() => { loadCourses(); loadCfg(); loadData(); loadOverview() })
</script>

<style scoped>
.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
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
/* 表头与内容统一居中 */
.wrong-table :deep(.el-table__header th.el-table__cell),
.wrong-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}
/* 时间列（最近错误 / 上次作答）：日期时间必须一行显示，不折行 */
.wrong-table :deep(.nowrap-col .cell) {
  white-space: nowrap;
}
/* 操作列按钮居中：theme.css 为了让多按钮同排，把表格最后一列的 .cell 设成了 display:flex，
   flex 容器下单元格的 text-align 对子项不再生效，必须显式让主轴居中 */
.wrong-table :deep(.el-table__header th:last-child .cell),
.wrong-table :deep(.el-table__body td:last-child .cell) {
  justify-content: center;
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
  color: #409eff;
}
.stat-num.ok {
  color: #67c23a;
}
.stat-num.warn {
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
/* 批量操作条 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  font-size: 13px;
  color: #409eff;
}
.batch-bar b {
  font-size: 15px;
}
/* 连续答对次数阈值输入 */
.threshold-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}
/* 详情弹窗内的解析文本 */
.analysis {
  display: inline-block;
  vertical-align: top;
  max-width: calc(100% - 70px);
  white-space: pre-wrap;
  line-height: 1.7;
}
/* 自动移出策略 */
.strategy-card {
  margin-bottom: 16px;
}
.strategy-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}
.strategy-label {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.strategy-tip {
  font-size: 13px;
  color: #909399;
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
  color: #f56c6c;
  font-size: 16px;
}
.ans {
  font-weight: 600;
}
.ans.wrong {
  color: #f56c6c;
}
.ans.right {
  color: #67c23a;
}
.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
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
.opts {
  display: inline-block;
  vertical-align: top;
}
.opt-item {
  margin-bottom: 4px;
}
.muted {
  color: #909399;
}
/* 重做弹窗 */
.redo-count-tag {
  margin-left: 8px;
}
.opt-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  margin: 6px 0 4px 64px;
  width: calc(100% - 64px);
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
.actions {
  margin-top: 20px;
}
/* AI 讲解（多轮对话） */
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
