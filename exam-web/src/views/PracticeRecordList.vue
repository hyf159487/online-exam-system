<template>
  <div class="practice-record">
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
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="学生姓名/作业名称"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="list" stripe border v-loading="loading" :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="作业名称" prop="paperName" min-width="220" show-overflow-tooltip />
        <el-table-column label="学生姓名" prop="studentName" width="120" align="center" />
        <el-table-column label="班级" prop="className" width="140" align="center">
          <template #default="{ row }">{{ row.className || '—' }}</template>
        </el-table-column>
        <el-table-column label="得分" width="100" align="center">
          <template #default="{ row }">
            <span :class="getScoreClass(row)">{{ row.score }}</span>
            <span class="total">/{{ row.totalScore || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="正确率" width="110" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="calcRate(row)"
              :stroke-width="12"
              :color="rateColor(calcRate(row))"
            />
          </template>
        </el-table-column>
        <el-table-column label="对/错题数" width="110" align="center">
          <template #default="{ row }">
            <span class="correct">{{ row.correctCount || 0 }}</span>
            <span class="sep"> / </span>
            <span class="wrong">{{ row.wrongCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批改状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row.reviewStatus)" size="small">
              {{ reviewStatusText(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成状态" prop="finishStatus" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.finishStatus === 1 ? 'success' : 'warning'" size="small">
              {{ row.finishStatus === 1 ? '已完成' : '未完成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" prop="startTime" width="170" align="center" />
        <el-table-column label="完成时间" prop="finishTime" width="170" align="center">
          <template #default="{ row }">{{ row.finishTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDetail(row)">
              查看详情
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

    <!-- 答题详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="答题详情"
      width="720px"
      top="4vh"
      destroy-on-close
    >
      <div v-if="recordInfo" class="detail-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="作业">{{ recordInfo.paperName }}</el-descriptions-item>
          <el-descriptions-item label="学生">
            {{ recordInfo.studentName }}
            <span v-if="recordInfo.className">（{{ recordInfo.className }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="得分" label-class-name="score-label">
            <b class="score-val">{{ recordInfo.score }}</b>
            <span class="total">/{{ recordInfo.totalScore }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="答对">{{ recordInfo.correctCount || 0 }} 题</el-descriptions-item>
          <el-descriptions-item label="答错">{{ recordInfo.wrongCount || 0 }} 题</el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ recordInfo.finishTime || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-loading="detailLoading" class="detail-body" style="margin-top: 16px">
        <div v-for="(a, idx) in answerList" :key="a.id" class="answer-card" :class="cardClass(a)">
          <div class="answer-header">
            <span class="answer-index">第 {{ a.sortNo || (idx + 1) }} 题</span>
            <el-tag size="small" effect="plain">{{ questionTypeText(a.questionType) }}</el-tag>
            <span class="answer-score">
              <b :class="isSubjective(a) ? 'ok' : (a.isCorrect === 1 ? 'ok' : 'err')">
                {{ answerScoreText(a) }}
              </b>
              <span class="total">/{{ a.score }}</span>
            </span>
            <el-tag v-if="isSubjective(a)" size="small" type="warning" effect="plain">
              {{ a.reviewStatus === 1 ? '已批改' : '待批改' }}
            </el-tag>
            <el-tag v-else :type="a.isCorrect === 1 ? 'success' : 'danger'" size="small">
              {{ a.isCorrect === 1 ? '答对' : '答错' }}
            </el-tag>
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
              <span class="label">正确答案：</span>
              <span class="value correct">{{ a.correctAnswer || '—' }}</span>
            </div>
          </div>
          <div v-if="isSubjective(a) && a.reviewOpinion" class="review-opinion">
            <span class="label">教师评语：</span>{{ a.reviewOpinion }}
          </div>
          <div v-if="a.analysis" class="answer-analysis">
            <div class="analysis-label">解析：</div>
            <div class="analysis-text">{{ a.analysis }}</div>
          </div>
        </div>
        <el-empty v-if="!answerList.length && !detailLoading" description="暂无答题明细" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listPracticePaper,
  listPracticeRecord,
  getPracticeRecordDetail
} from '@/api/practicePaper'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const query = reactive({ paperId: null, keyword: '' })
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
function calcRate(row) {
  if (!row?.totalScore) return 0
  return Math.round(((row.score || 0) / row.totalScore) * 100)
}
function rateColor(rate) {
  if (rate >= 80) return '#67c23a'
  if (rate >= 60) return '#e6a23c'
  return '#f56c6c'
}
function getScoreClass(row) {
  const r = calcRate(row)
  if (r >= 80) return 'score-high'
  if (r >= 60) return 'score-mid'
  return 'score-low'
}

// ===== 批改状态（0无需批改 1待批改 2已批改）=====
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
  listPracticeRecord({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    paperId: query.paperId,
    keyword: query.keyword
  }).then(res => {
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  }).catch(err => ElMessage.error(err?.message || '加载失败'))
    .finally(() => loading.value = false)
}

function resetQuery() {
  query.paperId = null
  query.keyword = ''
  pageNum.value = 1
  loadList()
}

onMounted(() => {
  // 加载作业下拉（不分页，取最近 200 条）
  listPracticePaper({ pageNum: 1, pageSize: 200 }).then(res => {
    paperOptions.value = res.data?.records || res.data || []
  }).catch(() => {})
  loadList()
})

// ===== 详情 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const recordInfo = ref(null)
const answerList = ref([])

function showDetail(row) {
  recordInfo.value = row
  answerList.value = []
  detailVisible.value = true
  detailLoading.value = true
  getPracticeRecordDetail(row.id).then(res => {
    answerList.value = res.data || []
  }).catch(err => {
    ElMessage.error(err?.message || '获取答题详情失败')
  }).finally(() => {
    detailLoading.value = false
  })
}
</script>

<style scoped>
.practice-record { padding: 4px; }
.mb-4 { margin-bottom: 16px; }
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.score-high { color: #67c23a; font-weight: 600; font-size: 15px; }
.score-mid { color: #e6a23c; font-weight: 600; font-size: 15px; }
.score-low { color: #f56c6c; font-weight: 600; font-size: 15px; }
.total { color: #909399; font-size: 12px; font-weight: normal; }
.correct { color: #67c23a; font-weight: 600; }
.wrong { color: #f56c6c; font-weight: 600; }
.sep { color: #c0c4cc; }

/* 详情 */
.score-val { font-size: 18px; color: #409eff; }

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
/* 主观题：待批改（橙）/ 已批改（绿） */
.answer-card.subjective {
  background: linear-gradient(135deg, #fdf6ec, #ffffff);
  border-left: 4px solid #e6a23c;
}
.answer-card.reviewed {
  background: linear-gradient(135deg, #f0f9eb, #ffffff);
  border-left: 4px solid #67c23a;
}
.review-opinion {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f0f9eb;
  border: 1px solid #c2e7b0;
  border-radius: 6px;
  font-size: 13px;
  color: #529b2e;
}
.review-opinion .label { color: #909399; margin-right: 4px; }
.answer-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e4e7ed;
}
.answer-index {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}
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
.answer-option.opt-correct {
  background: #f0f9eb;
  color: #67c23a;
  border-color: #c2e7b0;
}
.answer-option.opt-wrong {
  background: #fef0f0;
  color: #f56c6c;
  border-color: #fbc4c4;
}
.answer-option.opt-both {
  background: #ecf5ff;
  color: #409eff;
  border-color: #a0cfff;
}

.answer-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  background: #fafafa;
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 10px;
}
.answer-compare .label {
  color: #909399;
  font-size: 12px;
  margin-right: 4px;
}
.answer-compare .value { font-weight: 600; }
.answer-compare .value.user { color: #606266; }
.answer-compare .value.correct { color: #67c23a; }

.answer-analysis {
  padding: 10px 12px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 6px;
  font-size: 13px;
}
.analysis-label {
  color: #d46b08;
  font-weight: 600;
  margin-bottom: 4px;
}
.analysis-text {
  color: #873800;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
