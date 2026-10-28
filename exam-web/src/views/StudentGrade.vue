<template>
  <div class="my-grade">
    <div class="page-title">
      <h2>我的成绩</h2>
      <span class="tip">所有已交卷的考试成绩都在这里，教师批阅完成后可点开查看试卷详情与参考答案</span>
    </div>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border class="grade-table">
        <el-table-column type="index" label="序号" width="70" align="center" header-align="center" />

        <el-table-column prop="paperName" label="试卷" min-width="180" align="center" header-align="center" show-overflow-tooltip />

        <el-table-column label="得分 / 满分" min-width="140" align="center" header-align="center">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.status === 1"
              content="本卷含主观题，当前为客观题与已批阅部分的分数，教师批阅完成后会更新"
              placement="top"
            >
              <span class="score" :class="row.isPass === 1 ? 'pass' : 'fail'">
                {{ row.totalScore }} / {{ row.paperTotalScore || '-' }}
              </span>
            </el-tooltip>
            <span v-else class="score" :class="row.isPass === 1 ? 'pass' : 'fail'">
              {{ row.totalScore }} / {{ row.paperTotalScore || '-' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="及格分" width="90" align="center" header-align="center">
          <template #default="{ row }">{{ row.passScore }}</template>
        </el-table-column>

        <el-table-column label="得分率" width="110" align="center" header-align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="rate(row)"
              :color="row.isPass === 1 ? '#67c23a' : '#f56c6c'"
              :stroke-width="10"
            />
          </template>
        </el-table-column>

        <el-table-column label="是否及格" width="100" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="row.isPass === 1 ? 'success' : 'danger'" effect="light">
              {{ row.isPass === 1 ? '及格' : '不及格' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" type="success" effect="plain">已判分</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" effect="plain">批阅中</el-tag>
            <el-tag v-else type="info" effect="plain">考试中</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="交卷时间" width="180" align="center" header-align="center">
          <template #default="{ row }">{{ row.submitTime || '-' }}</template>
        </el-table-column>

        <el-table-column label="操作" width="130" align="center" header-align="center" fixed="right">
          <template #default="{ row }">
            <!-- 考试结束后才放行；未结束时置灰并说明原因，避免点了报错 -->
            <el-tooltip
              :disabled="canViewPaper(row)"
              content="本场考试结束后才能查看试卷"
              placement="top"
            >
              <span>
                <el-button
                  type="primary"
                  link
                  :icon="View"
                  :disabled="!canViewPaper(row)"
                  @click="openDetail(row)"
                >查看试卷</el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0" description="你还没有参加过任何考试" />
    </el-card>

    <!-- 试卷回看：逐题作答 + 得分 + 教师评语 +（考后）参考答案与解析 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail.paperName ? `试卷详情 · ${detail.paperName}` : '试卷详情'"
      size="62%"
      direction="rtl"
    >
      <div v-loading="detailLoading" class="paper-detail">
        <template v-if="detail.recordId">
          <!-- 成绩概览 -->
          <div class="score-hero">
            <div class="hero-score" :class="detail.isPass === 1 ? 'pass' : 'fail'">
              <span class="num">{{ detail.totalScore }}</span>
              <span class="total">/ {{ detail.paperTotalScore || '-' }} 分</span>
            </div>
            <div class="hero-meta">
              <div class="meta-line">
                <el-tag :type="detail.isPass === 1 ? 'success' : 'danger'" effect="light">
                  {{ detail.isPass === 1 ? '及格' : '不及格' }}
                </el-tag>
                <el-tag v-if="detail.rank" type="warning" effect="plain">
                  本卷排名第 {{ detail.rank }} 名
                </el-tag>
                <el-tag :type="detail.fullyReviewed ? 'success' : 'warning'" effect="plain">
                  {{ detail.fullyReviewed ? '已判分' : '教师批阅中' }}
                </el-tag>
              </div>
              <div class="meta-line muted">
                及格分 {{ detail.passScore ?? '-' }} · 得分率 {{ heroRate }}% · 交卷时间 {{ detail.submitTime || '-' }}
              </div>
            </div>
          </div>

          <!-- 批阅进度 / 答案解析可见性提示 -->
          <el-alert
            v-if="!detail.fullyReviewed"
            type="warning"
            :closable="false"
            show-icon
            class="tip-alert"
            :title="pendingTip"
          />
          <el-alert
            v-else
            :type="detail.answerVisible ? 'success' : 'info'"
            :closable="false"
            show-icon
            class="tip-alert"
            :title="detail.answerTip"
          />

          <!-- 逐题回看 -->
          <div
            v-for="(q, idx) in detail.questions"
            :key="q.questionId"
            class="q-card"
            :class="cardClass(q)"
          >
            <div class="q-head">
              <span class="q-no">第 {{ idx + 1 }} 题</span>
              <el-tag size="small" effect="plain" :type="typeTag(q.questionType)">
                {{ typeText(q.questionType) }}
              </el-tag>
              <span class="q-total">满分 {{ q.score }} 分</span>
              <span class="q-gain" :class="gainClass(q)">
                {{ isPending(q) ? '待教师批阅' : `得 ${q.getScore ?? 0} 分` }}
              </span>
            </div>

            <p class="q-content">{{ q.content }}</p>

            <!-- 选择题选项：标出我的选择与正确答案 -->
            <div v-if="optionList(q).length" class="q-options">
              <div
                v-for="(opt, i) in optionList(q)"
                :key="i"
                class="opt-line"
                :class="{
                  mine: pickedLetters(q).includes(letter(i)),
                  right: detail.answerVisible && answerLetters(q).includes(letter(i))
                }"
              >
                <span class="opt-letter">{{ letter(i) }}</span>
                <span class="opt-text">{{ opt }}</span>
                <span v-if="pickedLetters(q).includes(letter(i))" class="opt-mark mine-mark">我的选择</span>
                <span
                  v-if="detail.answerVisible && answerLetters(q).includes(letter(i))"
                  class="opt-mark right-mark"
                >
                  正确答案
                </span>
              </div>
            </div>

            <!-- 我的作答 -->
            <div class="ans-row">
              <span class="ans-label">我的作答</span>
              <span class="ans-value" :class="{ empty: !q.answered }">
                {{ displayAnswer(q.userAnswer, q) || '未作答' }}
              </span>
              <el-tag
                v-if="!isPending(q)"
                size="small"
                :type="q.isCorrect === 1 ? 'success' : 'danger'"
                effect="light"
              >
                {{ q.isCorrect === 1 ? '答对' : '答错' }}
              </el-tag>
            </div>

            <!-- 参考答案（考试结束后可见） -->
            <div class="ans-row">
              <span class="ans-label">参考答案</span>
              <span v-if="detail.answerVisible" class="ans-value">
                {{ displayAnswer(q.correctAnswer, q) || '-' }}
              </span>
              <span v-else class="ans-value locked">考试结束后可见</span>
            </div>

            <!-- 教师评语 -->
            <div v-if="q.teacherComment" class="comment-box">
              <el-icon><ChatDotSquare /></el-icon>
              <span>教师评语：{{ q.teacherComment }}</span>
            </div>

            <!-- 解析 -->
            <div v-if="q.analysis" class="analysis-box">
              <span class="analysis-label">
                <el-icon><Reading /></el-icon> 解析
              </span>
              <div class="analysis-text">{{ q.analysis }}</div>
            </div>

            <!-- AI 讲解：考试结束后才开放（后端按考试是否结束硬校验，未结束返回 400） -->
            <AiExplainChat
              v-if="detail.answerVisible"
              hint="AI 会结合你的作答分析错因，可继续追问"
              :explain-fetcher="() => explainRecordQuestion(detail.recordId, q.questionId)"
              :chat-fetcher="(payload) => chatRecordQuestion(detail.recordId, q.questionId, payload)"
            />
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { View, ChatDotSquare, Reading } from '@element-plus/icons-vue'
import {
  getMyRecords,
  getMyRecordDetail,
  explainRecordQuestion,
  chatRecordQuestion
} from '@/api/studentExam'
import AiExplainChat from '@/components/AiExplainChat.vue'

const route = useRoute()

const loading = ref(false)
const tableData = ref([])

// 试卷回看
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref({})

onMounted(async () => {
  await fetchData()
  // 从「我的考试」点「查看」带 recordId 进来时，直接展开这份卷子，省去再找一遍
  const recordId = Number(route.query.recordId)
  if (recordId) {
    openDetail({ id: recordId })
  }
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getMyRecords()
    tableData.value = res.data || []
  } catch (err) {
    console.error('加载成绩失败:', err)
  } finally {
    loading.value = false
  }
}

// 得分率（0-100 整数）
function rate(row) {
  const total = Number(row.paperTotalScore)
  const score = Number(row.totalScore)
  if (!total) return 0
  return Math.round((score / total) * 100)
}

/**
 * 能否查看试卷：考试结束后才放行（后端按 exam.end_time 判定并硬校验）
 * 字段缺失时不拦截，交给后端给出准确提示，避免前端一片灰
 */
function canViewPaper(row) {
  return row.canViewPaper !== 0
}

/* ---------------- 试卷回看 ---------------- */

const TYPE_MAP = {
  1: { text: '单选题', tag: 'primary' },
  2: { text: '多选题', tag: 'primary' },
  3: { text: '判断题', tag: 'success' },
  4: { text: '填空题', tag: 'warning' },
  5: { text: '简答题', tag: 'danger' },
  6: { text: '主观题', tag: 'danger' }
}

function typeText(t) {
  return TYPE_MAP[t]?.text || '题目'
}

function typeTag(t) {
  return TYPE_MAP[t]?.tag || 'info'
}

/** 概览里的得分率 */
const heroRate = computed(() => {
  const total = Number(detail.value.paperTotalScore)
  const score = Number(detail.value.totalScore)
  if (!total) return 0
  return Math.round((score / total) * 100)
})

/** 待批阅提示：说清楚「总分还会变」，避免学生以为这是最终成绩 */
const pendingTip = computed(() => {
  const total = Number(detail.value.subjectiveCount) || 0
  const done = Number(detail.value.reviewedCount) || 0
  return `本卷含 ${total} 道主观题，教师已批阅 ${done} 道，还有 ${total - done} 道待批阅；`
    + '当前总分为客观题与已批阅部分的得分，全部批阅完成后自动更新'
})

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = {}
  try {
    const res = await getMyRecordDetail(row.id)
    detail.value = res.data || {}
  } catch (err) {
    // 失败提示由响应拦截器统一弹出
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/** 选项 JSON 数组（["选项一","选项二"]） */
function optionList(q) {
  if (!q.options) return []
  try {
    const arr = JSON.parse(q.options)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

function letter(idx) {
  return String.fromCharCode(65 + idx)
}

/** 我的选择（选择题）："A,C" → ['A','C']；判断题/填空题返回空数组 */
function pickedLetters(q) {
  if (q.questionType !== 1 && q.questionType !== 2) return []
  return lettersOf(q.userAnswer)
}

/** 正确答案字母（选择题） */
function answerLetters(q) {
  if (q.questionType !== 1 && q.questionType !== 2) return []
  return lettersOf(q.correctAnswer)
}

function lettersOf(raw) {
  if (!raw) return []
  return String(raw)
    .split(/[,，\s]+/)
    .map(s => s.trim().toUpperCase())
    .filter(Boolean)
}

/** 作答展示：判断题 T/F 转成中文，多选 A,C 换成顿号 */
function displayAnswer(val, q) {
  if (val === null || val === undefined || val === '') return ''
  const v = String(val)
  if (q.questionType === 3) {
    const up = v.toUpperCase()
    if (up === 'T') return '正确（T）'
    if (up === 'F') return '错误（F）'
  }
  if (q.questionType === 2) {
    return lettersOf(v).join('、')
  }
  return v
}

/** 主观题是否待批阅（客观题 reviewStatus 恒为 1） */
function isPending(q) {
  return q.isObjective === 0 && q.reviewStatus !== 1
}

function cardClass(q) {
  if (isPending(q)) return 'pending'
  return q.isCorrect === 0 ? 'wrong' : 'right'
}

function gainClass(q) {
  if (isPending(q)) return 'pending'
  return q.isCorrect === 0 ? 'wrong' : 'right'
}
</script>

<style scoped>
/* 表格：浅色格线 + 单元格内边距，表头/内容统一居中 */
.grade-table {
  --el-table-border-color: #e8edf4;
}
.grade-table :deep(th.el-table__cell),
.grade-table :deep(td.el-table__cell) {
  text-align: center;
  padding: 10px 6px;
}
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
.score {
  font-weight: bold;
  font-size: 15px;
}
.pass {
  color: #67c23a;
}
.fail {
  color: #f56c6c;
}

/* ========== 试卷回看抽屉 ========== */
.paper-detail {
  min-height: 200px;
  padding: 0 4px 20px;
}

/* 成绩概览 */
.score-hero {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 20px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0f7ff, #eefaf3);
  margin-bottom: 14px;
}
.hero-score {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-weight: 700;
}
.hero-score .num {
  font-size: 34px;
  line-height: 1;
}
.hero-score .total {
  font-size: 14px;
  font-weight: 400;
  color: #909399;
}
.hero-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.meta-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.meta-line.muted {
  font-size: 13px;
  color: #909399;
}
.tip-alert {
  margin-bottom: 14px;
}

/* 题目卡片 */
.q-card {
  border: 1px solid #e8edf4;
  border-left: 4px solid #dcdfe6;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
  background: #fff;
}
.q-card.right {
  border-left-color: #67c23a;
}
.q-card.wrong {
  border-left-color: #f56c6c;
}
.q-card.pending {
  border-left-color: #e6a23c;
}
.q-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.q-no {
  font-weight: 600;
  color: #303133;
}
.q-total {
  font-size: 13px;
  color: #909399;
}
.q-gain {
  margin-left: auto;
  font-size: 14px;
  font-weight: 600;
}
.q-gain.right {
  color: #67c23a;
}
.q-gain.wrong {
  color: #f56c6c;
}
.q-gain.pending {
  color: #e6a23c;
}
.q-content {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 选项 */
.q-options {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.opt-line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 14px;
  color: #606266;
}
.opt-line.mine {
  background: #ecf5ff;
}
.opt-line.right {
  background: #f0f9eb;
}
.opt-letter {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f4f4f5;
  color: #606266;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.opt-text {
  word-break: break-word;
}
.opt-mark {
  flex-shrink: 0;
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
}
.opt-mark.mine-mark {
  color: #409eff;
  background: #d9ecff;
}
.opt-mark.right-mark {
  color: #67c23a;
  background: #e1f3d8;
}

/* 作答行 */
.ans-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 10px;
  font-size: 14px;
}
.ans-label {
  flex-shrink: 0;
  width: 60px;
  font-size: 13px;
  color: #909399;
  line-height: 22px;
}
.ans-value {
  flex: 1;
  color: #303133;
  line-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}
.ans-value.empty,
.ans-value.locked {
  color: #c0c4cc;
}

/* 教师评语 */
.comment-box {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #fff9ec;
  border: 1px solid #fbe3b8;
  color: #a86b00;
  font-size: 13px;
  line-height: 1.6;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 解析 */
.analysis-box {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f7f9fb;
}
.analysis-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}
.analysis-text {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
