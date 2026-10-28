<template>
  <div class="review-workbench" v-loading="loading">
    <!-- 筛选条件 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true">
        <el-form-item label="考试">
          <el-select v-model="filterExamId" placeholder="全部考试" clearable style="width: 240px">
            <el-option
              v-for="exam in examOptions"
              :key="exam.id"
              :label="exam.examName"
              :value="exam.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="filterClassId" placeholder="全部班级" clearable style="width: 180px">
            <el-option
              v-for="cls in classOptions"
              :key="cls.id"
              :label="cls.className"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="阅卷状态">
          <el-select v-model="filterStatus" style="width: 140px">
            <el-option label="全部" :value="null" />
            <el-option label="待阅卷" :value="0" />
            <el-option label="已阅卷" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filterKeyword"
            placeholder="学号/姓名/题目"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 进度总览 -->
    <el-card class="progress-card" shadow="never">
      <div class="progress-info">
        <div class="progress-title">阅卷进度</div>
        <div class="progress-sub">
          待阅 <b class="hl">{{ pendingPapers }}</b> 份卷 ·
          待批 <b class="hl">{{ pendingQuestions }}</b> 道题 ·
          已阅 <b class="hl-green">{{ gradedQuestions }}</b> 道题
        </div>
      </div>
      <el-progress
        class="progress-bar"
        :percentage="reviewProgress"
        :stroke-width="12"
        :color="reviewProgress >= 100 ? '#67c23a' : '#409eff'"
      />
    </el-card>

    <el-alert
      v-if="truncated"
      class="tip-alert"
      type="info"
      :closable="false"
      show-icon
      :title="`当前筛选下共 ${backendTotal} 条答题记录，工作台已加载前 ${FETCH_SIZE} 条，请用考试 / 班级 / 关键词缩小范围`"
    />

    <!-- 工作台：左队列 · 右逐题 -->
    <div class="workbench">
      <!-- 左：待阅学生队列（按学生聚合整份卷） -->
      <el-card class="queue-card" shadow="never">
        <template #header>
          <div class="queue-head">
            <span>待阅学生 <b>{{ studentTotal }}</b></span>
            <el-checkbox v-model="onlyPending" size="small">仅未完成</el-checkbox>
          </div>
        </template>

        <el-input
          v-model="queueKeyword"
          class="queue-search"
          size="small"
          placeholder="搜索姓名 / 学号 / 考试"
          clearable
        />

        <div class="queue-list">
          <div
            v-for="g in pagedGroups"
            :key="g.recordId"
            class="queue-item"
            :class="{ active: g.recordId === currentRecordId, done: g.pending === 0 }"
            @click="selectStudent(g)"
          >
            <div class="qi-top">
              <span class="qi-name">{{ g.realName }}</span>
              <el-tag
                size="small"
                effect="plain"
                :type="g.pending === 0 ? 'success' : 'warning'"
              >
                {{ g.pending === 0 ? '已阅完' : `待阅 ${g.pending}/${g.items.length}` }}
              </el-tag>
            </div>
            <div class="qi-sub">{{ g.username }} · {{ g.className }}</div>
            <div class="qi-sub qi-exam">{{ g.examName }}</div>
            <div class="qi-sub qi-time">{{ g.submitTime || '-' }}</div>
          </div>

          <el-empty
            v-if="!pagedGroups.length"
            :image-size="60"
            :description="queueEmptyText"
          />
        </div>

        <div class="queue-foot">
          <el-button size="small" :disabled="studentPage <= 1" @click="studentPage -= 1">
            上一页
          </el-button>
          <span class="page-text">{{ studentPage }} / {{ studentPages }}</span>
          <el-button
            size="small"
            :disabled="studentPage >= studentPages"
            @click="studentPage += 1"
          >
            下一页
          </el-button>
        </div>
      </el-card>

      <!-- 右：逐题批阅（一题一屏） -->
      <el-card class="review-card" shadow="never">
        <template v-if="currentGroup && currentItem">
          <!-- 学生信息 -->
          <div class="rv-head">
            <div>
              <div class="rv-name">
                {{ currentGroup.realName }}
                <span class="rv-uid">{{ currentGroup.username }}</span>
              </div>
              <div class="rv-sub">
                {{ currentGroup.className }} · {{ currentGroup.examName }} · 交卷
                {{ currentGroup.submitTime || '-' }}
              </div>
            </div>
            <div class="rv-actions">
              <el-button size="small" @click="openFullReview(currentGroup)">整卷阅卷</el-button>
              <el-button size="small" :disabled="!hasPrevStudent" @click="goStudent(-1)">
                上一位
              </el-button>
              <el-button size="small" :disabled="!hasNextStudent" @click="goStudent(1)">
                下一位
              </el-button>
            </div>
          </div>

          <!-- 题号导航 -->
          <div class="q-nav">
            <span
              v-for="(it, idx) in currentItems"
              :key="it.answerId"
              class="q-dot"
              :class="{ done: it.reviewStatus === 1, cur: idx === currentQuestionIndex }"
              @click="currentQuestionIndex = idx"
            >
              {{ idx + 1 }}
            </span>
            <span class="q-nav-tip">
              已给分 {{ gradedCount }}/{{ currentItems.length }} 题
            </span>
          </div>

          <!-- 当前题 -->
          <div class="q-body">
            <div class="q-head">
              <span class="q-index">第 {{ currentQuestionIndex + 1 }} 题</span>
              <span class="q-type">{{ typeText(currentItem.questionType) }}</span>
              <span class="q-score">满分 {{ currentItem.questionScore ?? '-' }} 分</span>
              <el-tag
                size="small"
                effect="plain"
                :type="currentItem.reviewStatus === 1 ? 'success' : 'warning'"
              >
                {{ currentItem.reviewStatus === 1 ? '已阅' : '待阅' }}
              </el-tag>
            </div>

            <div class="q-content">{{ currentItem.content }}</div>

            <div class="block-label">
              学生作答
              <span v-if="answerLength" class="block-meta">共 {{ answerLength }} 字</span>
            </div>
            <div class="answer-box" :class="{ 'is-empty': !answerLength }">
              {{ answerLength ? studentAnswerText : '（未作答）' }}
            </div>

            <div class="std-toggle" @click="toggleStd(currentItem)">
              <el-icon class="caret" :class="{ open: stdOpen[currentItem.answerId] }">
                <CaretRight />
              </el-icon>
              参考答案
            </div>
            <div v-if="stdOpen[currentItem.answerId]" class="std-box">
              <span v-if="stdMap[currentItem.answerId]?.loading">加载中…</span>
              <span v-else>{{ stdMap[currentItem.answerId]?.answer || '（无参考答案）' }}</span>
            </div>

            <!-- 打分 -->
            <div class="score-row">
              <span class="field-label">给分</span>
              <el-input-number
                v-model="form.score"
                :min="0"
                :max="scoreMax || undefined"
                :step="0.5"
                :precision="1"
                controls-position="right"
                style="width: 140px"
              />
              <span class="field-label">/ {{ currentItem.questionScore ?? '-' }} 分</span>
              <div class="quick-chips">
                <el-button size="small" @click="setScore(0)">0 分</el-button>
                <el-button size="small" @click="setScore(halfScore)">半分</el-button>
                <el-button size="small" @click="setScore(scoreMax)">满分</el-button>
                <el-button
                  type="primary"
                  plain
                  size="small"
                  :icon="MagicStick"
                  :loading="aiLoadingId === currentItem.answerId"
                  @click="aiScoreCurrent"
                >
                  AI 预评分
                </el-button>
              </div>
            </div>

            <div class="comment-row">
              <span class="field-label">评语</span>
              <el-input
                v-model="form.opinion"
                placeholder="扣分原因 / 答题点评（选填）"
                maxlength="200"
                show-word-limit
                clearable
              />
            </div>

            <!-- AI 预评分依据：命中/缺失得分点与置信度，供教师复核后采纳 -->
            <div v-if="currentAi" class="ai-panel">
              <div class="ai-panel-head">
                <span class="ai-title">
                  <el-icon><MagicStick /></el-icon>
                  AI 评分依据
                </span>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="currentAi.source === 'llm' ? 'success' : 'info'"
                >
                  {{ currentAi.source === 'llm' ? '大模型' : '本地规则引擎' }}
                </el-tag>
                <el-tag size="small" effect="plain" type="warning">
                  置信度 {{ Math.round((Number(currentAi.confidence) || 0) * 100) }}%
                </el-tag>
                <span class="ai-score">建议 {{ currentAi.score }}/{{ currentAi.maxScore }} 分</span>
                <el-button link size="small" @click="clearAi(currentItem.answerId)">收起</el-button>
              </div>
              <div class="ai-reason">{{ currentAi.reason || '（无评分依据）' }}</div>
              <div v-if="currentAi.hits?.length" class="ai-points">
                <span class="ai-points-label hit">命中</span>
                <el-tag
                  v-for="(h, i) in currentAi.hits"
                  :key="`ai-hit-${i}`"
                  size="small"
                  type="success"
                  effect="plain"
                >
                  {{ h }}
                </el-tag>
              </div>
              <div v-if="currentAi.missing?.length" class="ai-points">
                <span class="ai-points-label miss">缺失</span>
                <el-tag
                  v-for="(m, i) in currentAi.missing"
                  :key="`ai-miss-${i}`"
                  size="small"
                  type="danger"
                  effect="plain"
                >
                  {{ m }}
                </el-tag>
              </div>
              <div v-if="currentAi.warning" class="ai-warning">{{ currentAi.warning }}</div>
            </div>
          </div>

          <!-- 底部操作 -->
          <div class="rv-footer">
            <span class="hint">Ctrl + Enter 保存并下一题</span>
            <div class="rv-footer-btns">
              <el-button :disabled="currentQuestionIndex === 0" @click="prevQuestion">
                上一题
              </el-button>
              <el-button
                :icon="MagicStick"
                :loading="aiBatchLoading"
                :disabled="!studentPendingCount"
                @click="aiScoreAllPending"
              >
                AI 预评待阅题（{{ studentPendingCount }}）
              </el-button>
              <el-button type="primary" :loading="submitting" @click="saveAndNext">
                保存并下一题
              </el-button>
              <el-button
                :loading="submittingAll"
                :disabled="!studentPendingCount"
                @click="saveAllForStudent"
              >
                保存该生全部（{{ studentPendingCount }}）
              </el-button>
            </div>
          </div>
        </template>

        <el-empty v-else :description="panelEmptyText" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, CaretRight, MagicStick } from '@element-plus/icons-vue'
import {
  getReviewList,
  getAnswerDetail,
  saveScore,
  aiScoreAnswer,
  aiScoreBatch
} from '@/api/examReview'
import { getClassList } from '@/api/sysClass'
import { getExamPage } from '@/api/exam'

/** 单次拉取的答题行数：前端按学生聚合，故一次多取一些 */
const FETCH_SIZE = 500
/** 左侧队列每页学生数 */
const STUDENT_PAGE_SIZE = 10

const router = useRouter()

const loading = ref(false)
const rows = ref([])
const backendTotal = ref(0)

// 筛选条件
const filterExamId = ref(null)
const filterClassId = ref(null)
// 默认「全部」：保证进度统计和已阅回看都是完整口径
const filterStatus = ref(null)
const filterKeyword = ref('')
const examOptions = ref([])
const classOptions = ref([])

// 左侧队列
const queueKeyword = ref('')
const onlyPending = ref(true)
const studentPage = ref(1)
const currentRecordId = ref(null)
const currentQuestionIndex = ref(0)

// 阅卷数据
const reviewMap = reactive({})
const stdMap = reactive({})
const stdOpen = reactive({})

const submitting = ref(false)
const submittingAll = ref(false)

// AI 预评分
const aiLoadingId = ref(null)
const aiBatchLoading = ref(false)
/** answerId -> AI 预评分结果（含评分依据，仅用于教师侧展示，不落库） */
const aiMap = reactive({})

const TYPE_MAP = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
const typeText = (t) => TYPE_MAP[t] || '主观题'

/* ---------------- 数据聚合：一行一题 → 一位学生一份卷 ---------------- */
const groups = computed(() => {
  const map = new Map()
  rows.value.forEach((row) => {
    const key = row.recordId
    if (!map.has(key)) {
      map.set(key, {
        recordId: key,
        realName: row.realName || '-',
        username: row.username || '-',
        className: row.className || '-',
        examName: row.examName || '-',
        submitTime: row.submitTime || '',
        items: []
      })
    }
    map.get(key).items.push(row)
  })
  const list = Array.from(map.values()).map((g) => {
    // 同一份卷内按答题顺序（answerId 递增）排列，保证题号稳定
    g.items.sort((a, b) => (a.answerId || 0) - (b.answerId || 0))
    g.pending = g.items.filter((i) => i.reviewStatus === 0).length
    return g
  })
  // 待阅多的排前面，其次按交卷时间倒序
  list.sort((a, b) => b.pending - a.pending || String(b.submitTime).localeCompare(String(a.submitTime)))
  return list
})

const visibleGroups = computed(() => {
  const kw = queueKeyword.value.trim().toLowerCase()
  let list = groups.value
  if (onlyPending.value) list = list.filter((g) => g.pending > 0)
  if (kw) {
    list = list.filter((g) =>
      `${g.realName}${g.username}${g.className}${g.examName}`.toLowerCase().includes(kw)
    )
  }
  return list
})

const studentTotal = computed(() => visibleGroups.value.length)
const studentPages = computed(() => Math.max(1, Math.ceil(studentTotal.value / STUDENT_PAGE_SIZE)))
const pagedGroups = computed(() => {
  const start = (studentPage.value - 1) * STUDENT_PAGE_SIZE
  return visibleGroups.value.slice(start, start + STUDENT_PAGE_SIZE)
})

const currentGroup = computed(
  () => groups.value.find((g) => g.recordId === currentRecordId.value) || null
)
const currentItems = computed(() => currentGroup.value?.items || [])
const currentItem = computed(() => currentItems.value[currentQuestionIndex.value] || null)

const gradedCount = computed(() => currentItems.value.filter((i) => i.reviewStatus === 1).length)
const studentPendingCount = computed(() => currentItems.value.filter((i) => i.reviewStatus === 0).length)

// 统计
const totalQuestions = computed(() => rows.value.length)
const pendingQuestions = computed(() => rows.value.filter((r) => r.reviewStatus === 0).length)
const gradedQuestions = computed(() => totalQuestions.value - pendingQuestions.value)
const reviewProgress = computed(() =>
  totalQuestions.value ? Math.round((gradedQuestions.value / totalQuestions.value) * 100) : 0
)
const pendingPapers = computed(() => groups.value.filter((g) => g.pending > 0).length)
const truncated = computed(() => backendTotal.value > rows.value.length)

const hasPrevStudent = computed(() => {
  const i = visibleGroups.value.findIndex((g) => g.recordId === currentRecordId.value)
  return i > 0
})
const hasNextStudent = computed(() => {
  const i = visibleGroups.value.findIndex((g) => g.recordId === currentRecordId.value)
  return i >= 0 && i < visibleGroups.value.length - 1
})

const queueEmptyText = computed(() => {
  if (!rows.value.length) return filterStatus.value === 0 ? '没有待阅的主观题' : '没有阅卷记录'
  if (!onlyPending.value) return '没有符合条件的学生'
  return filterStatus.value === 0 ? '没有待阅的主观题' : '太棒了，主观题已全部阅完'
})
const panelEmptyText = computed(() =>
  studentTotal.value ? '请从左侧选择一位学生开始批阅' : '暂无可批阅的学生'
)

/* ---------------- 当前题的作答/给分 ---------------- */
/** 去掉学生作答里回显的答题提示，只留正文 */
function cleanAnswer(text) {
  if (!text) return ''
  return String(text)
    .replace(/^\s*\*{0,2}\s*(answer|答案|答)\s*[:：]\s*\*{0,2}/i, '')
    .replace(/\*{2}/g, '')
    .trim()
}

const studentAnswerText = computed(() => cleanAnswer(currentItem.value?.userAnswer))
const answerLength = computed(() => studentAnswerText.value.length)
const scoreMax = computed(() => Number(currentItem.value?.questionScore) || 0)
const halfScore = computed(() => {
  const half = scoreMax.value / 2
  return Number.isInteger(scoreMax.value) ? Math.round(half * 2) / 2 : Math.round(half * 10) / 10
})

/** 当前题的 AI 预评分结果（教师侧评分依据） */
const currentAi = computed(() =>
  currentItem.value ? aiMap[currentItem.value.answerId] || null : null
)

/** 当前题的给分表单：直接读写 reviewMap，切题不丢未保存内容 */
const form = computed(() => {
  const item = currentItem.value
  if (!item) return { score: null, opinion: '' }
  return reviewMap[item.answerId] || { score: null, opinion: '' }
})

function ensureEntry(item) {
  if (!item) return
  if (!reviewMap[item.answerId]) {
    reviewMap[item.answerId] = {
      score: item.reviewScore != null ? Number(item.reviewScore) : null,
      opinion: item.reviewOpinion || ''
    }
  }
}

function initReviewMap() {
  Object.keys(reviewMap).forEach((k) => delete reviewMap[k])
  Object.keys(stdMap).forEach((k) => delete stdMap[k])
  Object.keys(stdOpen).forEach((k) => delete stdOpen[k])
  rows.value.forEach(ensureEntry)
}

watch(currentItem, (item) => {
  if (!item) return
  ensureEntry(item)
  // 参考答案默认展开并懒加载，便于对点给分
  if (stdOpen[item.answerId] === undefined) {
    stdOpen[item.answerId] = true
    loadStdAnswer(item.answerId)
  }
})

// 队列筛选/阅卷后学生数变化时，页码回落到有效范围
watch(studentTotal, () => {
  if (studentPage.value > studentPages.value) studentPage.value = studentPages.value
})

function setScore(v) {
  if (!currentItem.value) return
  ensureEntry(currentItem.value)
  reviewMap[currentItem.value.answerId].score = Number(v) || 0
}

function toggleStd(item) {
  const id = item.answerId
  stdOpen[id] = !stdOpen[id]
  if (stdOpen[id] && !stdMap[id]) loadStdAnswer(id)
}

function loadStdAnswer(answerId) {
  stdMap[answerId] = { loading: true, answer: '' }
  getAnswerDetail(answerId)
    .then((res) => {
      stdMap[answerId] = { loading: false, answer: res.data?.correctAnswer || '' }
    })
    .catch(() => {
      stdMap[answerId] = { loading: false, answer: '' }
    })
}

/* ---------------- 选择 / 导航 ---------------- */
function firstPendingIndex(group) {
  if (!group) return 0
  const idx = group.items.findIndex((i) => i.reviewStatus === 0)
  return idx >= 0 ? idx : 0
}

function syncStudentPage(group) {
  const i = visibleGroups.value.findIndex((g) => g.recordId === group.recordId)
  if (i >= 0) studentPage.value = Math.floor(i / STUDENT_PAGE_SIZE) + 1
}

function selectStudent(group) {
  currentRecordId.value = group.recordId
  currentQuestionIndex.value = firstPendingIndex(group)
  syncStudentPage(group)
}

function goStudent(delta) {
  const list = visibleGroups.value
  const i = list.findIndex((g) => g.recordId === currentRecordId.value)
  const target = list[i + delta]
  if (target) selectStudent(target)
}

function prevQuestion() {
  if (currentQuestionIndex.value > 0) currentQuestionIndex.value -= 1
}

/** 保存后跳到本题之后的下一道待阅题，再不行就找下一位学生 */
function gotoNextPending(fromAnswerId) {
  const group = currentGroup.value
  if (group) {
    const rest = group.items.filter(
      (i) => i.reviewStatus === 0 && i.answerId !== fromAnswerId
    )
    if (rest.length) {
      currentQuestionIndex.value = group.items.indexOf(rest[0])
      return true
    }
  }
  const list = visibleGroups.value
  const cur = list.findIndex((g) => g.recordId === currentRecordId.value)
  const next =
    list.slice(cur + 1).find((g) => g.pending > 0) ||
    list.slice(0, Math.max(cur, 0)).find((g) => g.pending > 0)
  if (next) {
    selectStudent(next)
    return true
  }
  return false
}

function applyLocal(item, score, opinion) {
  item.reviewStatus = 1
  item.reviewScore = score
  item.reviewOpinion = opinion
}

/* ---------------- 保存 ---------------- */
function validateScore(item) {
  const entry = reviewMap[item.answerId] || {}
  if (entry.score === null || entry.score === undefined || entry.score === '') {
    ElMessage.warning('请先输入得分')
    return null
  }
  const score = Number(entry.score)
  const max = Number(item.questionScore) || 0
  if (Number.isNaN(score) || score < 0) {
    ElMessage.warning('得分不能为负')
    return null
  }
  if (max > 0 && score > max) {
    ElMessage.warning(`得分不能超过满分 ${max} 分`)
    return null
  }
  return score
}

function saveAndNext() {
  const item = currentItem.value
  if (!item || submitting.value) return
  const score = validateScore(item)
  if (score === null) return
  const opinion = reviewMap[item.answerId].opinion || ''
  submitting.value = true
  saveScore({ answerId: item.answerId, reviewScore: score, reviewOpinion: opinion })
    .then(() => {
      applyLocal(item, score, opinion)
      ElMessage.success('已保存')
      if (!gotoNextPending(item.answerId)) ElMessage.info('已无待阅题目')
    })
    .catch((err) => {
      ElMessage.error(err?.message || '保存失败')
    })
    .finally(() => {
      submitting.value = false
    })
}

async function saveAllForStudent() {
  const group = currentGroup.value
  if (!group || submittingAll.value) return
  const targets = group.items.filter((i) => {
    const e = reviewMap[i.answerId]
    return i.reviewStatus === 0 && e && e.score !== null && e.score !== undefined
  })
  if (!targets.length) {
    ElMessage.warning('请先为至少一道题输入得分')
    return
  }
  submittingAll.value = true
  let ok = 0
  try {
    for (const item of targets) {
      const entry = reviewMap[item.answerId]
      // 每道题独立校验，超分/负分的跳过
      const score = Number(entry.score)
      const max = Number(item.questionScore) || 0
      if (Number.isNaN(score) || score < 0 || (max > 0 && score > max)) continue
      await saveScore({
        answerId: item.answerId,
        reviewScore: score,
        reviewOpinion: entry.opinion || ''
      })
      applyLocal(item, score, entry.opinion || '')
      ok += 1
    }
    if (ok) {
      ElMessage.success(`已保存 ${ok} 道题`)
      if (!gotoNextPending(null)) ElMessage.info('已无待阅题目')
    } else {
      ElMessage.warning('没有可保存的得分，请检查是否超出满分')
    }
  } catch (err) {
    ElMessage.error(err?.message || '保存失败')
  } finally {
    submittingAll.value = false
  }
}

/* ---------------- AI 预评分 ---------------- */

/** 把 AI 建议得分/评语填入表单，并保留评分依据供教师复核 */
function applyAiResult(res) {
  if (!res || res.answerId === null || res.answerId === undefined) return
  const item = rows.value.find((r) => r.answerId === res.answerId)
  if (!item) return
  ensureEntry(item)
  const entry = reviewMap[item.answerId]
  if (res.score !== null && res.score !== undefined && !Number.isNaN(Number(res.score))) {
    entry.score = Number(res.score)
  }
  // AI 评语只是建议，教师可自行改写
  if (res.comment) entry.opinion = res.comment
  aiMap[item.answerId] = res
}

function clearAi(answerId) {
  delete aiMap[answerId]
}

/** 单题 AI 预评分：只填建议，仍需教师确认或调整后再保存 */
function aiScoreCurrent() {
  const item = currentItem.value
  if (!item || aiLoadingId.value) return
  aiLoadingId.value = item.answerId
  aiScoreAnswer(item.answerId)
    .then((res) => {
      applyAiResult(res.data)
      const ai = res.data || {}
      ElMessage.success(`AI 建议 ${ai.score}/${ai.maxScore} 分，确认或调整后保存`)
    })
    .catch(() => {
      // 失败提示由响应拦截器统一弹出，避免重复提示
    })
    .finally(() => {
      aiLoadingId.value = null
    })
}

/** 整份卷待阅题一键 AI 预评：预评后仍需教师核对，再点「保存该生全部」落库 */
function aiScoreAllPending() {
  const group = currentGroup.value
  if (!group || aiBatchLoading.value) return
  const pending = group.items.filter((i) => i.reviewStatus === 0)
  if (!pending.length) {
    ElMessage.warning('该生已无待阅题目')
    return
  }
  // 后端单次最多 10 题，超出部分提示教师分两次预评
  const ids = pending.slice(0, 10).map((i) => i.answerId)
  aiBatchLoading.value = true
  aiScoreBatch(ids)
    .then((res) => {
      const list = res.data || []
      list.forEach(applyAiResult)
      ElMessage.success(res.msg || `已预评 ${list.length} 道题，请核对后保存`)
      if (pending.length > ids.length) {
        ElMessage.info(`单次最多预评 10 道题，本次已预评前 ${ids.length} 道`)
      }
    })
    .catch(() => {
      // 失败提示由响应拦截器统一弹出
    })
    .finally(() => {
      aiBatchLoading.value = false
    })
}

function openFullReview(group) {
  router.push(`/grades/${group.recordId}/review`)
}

/* ---------------- 加载 ---------------- */
function loadList() {
  loading.value = true
  getReviewList({
    examId: filterExamId.value ?? undefined,
    classId: filterClassId.value ?? undefined,
    reviewStatus: filterStatus.value === null ? undefined : filterStatus.value,
    keyword: filterKeyword.value || undefined,
    pageNum: 1,
    pageSize: FETCH_SIZE
  })
    .then((res) => {
      const data = res.data || {}
      rows.value = data.records || []
      backendTotal.value = data.total || 0
      initReviewMap()
      studentPage.value = 1
      const keep = groups.value.find((g) => g.recordId === currentRecordId.value)
      const target = keep || groups.value.find((g) => g.pending > 0) || groups.value[0] || null
      if (target) selectStudent(target)
      else {
        currentRecordId.value = null
        currentQuestionIndex.value = 0
      }
    })
    .catch((err) => {
      ElMessage.error(err?.message || '加载失败')
    })
    .finally(() => {
      loading.value = false
    })
}

async function loadFilters() {
  try {
    const [cls, exams] = await Promise.all([
      getClassList(),
      getExamPage({ pageNum: 1, pageSize: 200 })
    ])
    classOptions.value = cls.data || []
    const list = exams.data?.records || (Array.isArray(exams.data) ? exams.data : [])
    examOptions.value = list
  } catch (e) {
    // 下拉加载失败不阻塞阅卷
  }
}

function handleSearch() {
  loadList()
}

function resetFilter() {
  filterExamId.value = null
  filterClassId.value = null
  filterStatus.value = null
  filterKeyword.value = ''
  queueKeyword.value = ''
  onlyPending.value = true
  loadList()
}

function onKeydown(e) {
  if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    saveAndNext()
  }
}

onMounted(() => {
  loadFilters()
  loadList()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.review-workbench {
  padding: 4px;
}

.filter-card {
  margin-bottom: 16px;
}

/* 进度总览 */
.progress-card {
  margin-bottom: 16px;
}
.progress-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 32px;
}
.progress-info {
  min-width: 320px;
}
.progress-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.progress-sub {
  font-size: 13px;
  color: #909399;
}
.progress-sub .hl {
  color: #e6a23c;
  font-size: 15px;
}
.progress-sub .hl-green {
  color: #67c23a;
  font-size: 15px;
}
.progress-bar {
  flex: 1;
  max-width: 520px;
}

.tip-alert {
  margin-bottom: 12px;
}

/* 工作台布局 */
.workbench {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* 左侧队列 */
.queue-card :deep(.el-card__body) {
  padding: 12px;
}
.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.queue-search {
  margin-bottom: 10px;
}
.queue-list {
  max-height: 560px;
  overflow-y: auto;
}
.queue-item {
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.queue-item:hover {
  border-color: #c6e2ff;
  background: #f7fbff;
}
.queue-item.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 1px #409eff inset;
}
.queue-item.done {
  opacity: 0.75;
}
.qi-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.qi-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.qi-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.qi-exam {
  color: #606266;
}
.qi-time {
  font-family: Consolas, monospace;
}
.queue-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}
.page-text {
  font-size: 13px;
  color: #909399;
}

/* 右侧批阅区 */
.rv-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.rv-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.rv-uid {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
  margin-left: 8px;
}
.rv-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.rv-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 题号导航 */
.q-nav {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.q-dot {
  width: 28px;
  height: 28px;
  line-height: 26px;
  text-align: center;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
}
.q-dot:hover {
  border-color: #409eff;
  color: #409eff;
}
.q-dot.done {
  background: #f0f9eb;
  border-color: #b3e19d;
  color: #67c23a;
}
.q-dot.cur {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
  font-weight: 600;
}
.q-nav-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

/* 题干与作答 */
.q-body {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.q-index {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.q-type {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f9ecff;
  color: #a855f7;
}
.q-score {
  font-size: 13px;
  color: #909399;
}
.q-content {
  font-size: 15px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
.block-label {
  font-size: 13px;
  color: #909399;
  margin: 14px 0 6px;
}
.block-meta {
  margin-left: 6px;
  color: #c0c4cc;
}
.answer-box {
  background: #f4f8ff;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
}
.answer-box.is-empty {
  color: #c0c4cc;
}

.std-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
  user-select: none;
}
.caret {
  transition: transform 0.2s;
}
.caret.open {
  transform: rotate(90deg);
}
.std-box {
  margin-top: 6px;
  background: #f7f9fb;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.7;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow-y: auto;
}

/* AI 预评分依据 */
.ai-panel {
  margin-top: 12px;
  border: 1px dashed #c6e2ff;
  background: #f5faff;
  border-radius: 6px;
  padding: 10px 14px;
}
.ai-panel-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.ai-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}
.ai-score {
  font-size: 12px;
  color: #909399;
}
.ai-reason {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}
.ai-points {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.ai-points-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #909399;
}
.ai-points-label.hit {
  color: #67c23a;
}
.ai-points-label.miss {
  color: #f56c6c;
}
.ai-warning {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #e6a23c;
}

/* 打分 */
.score-row,
.comment-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}
.field-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}
.quick-chips {
  display: flex;
  gap: 8px;
  margin-left: 6px;
}
.comment-row :deep(.el-input) {
  flex: 1;
  min-width: 240px;
}

/* 底部操作 */
.rv-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
.hint {
  font-size: 12px;
  color: #c0c4cc;
}
.rv-footer-btns {
  display: flex;
  gap: 10px;
}

@media (max-width: 1100px) {
  .workbench {
    grid-template-columns: 1fr;
  }
  .queue-list {
    max-height: 320px;
  }
}
</style>
