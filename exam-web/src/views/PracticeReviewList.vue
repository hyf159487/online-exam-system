<template>
  <div class="practice-review">
    <!-- 筛选区 -->
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="query">
        <el-form-item label="作业">
          <el-select
            v-model="query.paperId"
            placeholder="全部作业"
            clearable filterable
            style="width: 260px"
          >
            <el-option
              v-for="p in paperOptions"
              :key="p.id"
              :label="p.paperName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="批改状态">
          <el-select
            v-model="query.reviewStatus"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option label="待批改" :value="1" />
            <el-option label="已批改" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="学生姓名/作业名称"
            clearable
            style="width: 220px"
            @keyup.enter="loadList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="需要人工批改的两类作业：①「简答题/名词解释」主观题（客观题自动判分，主观题记 0 分待批改）；②文件型作业（学生下载作业文件、线下作答后上传提交）。批改完成后本次作业得分会自动重算。"
      />
    </el-card>

    <!-- 批改列表 -->
    <el-card shadow="never">
      <el-table
        :data="list"
        stripe border
        v-loading="loading"
        :header-cell-style="{ textAlign: 'center' }"
        :cell-style="{ textAlign: 'center' }"
      >
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="作业名称" prop="paperName" min-width="200" show-overflow-tooltip />
        <el-table-column label="学生姓名" prop="studentName" width="110" align="center" />
        <!-- 文件型作业：学生提交的作业文件（点击文件名预览，点「下载」按原始文件名保存到本地） -->
        <el-table-column label="提交文件" width="180" align="center">
          <template #default="{ row }">
            <template v-if="row.attachmentPath">
              <el-link
                type="primary"
                :href="row.attachmentPath"
                target="_blank"
                class="attach-name"
                :title="row.attachmentName || '查看'"
              >
                {{ row.attachmentName || '查看' }}
              </el-link>
              <el-button
                type="primary"
                link
                size="small"
                :loading="downloadingId === row.id"
                @click="downloadAttachment(row)"
              >
                下载
              </el-button>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="班级" width="130" align="center">
          <template #default="{ row }">{{ row.className || '—' }}</template>
        </el-table-column>
        <el-table-column label="主观题" width="90" align="center">
          <template #default="{ row }">{{ row.subjectiveCount || 0 }} 题</template>
        </el-table-column>
        <el-table-column label="待批改" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.pendingReviewCount" type="warning" size="small">
              {{ row.pendingReviewCount }} 题
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="当前得分" width="110" align="center">
          <template #default="{ row }">
            <span class="score">{{ row.score }}</span>
            <span class="total">/{{ row.totalScore || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批改状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row.reviewStatus)" size="small">
              {{ reviewStatusText(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170" align="center">
          <template #default="{ row }">{{ row.finishTime || row.createTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openReview(row)">
              {{ row.reviewStatus === 2 ? '查看批改' : '批改' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 批改弹窗 -->
    <el-dialog
      v-model="reviewVisible"
      title="批改作业"
      width="900px"
      top="4vh"
      destroy-on-close
    >
      <div v-if="currentRecord" class="review-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="作业">{{ currentRecord.paperName }}</el-descriptions-item>
          <el-descriptions-item label="学生">
            {{ currentRecord.studentName }}
            <span v-if="currentRecord.className">（{{ currentRecord.className }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="当前得分">
            <b class="score-val">{{ currentRecord.score }}</b>
            <span class="total">/{{ currentRecord.totalScore }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-loading="detailLoading" class="review-body">
        <!-- 文件型作业提交：没有逐题明细，直接查看学生上传的作业文件并给分 -->
        <div v-if="isAttachmentSubmit" class="attach-card">
          <div class="attach-head">
            <el-tag type="warning" effect="plain">附件作业</el-tag>
            <span class="attach-title">学生提交的作业文件</span>
          </div>
          <div class="attach-row">
            <span class="label">提交文件：</span>
            <el-link type="primary" :href="currentRecord.attachmentPath" target="_blank">
              {{ currentRecord.attachmentName || '查看' }}
            </el-link>
            <el-button
              type="primary"
              link
              size="small"
              :icon="Download"
              :loading="downloadingId === currentRecord.id"
              @click="downloadAttachment(currentRecord)"
            >
              下载
            </el-button>
            <span class="hint">提交时间：{{ currentRecord.finishTime || '—' }}</span>
          </div>
          <div v-if="currentRecord.submitRemark" class="attach-row">
            <span class="label">学生备注：</span>{{ currentRecord.submitRemark }}
          </div>

          <div v-if="canReview" class="review-form attach-review">
            <div class="review-form-row">
              <span class="label">得分：</span>
              <el-input-number
                v-model="attachScore"
                :min="0"
                :max="attachmentMaxScore"
                :precision="0"
                size="small"
                controls-position="right"
                style="width: 130px"
              />
              <span class="hint">满分 {{ attachmentMaxScore }} 分</span>
              <el-button link type="primary" size="small" @click="attachScore = attachmentMaxScore">
                给满分
              </el-button>
              <el-button link size="small" @click="attachScore = 0">给 0 分</el-button>
            </div>
            <div class="review-form-row">
              <span class="label">评语：</span>
              <el-input
                v-model="attachOpinion"
                placeholder="选填，例如：整体完成度不错，第 3 题步骤可再细化"
                maxlength="255"
                show-word-limit
                size="small"
              />
            </div>
          </div>
          <div v-else class="attach-result">
            <span class="label">教师评分：</span>
            <b class="ok">{{ currentRecord.score }} 分</b>
            <span v-if="currentRecord.reviewOpinion" class="opinion">
              评语：{{ currentRecord.reviewOpinion }}
            </span>
          </div>
        </div>

        <div
          v-for="(a, idx) in answerList"
          :key="a.id"
          class="answer-card"
          :class="cardClass(a)"
        >
          <div class="answer-header">
            <span class="answer-index">第 {{ a.sortNo || (idx + 1) }} 题</span>
            <el-tag size="small" effect="plain">{{ questionTypeText(a.questionType) }}</el-tag>
            <el-tag v-if="isSubjective(a)" size="small" type="warning" effect="plain">
              {{ a.reviewStatus === 1 ? '已批改' : '待批改' }}
            </el-tag>
            <el-tag v-else :type="a.isCorrect === 1 ? 'success' : 'danger'" size="small">
              {{ a.isCorrect === 1 ? '答对' : '答错' }}
            </el-tag>
            <span class="answer-score">
              <b :class="isSubjective(a) ? 'ok' : (a.isCorrect === 1 ? 'ok' : 'err')">
                {{ answerScoreText(a) }}
              </b>
              <span class="total">/{{ a.score }}</span>
            </span>
          </div>

          <div class="answer-q-content">{{ a.questionContent }}</div>

          <div v-if="a.options" class="answer-options">
            <div
              v-for="(opt, oi) in splitOptions(a.options)"
              :key="oi"
              class="answer-option"
              :class="getOptionClass(a, oi)"
            >
              <span class="opt-letter">{{ letter(oi) }}</span>. {{ opt }}
            </div>
          </div>

          <div class="answer-compare">
            <div class="col">
              <span class="label">学生作答：</span>
              <span class="value user">{{ a.userAnswer || '未作答' }}</span>
            </div>
            <div class="col">
              <span class="label">{{ isSubjective(a) ? '参考答案：' : '正确答案：' }}</span>
              <span class="value correct">{{ a.correctAnswer || '—' }}</span>
            </div>
          </div>

          <!-- 主观题：教师给分 + 评语 -->
          <div v-if="isSubjective(a) && canReview" class="review-form">
            <div class="review-form-row">
              <span class="label">本题得分：</span>
              <el-input-number
                v-model="a.reviewScore"
                :min="0"
                :max="a.score || 0"
                :precision="0"
                size="small"
                controls-position="right"
                style="width: 130px"
              />
              <span class="hint">满分 {{ a.score }} 分</span>
              <el-button link type="primary" size="small" @click="a.reviewScore = a.score || 0">
                给满分
              </el-button>
              <el-button link size="small" @click="a.reviewScore = 0">给 0 分</el-button>
            </div>
            <div class="review-form-row">
              <span class="label">评语：</span>
              <el-input
                v-model="a.reviewOpinion"
                placeholder="选填，例如：要点完整，可再补充公式推导"
                maxlength="255"
                show-word-limit
                size="small"
              />
            </div>
          </div>

          <div v-else-if="isSubjective(a) && a.reviewOpinion" class="review-opinion">
            <span class="label">教师评语：</span>{{ a.reviewOpinion }}
          </div>

          <div v-if="a.analysis" class="answer-analysis">
            <div class="analysis-label">解析：</div>
            <div class="analysis-text">{{ a.analysis }}</div>
          </div>
        </div>
        <el-empty
          v-if="!answerList.length && !detailLoading && !isAttachmentSubmit"
          description="暂无答题明细"
        />
      </div>

      <template #footer>
        <span v-if="canReview && pendingCount" class="pending-tip">
          还有 {{ pendingCount }} 道主观题未给分，未批改的题按 0 分计入总分
        </span>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          v-if="canReview"
          type="primary"
          :loading="saving"
          :disabled="!subjectiveList.length && !isAttachmentSubmit"
          @click="saveReview"
        >
          保存批改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import {
  listPracticePaper,
  listPracticeReview,
  getPracticeRecordDetail,
  submitPracticeReview,
  submitAttachmentReview,
  downloadRecordAttachment
} from '@/api/practicePaper'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const query = reactive({ paperId: null, keyword: '', reviewStatus: 1 })
const paperOptions = ref([])

function questionTypeText(t) {
  const m = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
  return m[t] || '未知'
}
function letter(i) { return 'ABCDEFGHIJ'[i] || '' }
function splitOptions(str) {
  if (!str) return []
  try {
    const arr = JSON.parse(str)
    return Array.isArray(arr) ? arr : []
  } catch {
    return String(str).split(/[,;]/).filter(x => x)
  }
}
function reviewStatusText(s) {
  if (s === 1) return '待批改'
  if (s === 2) return '已批改'
  return '无需批改'
}
function reviewTagType(s) {
  if (s === 1) return 'warning'
  if (s === 2) return 'success'
  return 'info'
}

// 答题选项样式
function getOptionClass(ans, oi) {
  const letterI = letter(oi)
  const user = (ans.userAnswer || '').toUpperCase()
  const correct = (ans.correctAnswer || '').toUpperCase()
  const isCorrect = correct.includes(letterI)
  const isUser = user.includes(letterI)
  if (isCorrect && isUser) return 'opt-both'
  if (isCorrect) return 'opt-correct'
  if (isUser && !isCorrect) return 'opt-wrong'
  return ''
}

// ===== 列表加载 =====
function loadList() {
  loading.value = true
  listPracticeReview({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    paperId: query.paperId,
    keyword: query.keyword,
    reviewStatus: query.reviewStatus
  }).then(res => {
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  }).catch(err => ElMessage.error(err?.message || '加载失败'))
    .finally(() => loading.value = false)
}

function resetQuery() {
  query.paperId = null
  query.keyword = ''
  query.reviewStatus = 1
  pageNum.value = 1
  loadList()
}

onMounted(() => {
  // 作业下拉（不分页，取最近 200 条）
  listPracticePaper({ pageNum: 1, pageSize: 200 }).then(res => {
    paperOptions.value = res.data?.records || res.data || []
  }).catch(() => {})
  loadList()
})

// ===== 批改 =====
const reviewVisible = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const currentRecord = ref(null)
const answerList = ref([])

const subjectiveList = computed(() => answerList.value.filter(a => isSubjective(a)))
const pendingCount = computed(() => subjectiveList.value.filter(a => a.reviewStatus !== 1).length)
// 已批改的记录只允许查看，待批改/无需批改的记录可以继续批改
const canReview = computed(() => currentRecord.value?.reviewStatus !== 2)

/** 是否为文件型作业提交（学生上传了作业文件，没有逐题明细，直接给总分） */
const isAttachmentSubmit = computed(() => !!currentRecord.value?.attachmentPath)
/** 附件提交满分上限：作业设了总分用总分，否则按 100 分制（与后端保持一致） */
const attachmentMaxScore = computed(() => {
  const t = currentRecord.value?.totalScore
  return (t != null && t > 0) ? t : 100
})
const attachScore = ref(0)
const attachOpinion = ref('')

function isSubjective(a) {
  return a?.isObjective === 0
}
function answerScoreText(a) {
  if (isSubjective(a) && a.reviewStatus !== 1) return '待批改'
  return a.getScore ?? 0
}
function cardClass(a) {
  if (isSubjective(a)) return a.reviewStatus === 1 ? 'reviewed' : 'subjective'
  return a.isCorrect === 1 ? 'correct' : 'wrong'
}

// ===== 下载学生提交的作业文件 =====
const downloadingId = ref(null)

/**
 * 下载学生提交的作业文件：后端以学生上传时的原始文件名返回（blob），
 * 这里用 a[download] 触发浏览器下载，避免新窗口打开时文件名变成 UUID。
 */
function downloadAttachment(row) {
  if (!row?.attachmentPath) return
  downloadingId.value = row.id
  downloadRecordAttachment(row.id).then(res => {
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.attachmentName || '作业文件'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }).catch(() => {
    // 失败提示已由 request 拦截器统一弹出（如 403 无权下载 / 文件不存在）
  }).finally(() => {
    downloadingId.value = null
  })
}

function openReview(row) {
  currentRecord.value = row
  answerList.value = []
  // 文件型提交：回显上次给分与评语，便于二次修改
  attachScore.value = row.score || 0
  attachOpinion.value = row.reviewOpinion || ''
  reviewVisible.value = true
  detailLoading.value = true
  getPracticeRecordDetail(row.id).then(res => {
    answerList.value = (res.data || []).map(a => ({
      ...a,
      // 已批改的沿用原得分，未批改的默认 0 分，教师自行调整
      reviewScore: a.reviewScore != null ? a.reviewScore : 0,
      reviewOpinion: a.reviewOpinion || ''
    }))
  }).catch(err => {
    ElMessage.error(err?.message || '获取答题详情失败')
  }).finally(() => {
    detailLoading.value = false
  })
}

function saveReview() {
  // 文件型作业提交：没有逐题明细，直接给总分 + 评语
  if (isAttachmentSubmit.value) {
    saveAttachmentReview()
    return
  }
  if (!subjectiveList.value.length) {
    ElMessage.warning('该作业没有需要批改的主观题')
    return
  }
  const items = subjectiveList.value.map(a => ({
    answerId: a.id,
    reviewScore: a.reviewScore ?? 0,
    reviewOpinion: a.reviewOpinion || ''
  }))
  saving.value = true
  submitPracticeReview(currentRecord.value.id, items).then(() => {
    ElMessage.success('批改成功，本次作业得分已重算')
    reviewVisible.value = false
    loadList()
  }).catch(err => {
    ElMessage.error(err?.message || '批改失败')
  }).finally(() => {
    saving.value = false
  })
}

/** 文件型作业批改：给总分 + 评语，成功后该记录状态变「已批改」 */
function saveAttachmentReview() {
  saving.value = true
  submitAttachmentReview(currentRecord.value.id, {
    score: attachScore.value ?? 0,
    opinion: attachOpinion.value || ''
  }).then(() => {
    ElMessage.success('批改成功')
    reviewVisible.value = false
    loadList()
  }).catch(err => {
    ElMessage.error(err?.message || '批改失败')
  }).finally(() => {
    saving.value = false
  })
}
</script>

<style scoped>
.practice-review { padding: 4px; }
.mb-4 { margin-bottom: 16px; }
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.score { color: #303133; font-weight: 600; font-size: 15px; }
.total { color: #909399; font-size: 12px; font-weight: normal; }

/* 批改弹窗 */
.score-val { font-size: 18px; color: #409eff; }
.review-body { margin-top: 16px; max-height: 62vh; overflow-y: auto; padding-right: 4px; }

.answer-card {
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 14px;
  border: 1px solid #ebeef5;
  background: #fff;
}
.answer-card.correct {
  background: linear-gradient(135deg, #f0f9eb, #ffffff);
  border-left: 4px solid #67c23a;
}
.answer-card.wrong {
  background: linear-gradient(135deg, #fef0f0, #ffffff);
  border-left: 4px solid #f56c6c;
}
.answer-card.subjective {
  background: linear-gradient(135deg, #fdf6ec, #ffffff);
  border-left: 4px solid #e6a23c;
}
.answer-card.reviewed {
  background: linear-gradient(135deg, #f0f9eb, #ffffff);
  border-left: 4px solid #67c23a;
}
.answer-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e4e7ed;
}
.answer-index { font-weight: 600; color: #303133; font-size: 14px; }
.answer-score { margin-left: auto; font-size: 13px; }
.answer-score .ok { color: #67c23a; font-size: 15px; }
.answer-score .err { color: #f56c6c; font-size: 15px; }

.answer-q-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  margin-bottom: 12px;
}
.answer-options {
  padding: 8px 0;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.answer-option {
  padding: 6px 12px;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
  font-size: 13px;
  border: 1px solid transparent;
}
.answer-option .opt-letter { color: #909399; margin-right: 4px; }
.answer-option.opt-correct { background: #f0f9eb; color: #67c23a; border-color: #c2e7b0; }
.answer-option.opt-wrong { background: #fef0f0; color: #f56c6c; border-color: #fbc4c4; }
.answer-option.opt-both { background: #ecf5ff; color: #409eff; border-color: #a0cfff; }

.answer-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  background: #fafafa;
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 10px;
}
.answer-compare .label { color: #909399; font-size: 12px; margin-right: 4px; }
.answer-compare .value { font-weight: 600; white-space: pre-wrap; }
.answer-compare .value.user { color: #606266; }
.answer-compare .value.correct { color: #67c23a; }

.review-form {
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.review-form-row { display: flex; align-items: center; gap: 8px; }
.review-form-row .label { color: #b88230; font-size: 13px; white-space: nowrap; }
.review-form-row .hint { color: #909399; font-size: 12px; }
.review-opinion {
  background: #f0f9eb;
  border: 1px solid #c2e7b0;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #529b2e;
}
.review-opinion .label { color: #909399; margin-right: 4px; }

.answer-analysis {
  margin-top: 10px;
  padding: 10px 12px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 6px;
  font-size: 13px;
}
.analysis-label { color: #d46b08; font-weight: 600; margin-bottom: 4px; }
.analysis-text { color: #873800; line-height: 1.7; white-space: pre-wrap; }

.pending-tip { float: left; color: #e6a23c; font-size: 12px; line-height: 32px; }

/* 文件型作业提交卡片 */
.attach-card {
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 14px;
  border: 1px solid #f5dab1;
  background: linear-gradient(135deg, #fdf6ec, #ffffff);
  border-left: 4px solid #e6a23c;
}
.attach-head {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 12px; padding-bottom: 10px;
  border-bottom: 1px dashed #e4e7ed;
}
.attach-title { font-weight: 600; color: #303133; font-size: 14px; }
.attach-row { margin-bottom: 8px; font-size: 14px; line-height: 1.7; color: #303133; }
.attach-row .label { color: #909399; margin-right: 4px; }
.attach-row .hint { margin-left: 12px; color: #909399; font-size: 12px; }
.attach-name {
  display: inline-block;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  margin-right: 6px;
}
.attach-review { margin-top: 12px; }
.attach-result {
  margin-top: 12px; padding: 8px 12px;
  background: #f0f9eb; border: 1px solid #c2e7b0;
  border-radius: 6px; font-size: 13px; color: #529b2e;
}
.attach-result .label { color: #909399; margin-right: 4px; }
.attach-result .ok { color: #67c23a; font-size: 15px; margin-right: 10px; }
.attach-result .opinion { color: #606266; }
</style>
