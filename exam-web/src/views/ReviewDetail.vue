<template>
  <div class="review-detail" v-loading="loading">
    <template v-if="detail">
      <!-- 顶部信息 -->
      <el-card shadow="never" class="top-bar">
        <div class="top-info">
          <div>
            <h2 class="paper-name">{{ detail.paperName }}</h2>
            <span class="sub">
              {{ detail.courseName || '-' }} · 学生：{{ detail.realName || '-' }}（{{ detail.username || '-' }}）
            </span>
          </div>
          <div class="top-right">
            <span class="time">交卷时间：{{ detail.submitTime || '-' }}</span>
            <el-tag :type="detail.status === 2 ? 'success' : 'warning'" effect="plain">
              {{ detail.status === 2 ? '已判分' : '待阅卷' }}
            </el-tag>
          </div>
        </div>
      </el-card>

      <!-- 得分概览 -->
      <el-card shadow="never" class="score-card">
        <div class="score-item">
          <span class="score-label">客观题得分</span>
          <span class="score-num">{{ detail.objectiveScore ?? 0 }}</span>
        </div>
        <div class="score-item">
          <span class="score-label">主观题（简答）人工得分</span>
          <span class="score-num manual">{{ manualScore }}</span>
        </div>
        <div class="score-item">
          <span class="score-label">当前总分</span>
          <span class="score-num total">{{ currentTotal }}</span>
          <span class="score-full">/ {{ detail.paperTotalScore }} 分</span>
        </div>
      </el-card>

      <!-- 题目列表 -->
      <el-card
        v-for="(q, i) in detail.questions"
        :key="q.questionId"
        shadow="never"
        class="question-card"
        :class="{ 'is-essay': q.questionType === 5 || q.questionType === 6 }"
      >
        <div class="q-head">
          <span class="q-index">{{ i + 1 }}.</span>
          <span class="q-type" :class="typeClass(q.questionType)">{{ typeText(q.questionType) }}</span>
          <span class="q-score">{{ q.score }} 分</span>
          <el-tag
            v-if="q.questionType <= 4 && q.getScore != null"
            :type="Number(q.getScore) > 0 ? 'success' : 'danger'"
            size="small"
            effect="light"
          >{{ Number(q.getScore) > 0 ? '答对' : '答错' }}</el-tag>
        </div>
        <p class="q-content">{{ q.content }}</p>

        <!-- 选择题选项 -->
        <div v-if="q.questionType === 1 || q.questionType === 2" class="option-list">
          <div v-for="(opt, idx) in parseOptions(q.options)" :key="idx" class="option-line">
            <span class="option-letter">{{ letter(idx) }}.</span>
            <span class="option-text">{{ opt }}</span>
          </div>
        </div>

        <!-- 学生作答 -->
        <div class="answer-block">
          <div class="answer-title">学生作答：</div>
          <div class="answer-body" :class="{ 'no-answer': !q.answered }">
            {{ q.answered ? q.userAnswer : '（未作答）' }}
          </div>
        </div>

        <!-- 标准答案 + 解析 -->
        <div class="std-block">
          <div class="std-title">标准答案：</div>
          <div class="std-body">{{ q.standardAnswer || '（无）' }}</div>
        </div>

        <!-- 简答题 / 名词解释：教师打分 -->
        <div v-if="q.questionType === 5 || q.questionType === 6" class="review-area">
          <el-form :inline="true">
            <el-form-item label="给分">
              <el-input-number
                v-model="reviewMap[q.questionId].score"
                :min="0"
                :max="Number(q.score) || 0"
                :precision="1"
                :step="0.5"
                style="width: 130px"
              />
              <span class="score-max">/ {{ q.score }} 分</span>
              <el-button
                class="ai-btn"
                type="primary"
                plain
                size="small"
                :icon="MagicStick"
                :loading="aiLoadingId === q.questionId"
                :disabled="detail.status === 2"
                @click="aiScoreQuestion(q)"
              >
                AI 预评分
              </el-button>
            </el-form-item>
          </el-form>
          <div class="comment-row">
            <span class="comment-label">评语：</span>
            <el-input
              v-model="reviewMap[q.questionId].comment"
              placeholder="可填写扣分原因、答题点评（选填）"
              maxlength="200"
              clearable
              style="max-width: 640px"
            />
          </div>

          <!-- AI 预评分依据：教师可对照命中/缺失要点复核给分 -->
          <div v-if="aiMap[q.questionId]" class="ai-panel">
            <div class="ai-panel-head">
              <span class="ai-title">AI 评分依据</span>
              <el-tag
                size="small"
                effect="plain"
                :type="aiMap[q.questionId].source === 'llm' ? 'success' : 'info'"
              >
                {{ aiMap[q.questionId].source === 'llm' ? '大模型' : '本地规则引擎' }}
              </el-tag>
              <el-tag size="small" effect="plain" type="warning">
                置信度 {{ Math.round((Number(aiMap[q.questionId].confidence) || 0) * 100) }}%
              </el-tag>
              <span class="ai-score">
                建议 {{ aiMap[q.questionId].score }}/{{ aiMap[q.questionId].maxScore }} 分
              </span>
              <el-button link size="small" @click="clearAi(q.questionId)">收起</el-button>
            </div>
            <div class="ai-reason">{{ aiMap[q.questionId].reason || '（无评分依据）' }}</div>
            <div v-if="aiMap[q.questionId].hits?.length" class="ai-points">
              <span class="ai-points-label hit">命中</span>
              <el-tag
                v-for="(h, hi) in aiMap[q.questionId].hits"
                :key="`ai-hit-${hi}`"
                size="small"
                type="success"
                effect="plain"
              >
                {{ h }}
              </el-tag>
            </div>
            <div v-if="aiMap[q.questionId].missing?.length" class="ai-points">
              <span class="ai-points-label miss">缺失</span>
              <el-tag
                v-for="(m, mi) in aiMap[q.questionId].missing"
                :key="`ai-miss-${mi}`"
                size="small"
                type="danger"
                effect="plain"
              >
                {{ m }}
              </el-tag>
            </div>
            <div v-if="aiMap[q.questionId].warning" class="ai-warning">
              {{ aiMap[q.questionId].warning }}
            </div>
          </div>
        </div>
      </el-card>

      <!-- 底部操作 -->
      <div class="footer-bar">
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" :loading="submitting" :disabled="detail.status === 2" @click="handleSubmit">
          {{ detail.status === 2 ? '已判分' : '提交判分' }}
        </el-button>
      </div>
    </template>

    <el-empty v-else-if="!loading" :description="loadError || '未获取到阅卷信息'" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { getReviewDetail, submitReview } from '@/api/grade'
import { aiScoreAnswer } from '@/api/examReview'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const detail = ref(null)
const reviewMap = reactive({})   // { questionId: { score, comment } }
const submitting = ref(false)

// AI 预评分：aiLoadingId 为正在预评的 questionId；aiMap 缓存评分依据供教师复核
const aiLoadingId = ref(null)
const aiMap = reactive({})

const recordId = route.params.recordId

onMounted(() => {
  loadDetail()
})

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getReviewDetail(recordId)
    detail.value = res.data
    // 初始化打分表（沿用已保存的给分/评语）
    ;(detail.value.questions || []).forEach((q) => {
      if (q.questionType === 5 || q.questionType === 6) {
        reviewMap[q.questionId] = {
          score: q.getScore != null ? Number(q.getScore) : 0,
          comment: q.teacherComment || ''
        }
      }
    })
  } catch (err) {
    loadError.value = err?.message || '加载失败'
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

/** 简答题人工得分合计 */
const manualScore = computed(() => {
  let sum = 0
  Object.values(reviewMap).forEach((v) => {
    sum += Number(v.score || 0)
  })
  return sum
})

/** 当前总分 = 客观分 + 简答人工分（实时预览） */
const currentTotal = computed(() => {
  const obj = Number(detail.value?.objectiveScore || 0)
  return obj + manualScore.value
})

function parseOptions(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

function letter(idx) {
  return String.fromCharCode(65 + idx)
}

function typeText(t) {
  return { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }[t] || '未知'
}
function typeClass(t) {
  return { 1: 't-single', 2: 't-multiple', 3: 't-judge', 4: 't-fill', 5: 't-essay' }[t] || ''
}

/** AI 预评分：只填建议得分与评语，教师可调整后再提交判分 */
async function aiScoreQuestion(q) {
  if (aiLoadingId.value) return
  if (!q.answerId) {
    ElMessage.warning('该题缺少答题记录，无法预评分')
    return
  }
  aiLoadingId.value = q.questionId
  try {
    const res = await aiScoreAnswer(q.answerId)
    const ai = res.data || {}
    const entry = reviewMap[q.questionId]
    if (entry) {
      if (ai.score !== null && ai.score !== undefined && !Number.isNaN(Number(ai.score))) {
        entry.score = Number(ai.score)
      }
      if (ai.comment) entry.comment = ai.comment
    }
    aiMap[q.questionId] = ai
    ElMessage.success(`AI 建议 ${ai.score}/${ai.maxScore} 分，可调整后提交`)
  } catch (e) {
    // 失败提示由响应拦截器统一弹出
  } finally {
    aiLoadingId.value = null
  }
}

function clearAi(questionId) {
  delete aiMap[questionId]
}

async function handleSubmit() {
  if (submitting.value) return
  const essays = (detail.value.questions || []).filter((q) => q.questionType === 5 || q.questionType === 6)
  const hasEssay = essays.length > 0
  try {
    await ElMessageBox.confirm(
      hasEssay
        ? '确认提交判分？提交后学生成绩将按当前给分更新，且不可再修改。'
        : '本卷无简答题，确认后按客观题得分完成判分。',
      '判分确认',
      { confirmButtonText: '确认提交', cancelButtonText: '再检查', type: 'warning' }
    )
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    const items = essays.map((q) => ({
      questionId: q.questionId,
      score: Number(reviewMap[q.questionId].score || 0),
      comment: reviewMap[q.questionId].comment || ''
    }))
    await submitReview(recordId, { items })
    ElMessage.success('判分成功')
    router.back()
  } catch (err) {
    ElMessage.error(err?.message || '判分失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.top-bar {
  margin-bottom: 16px;
}
.top-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.paper-name {
  margin: 0 0 4px;
  font-size: 18px;
  color: #303133;
}
.sub {
  font-size: 13px;
  color: #909399;
}
.top-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.time {
  font-size: 13px;
  color: #909399;
  font-family: Consolas, monospace;
}

/* 得分概览 */
.score-card {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 48px;
}
.score-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.score-label {
  font-size: 13px;
  color: #909399;
}
.score-num {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
}
.score-num.manual {
  color: #a855f7;
}
.score-num.total {
  color: #67c23a;
}
.score-full {
  font-size: 14px;
  color: #909399;
}

/* 题目 */
.question-card {
  margin-bottom: 14px;
}
.question-card.is-essay {
  border: 1px solid #e8d5f7;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.q-index {
  font-weight: bold;
  font-size: 16px;
  color: #303133;
}
.q-type {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #ecf5ff;
  color: #409eff;
}
.q-type.t-multiple { background: #f0f9eb; color: #67c23a; }
.q-type.t-judge { background: #fdf6ec; color: #e6a23c; }
.q-type.t-fill { background: #f4f4f5; color: #909399; }
.q-type.t-essay { background: #f9ecff; color: #a855f7; }
.q-score {
  font-size: 13px;
  color: #909399;
}
.q-content {
  margin: 4px 0 12px;
  line-height: 1.7;
  color: #303133;
}

.option-list {
  margin-bottom: 12px;
}
.option-line {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 14px;
  color: #303133;
}
.option-letter {
  font-weight: 600;
  color: #909399;
}

/* 学生作答 / 标准答案 */
.answer-block,
.std-block {
  margin-top: 10px;
  padding: 10px 14px;
  border-radius: 6px;
}
.answer-block {
  background: #f4f8ff;
}
.std-block {
  background: #f7f9fb;
}
.answer-title,
.std-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}
.answer-body,
.std-body {
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
}
.answer-body.no-answer {
  color: #c0c4cc;
}

/* 简答题打分区 */
.review-area {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
}
.score-max {
  margin-left: 8px;
  font-size: 13px;
  color: #909399;
}
.comment-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.comment-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.footer-bar {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin: 20px 0;
}

/* AI 预评分 */
.ai-btn {
  margin-left: 12px;
}
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
</style>
