<template>
  <div class="compose" v-loading="loadingDetail">
    <!-- 顶部：试卷信息 + 操作 -->
    <el-card shadow="never" class="head-card">
<div class="head">
          <div class="head-left">
            <el-button @click="router.back()">← 返回</el-button>
            <span class="paper-name">{{ paperInfo.paperName || '...' }}</span>
            <el-tag size="small" :type="paperTypeTagType(paperInfo.paperType)">
              组卷方式：{{ paperTypeLabel(paperInfo.paperType) }}
            </el-tag>
            <el-tag size="small" type="info">{{ paperInfo.duration || 0 }} 分钟</el-tag>
            <el-tooltip content="及格线统一为满分 × 60%，随组卷总分自动调整" placement="top">
              <el-tag size="small" type="warning">及格 {{ passLine }} 分</el-tag>
            </el-tooltip>
            <el-tag size="small" type="success">学科：{{ paperInfo.courseName || '-' }}</el-tag>
          </div>
        <div class="head-right">
          <span class="total">
            共 <b>{{ selected.length }}</b> 题 · 总分 <b class="score">{{ totalScore }}</b> 分
          </span>
          <el-button type="warning" :icon="MagicStick" @click="openRandomDialog">随机组卷</el-button>
          <el-button type="primary" plain :icon="Cpu" @click="openAiDialog">AI 智能组卷</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存组卷</el-button>
        </div>
      </div>
    </el-card>

    <!-- AI 智能组卷：需求配置弹窗 -->
    <el-dialog v-model="aiVisible" title="AI 智能组卷" width="700px" :close-on-click-modal="false">
      <div class="ai-tip">
        <el-icon class="ai-tip-icon"><Cpu /></el-icon>
        <div>
          只需说明考试要求，系统会自动生成<b>组卷方案</b>（题型配比 / 难度分布 / 分值分配）并从题库抽题，
          <b>会覆盖当前已选题目</b>。生成时按下列条件<b>自上而下逐级优先</b>：
          <b>目标总分 → 难度倾向 → 重点知识点 → 补充要求</b>，抽题时优先命中重点知识点并尽量覆盖更多知识点。
        </div>
      </div>
      <div class="ai-src-row">
        <el-tag :type="aiMeta.aiEnabled ? 'success' : 'warning'" size="small" effect="plain">
          {{ aiMeta.aiEnabled ? `方案来源：AI 大模型（${aiMeta.model}）` : '方案来源：本地规则引擎（未配置大模型 Key）' }}
        </el-tag>
        <span class="ai-src-hint">本科目可用题目 {{ aiMeta.totalQuestions }} 题</span>
      </div>

      <el-form label-width="100px" class="ai-form">
        <el-form-item label="目标总分">
          <el-input-number v-model="aiForm.totalScore" :min="10" :max="300" :step="10"
                           controls-position="right" style="width: 140px" />
          <span class="ai-unit">分</span>
        </el-form-item>
        <el-form-item label="难度倾向">
          <el-radio-group v-model="aiForm.difficultyPreference">
            <el-radio-button :value="1">偏基础</el-radio-button>
            <el-radio-button :value="2">均衡</el-radio-button>
            <el-radio-button :value="3">偏挑战</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="重点知识点">
          <el-select v-model="aiForm.knowledgeIds" multiple filterable clearable collapse-tags collapse-tags-tooltip
                     placeholder="不选 = AI 按知识点覆盖率自动均衡" style="width: 100%">
            <el-option v-for="k in aiMeta.knowledgeOptions" :key="k.knowledgeId"
                       :label="k.knowledgeName" :value="k.knowledgeId">
              <span>{{ k.knowledgeName }}</span>
              <span class="ai-opt-count">{{ k.questionCount }} 题</span>
            </el-option>
            <template #empty>
              <div class="ai-opt-empty">该学科暂无知识点题量统计</div>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="补充要求">
          <el-input v-model="aiForm.requirement" type="textarea" :rows="3" maxlength="1000" show-word-limit
                    placeholder="例：侧重第三章与多线程，计算与综合分析类题目多一点，避免反复考察同一知识点" />
        </el-form-item>
        <el-form-item label="题库概览" v-if="aiMeta.typeStats && aiMeta.typeStats.length">
          <div class="ai-bank">
            <el-tag v-for="t in aiMeta.typeStats" :key="t.questionType" size="small" effect="plain"
                    class="ai-bank-tag">
              {{ t.questionTypeText }} {{ t.total }} 题（易{{ t.easy }}/中{{ t.medium }}/难{{ t.hard }}）
            </el-tag>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="aiVisible = false">取消</el-button>
        <el-button type="primary" :icon="Cpu" :loading="aiComposing" @click="handleAiCompose(false)">
          生成方案并组卷
        </el-button>
      </template>
    </el-dialog>

    <!-- AI 智能组卷：方案报告弹窗 -->
    <el-dialog v-model="aiResultVisible" title="AI 组卷方案" width="880px" top="6vh">
      <el-alert type="success" :closable="false" show-icon class="ai-strategy">
        <template #title>
          组卷思路（{{ aiResult.source === 'llm' ? `AI 大模型 ${aiResult.model || ''}` : '本地规则引擎' }}）
        </template>
        <div class="ai-strategy-text">{{ aiResult.strategy }}</div>
      </el-alert>

      <div class="ai-report-head">
        <el-tag type="success">共 {{ aiResult.questionCount }} 题</el-tag>
        <el-tag :type="aiScoreHit ? 'success' : 'warning'">
          实际总分 {{ aiResult.totalScore }} 分（目标 {{ aiResult.targetScore }} 分{{ aiScoreHit ? '，已命中' : '，偏差较大' }}）
        </el-tag>
        <el-tag v-for="d in aiResult.difficultyStats" :key="d.difficulty" type="info" effect="plain">
          {{ d.difficultyText }} {{ d.count }} 题 / {{ d.score }} 分
        </el-tag>
      </div>

      <el-table :data="aiResult.rules" size="small" border class="ai-rule-table">
        <el-table-column label="序号" width="70" align="center" header-align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="题型" prop="questionTypeText" width="110" align="center" header-align="center" />
        <el-table-column label="难度" prop="difficultyText" width="90" align="center" header-align="center" />
        <el-table-column label="题数（实际/计划）" width="160" align="center" header-align="center">
          <template #default="{ row }">{{ row.actualCount }} / {{ row.count }}</template>
        </el-table-column>
        <el-table-column label="每题分值" width="110" align="center" header-align="center">
          <template #default="{ row }">{{ row.score }} 分</template>
        </el-table-column>
        <el-table-column label="小计" align="center" header-align="center">
          <template #default="{ row }">{{ (Number(row.score) * row.actualCount).toFixed(1) }} 分</template>
        </el-table-column>
      </el-table>

      <div class="ai-report-sub">抽到的题目（{{ aiResult.items?.length || 0 }} 题）</div>
      <el-table :data="aiResult.items" size="small" max-height="260" class="ai-item-table">
        <el-table-column label="序号" width="70" align="center" header-align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="题型" prop="questionTypeText" width="100" align="center" header-align="center" />
        <el-table-column label="题干" prop="content" min-width="300" align="center" header-align="center"
                         show-overflow-tooltip />
        <el-table-column label="知识点" prop="knowledgeName" width="160" align="center" header-align="center"
                         show-overflow-tooltip />
        <el-table-column label="难度" prop="difficultyText" width="80" align="center" header-align="center" />
        <el-table-column label="分值" width="80" align="center" header-align="center">
          <template #default="{ row }">{{ row.score }}</template>
        </el-table-column>
      </el-table>

      <el-alert v-if="aiResult.warnings && aiResult.warnings.length" type="warning" :closable="false"
                title="题库与方案调整提示" class="ai-warnings">
        <ul class="ai-warn-list">
          <li v-for="(w, i) in aiResult.warnings" :key="i">{{ w }}</li>
        </ul>
      </el-alert>

      <template #footer>
        <el-button @click="aiResultVisible = false">完成</el-button>
        <el-button @click="backToAiSettings">重新调整</el-button>
        <el-button type="primary" plain :loading="aiComposing" @click="handleAiCompose(true)">再生成一次</el-button>
      </template>
    </el-dialog>

    <!-- 随机组卷弹窗 -->
    <el-dialog v-model="randomVisible" title="随机组卷" width="720px" :close-on-click-modal="false">
      <div class="random-tip">
        按「题型 + 难度 + 数量」从题库随机抽题，<b>会覆盖当前已选题目</b>；每个规则可在同一张试卷中出现多次。
      </div>

      <div v-for="(rule, idx) in randomRules" :key="idx" class="random-rule">
        <el-form inline @submit.prevent>
          <el-form-item label="题型">
            <el-select v-model="rule.questionType" placeholder="题型" style="width: 110px">
              <el-option v-for="(label, value) in typeMap" :key="value" :label="label" :value="Number(value)" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="rule.difficulty" placeholder="不限" clearable style="width: 110px">
              <el-option label="简单" :value="1" />
              <el-option label="中等" :value="2" />
              <el-option label="困难" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="题数">
            <el-input-number v-model="rule.count" :min="1" :max="100" style="width: 110px" />
          </el-form-item>
          <el-form-item label="每题分">
            <el-input-number v-model="rule.score" :min="0.5" :max="100" :step="0.5" :precision="1" style="width: 110px" />
          </el-form-item>
          <el-form-item>
            <el-button link type="danger" :icon="Delete" @click="removeRandomRule(idx)">删除</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="random-ops">
        <el-button :icon="Plus" @click="addRandomRule">添加规则</el-button>
        <span class="random-summary">
          共 {{ randomCount }} 题 · 总分 {{ randomScore }} 分
        </span>
      </div>

      <template #footer>
        <el-button @click="randomVisible = false">取消</el-button>
        <el-button type="primary" :loading="randoming" @click="handleRandomCompose">开始抽题</el-button>
      </template>
    </el-dialog>

    <el-row :gutter="16" class="compose-body">
      <!-- 左：题库选题（更宽） -->
      <el-col :span="16">
        <el-card shadow="never" class="full-card">
          <template #header>
            <div class="card-title">题库（点击"添加"选题）</div>
          </template>

          <!-- 搜索 -->
          <el-form inline @submit.prevent class="qb-search">
            <el-form-item label="学科" v-if="paperInfo.courseId">
              <el-select
                v-model="qBank.courseId"
                placeholder="默认仅显示本学科"
                clearable
                style="width: 240px"
                @change="searchBank"
              >
                <el-option
                  v-for="c in courseList"
                  :key="c.id"
                  :label="`${c.courseName} (${c.courseCode})`"
                  :value="c.id"
                />
              </el-select>
              <span class="qb-hint">如需跨学科组卷，清空选择</span>
            </el-form-item>
            <el-form-item>
              <el-select v-model="qBank.questionType" placeholder="题型" clearable style="width: 130px">
                <el-option v-for="(label, value) in typeMap" :key="value" :label="label" :value="Number(value)" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select v-model="qBank.difficulty" placeholder="难度" clearable style="width: 130px">
                <el-option label="简单" :value="1" />
                <el-option label="中等" :value="2" />
                <el-option label="困难" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchBank">筛选</el-button>
            </el-form-item>
          </el-form>

          <div v-if="qBank.courseId" class="qb-tip">
            <el-icon><InfoFilled /></el-icon>
            当前仅展示「{{ qBank.courseName }}」学科的题库，共 {{ qBank.total }} 道。
          </div>
          <div v-else class="qb-tip warn">
            <el-icon><Warning /></el-icon>
            已显示全部 13 个学科的题库，请谨慎选题（建议选择与「{{ paperInfo.courseName || '试卷' }}」匹配的题目）。
          </div>

          <el-table :data="qBank.list" v-loading="loadingBank" stripe size="small" class="bank-table">
            <el-table-column label="题型" width="80">
              <template #default="{ row }">{{ typeMap[row.questionType] || row.questionType }}</template>
            </el-table-column>
            <el-table-column prop="content" label="题干" min-width="400" show-overflow-tooltip />
            <el-table-column prop="score" label="默认分" width="80" align="center" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!isSelected(row.id)"
                  type="primary" link @click="addQuestion(row)"
                >添加</el-button>
                <span v-else class="added-tag">已添加</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="pager"
            layout="total, sizes, prev, pager, next"
            :total="qBank.total"
            v-model:current-page="qBank.pageNum"
            v-model:page-size="qBank.pageSize"
            :page-sizes="[12]"
            @current-change="loadBank"
            @size-change="loadBank"
          />
        </el-card>
      </el-col>

      <!-- 右：已选题目（更紧凑） -->
      <el-col :span="8">
        <el-card shadow="never" class="full-card sel-card">
          <template #header>
            <div class="card-title">
              已选题目
              <span class="tip">（分值可改，顺序可调）</span>
            </div>
          </template>

          <el-empty v-if="selected.length === 0" description="还没选题，从左边题库点「添加」" :image-size="80" />

          <div v-for="(item, index) in selected" :key="item.questionId" class="sel-item">
            <div class="sel-item-head">
              <el-tag size="small">{{ index + 1 }}</el-tag>
              <el-tag size="small" type="info">{{ typeMap[item.questionType] || item.questionType }}</el-tag>
              <div class="sel-item-ops">
                <el-button link :disabled="index === 0" @click="move(index, -1)">↑</el-button>
                <el-button link :disabled="index === selected.length - 1" @click="move(index, 1)">↓</el-button>
                <el-button link type="danger" @click="removeQuestion(index)">移除</el-button>
              </div>
            </div>
            <div class="sel-content">{{ item.content }}</div>
            <div class="sel-score">
              分值：<el-input-number v-model="item.score" :min="0.5" :max="100" :step="0.5" size="small" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPaperDetail, savePaperQuestions, randomCompose, revokePaper, getAiComposeMeta, aiCompose, updatePaper } from '@/api/examPaper'
import { getQuestionList } from '@/api/question'
import { getCourseList } from '@/api/course'
import { getTemplateDetail } from '@/api/paperTemplate'
import { InfoFilled, Warning, MagicStick, Plus, Delete, Cpu } from '@element-plus/icons-vue'
import { usePaperTypeOptions } from '@/composables/usePaperTypeOptions'
import { takeComposeIntent } from '@/composables/composeIntent'

// 组卷方式字典：顶部标签文案/配色，以及点击随机/AI 组卷时提示切换
const { paperTypeLabel, paperTypeTagType, loadPaperTypeOptions } = usePaperTypeOptions()

const route = useRoute()
const router = useRouter()
const paperId = Number(route.params.id)
const templateId = route.query.templateId ? Number(route.query.templateId) : null

const typeMap = {
  1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释'
}

// ---------- 试卷信息 ----------
const paperInfo = ref({})
const loadingDetail = ref(false)

// ---------- 已选题目 ----------
const selected = ref([])

const totalScore = computed(() =>
  selected.value.reduce((sum, item) => sum + Number(item.score || 0), 0).toFixed(1)
)

/**
 * 及格线：满分 × 60%（与后端 exam_paper.pass_score 口径一致），随组卷总分实时变化。
 * 总分为 0（尚未组卷）时回退显示试卷上已保存的及格分。
 */
const passLine = computed(() => {
  const total = Number(totalScore.value) || 0
  if (total <= 0) return paperInfo.value.passScore ?? 0
  return Math.round(total * 0.6 * 10) / 10
})

// ---------- 题库列表 ----------
const courseList = ref([])
const qBank = reactive({
  pageNum: 1,
  // 默认每页 25，与下拉 [25,50] 保持一致
  pageSize: 12,
  questionType: null,
  difficulty: null,
  courseId: null,           // 试卷所属课程 id（默认锁定）
  courseName: '',           // 用于提示文案
  list: [],
  total: 0
})
const loadingBank = ref(false)

// 拉取全部课程列表（供题库筛选下拉用）
async function loadCourses() {
  try {
    const res = await getCourseList()
    courseList.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  }
}

async function loadBank() {
  loadingBank.value = true
  try {
    const params = {
      pageNum: qBank.pageNum,
      pageSize: qBank.pageSize,
      questionType: qBank.questionType ?? undefined,
      difficulty: qBank.difficulty ?? undefined,
      courseId: qBank.courseId ?? undefined
    }
    const res = await getQuestionList(params)
    qBank.list = res.data.records
    qBank.total = Number(res.data.total)
  } catch (e) {
    // 拦截器已提示
  } finally {
    loadingBank.value = false
  }
}

function searchBank() {
  qBank.pageNum = 1
  loadBank()
}

function isSelected(questionId) {
  return selected.value.some(item => item.questionId === questionId)
}

async function addQuestion(row) {
  if (isSelected(row.id)) return
  // 随机 / AI 组卷的试卷改为逐题挑选题目 → 先确认是否切换为手动组卷（保持原方式则本次不添加）
  if (!(await confirmSwitchPaperType(1, '手动组卷', '改为手动组卷并添加'))) return
  selected.value.push({
    questionId: row.id,
    content: row.content,
    questionType: row.questionType,
    score: Number(row.score) || 2
  })
}

function removeQuestion(index) {
  selected.value.splice(index, 1)
}

function move(index, dir) {
  const target = index + dir
  if (target < 0 || target >= selected.value.length) return
  const arr = selected.value
  ;[arr[index], arr[target]] = [arr[target], arr[index]]
}

// ---------- 加载试卷详情（回显已选题目 & 锁定所属学科） ----------
async function loadDetail() {
  loadingDetail.value = true
  try {
    const res = await getPaperDetail(paperId)
    paperInfo.value = res.data.paper
    selected.value = (res.data.questions || []).map(q => ({
      questionId: q.questionId,
      content: q.content,
      questionType: q.questionType,
      score: Number(q.score)
    }))
    // 关键：默认按试卷所属学科筛选题库
    if (paperInfo.value.courseId) {
      qBank.courseId = Number(paperInfo.value.courseId)
      qBank.courseName = paperInfo.value.courseName || ''
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loadingDetail.value = false
  }
}

// ---------- 保存 ----------
const saving = ref(false)

/**
 * 已发布试卷不能组卷：弹窗询问是否撤回。
 * 确认 → 先撤回为草稿再继续组卷；取消/关闭 → 中止本次组卷操作。
 * @returns {Promise<boolean>} true=可继续组卷
 */
async function ensureDraft() {
  if (paperInfo.value.status !== 1) return true
  try {
    await ElMessageBox.confirm(
      '该试卷已发布，无法组卷。是否先撤回（恢复为草稿）后再组卷？',
      '试卷已发布',
      { confirmButtonText: '撤回并重新组卷', cancelButtonText: '取消组卷', type: 'warning' }
    )
  } catch (e) {
    return false
  }
  try {
    await revokePaper(paperId)
    paperInfo.value.status = 0
    ElMessage.success('试卷已撤回，可继续组卷')
    return true
  } catch (e) {
    // 拦截器已提示
    return false
  }
}

/**
 * 把组卷方式写回 exam_paper.paper_type（只改该字段，其余字段原样带回）
 * @param {number} type 目标组卷方式：1手动 2随机 3AI智能
 * @param {boolean} silent 静默模式：不弹成功提示（模板套用时用）
 * @returns {Promise<boolean>} 是否修改成功
 */
async function syncPaperType(type, silent = false) {
  if (Number(paperInfo.value.paperType) === Number(type)) return true
  const info = { ...paperInfo.value }
  try {
    await updatePaper({
      id: paperId,
      paperName: info.paperName,
      courseId: info.courseId,
      paperType: type,
      duration: info.duration
    })
    paperInfo.value = { ...info, paperType: type }
    if (!silent) ElMessage.success(`组卷方式已更改为「${paperTypeLabel(type)}」`)
    return true
  } catch (e) {
    // 拦截器已提示
    return false
  }
}

/**
 * 打开随机 / AI 组卷弹窗前，询问是否更改组卷方式。
 * 典型场景：试卷当前为「手动组卷」，教师点了「随机组卷」/「AI 智能组卷」，
 * 需先确认是否放弃手动组卷；选择保持原方式则该次操作中止（不打开弹窗）。
 * @param {number} targetType 目标组卷方式
 * @param {string} targetLabel 目标方式文案
 * @returns {Promise<boolean>} true=可继续打开对应弹窗
 */
async function confirmSwitchPaperType(targetType, targetLabel, confirmText = '更改并继续') {
  const raw = paperInfo.value.paperType
  // 历史数据 paper_type 为空时按「手动组卷」处理
  const current = raw === null || raw === undefined || raw === '' ? 1 : Number(raw)
  if (current === Number(targetType)) return true
  const currentLabel = paperTypeLabel(current)
  // 组卷方式互斥：切换后原有选题不再适用（随机 / AI 组卷本身也是覆盖式抽题），提前告知会清空
  const clearCount = selected.value.length
  const tip = clearCount > 0 ? `更改后当前已选的 ${clearCount} 道题将被清空。` : ''
  try {
    await ElMessageBox.confirm(
      `当前组卷方式为「${currentLabel}」，是否更改为「${targetLabel}」？${tip}`,
      '更改组卷方式',
      { confirmButtonText: confirmText, cancelButtonText: `保持${currentLabel}`, type: 'warning' }
    )
  } catch (e) {
    return false
  }
  // 静默写库，成功后由这里统一提示（含清空说明）
  const ok = await syncPaperType(targetType, true)
  if (!ok) return false
  // 清空已选题目：仅清空页面上的暂存，点「保存组卷」才会落库，不点保存刷新即恢复
  if (clearCount > 0) selected.value = []
  ElMessage.success(
    clearCount > 0
      ? `组卷方式已更改为「${targetLabel}」，已清空原已选 ${clearCount} 道题`
      : `组卷方式已更改为「${targetLabel}」`
  )
  return true
}

async function handleSave() {
  if (!(await ensureDraft())) return
  if (selected.value.length === 0) {
    ElMessage.warning('请至少选择一道题')
    return
  }
  saving.value = true
  try {
    await savePaperQuestions({
      paperId,
      items: selected.value.map(item => ({
        questionId: item.questionId,
        score: item.score
      }))
    })
    ElMessage.success(`组卷保存成功！共 ${selected.value.length} 题，总分 ${totalScore} 分`)
    router.push('/papers')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

/**
 * 清掉 URL 上的 ai=1 / random=1 标记：弹窗已打开，避免用户关掉后刷新页面又自动弹出
 * 注意：Layout 的 router-view 绑了 :key="route.fullPath"，改 query 会重建本组件，
 * 因此这里只在「不再自动弹窗」的旧参数场景下做兼容清理，正常流程已改走内存意图。
 */
function clearComposeQuery() {
  const { ai, random, ...rest } = route.query
  if (ai === undefined && random === undefined) return
  router.replace({ path: route.path, query: rest })
}

// onMounted: 先加载试卷详情（拿到 courseId 后再 loadBank，避免首屏显示全部学科）
async function init() {
  await loadDetail()
  // 组卷方式字典（顶部标签文案 / 配色），失败时用内置兜底选项
  loadPaperTypeOptions()
  if (courseList.value.length === 0) await loadCourses()
  loadBank()
  // 如果从模板页跳转过来，自动加载模板并预填随机组卷规则
  if (templateId) {
    await applyTemplate(templateId)
  }
  // 按组卷方式自动打开对应弹窗（列表点「组卷」/ 新增试卷跳转时由内存意图带过来）：
  //   2 随机组卷 → 随机组卷弹窗；3 AI智能组卷 → AI 智能组卷弹窗；1 手动组卷不弹，只打开组卷面板
  // 意图读取即消费，模板套用由 applyTemplate 自行处理，不会叠加
  const intentType = takeComposeIntent(paperId)
  if (intentType === 2) {
    await openRandomDialog()
  } else if (intentType === 3) {
    await openAiDialog()
  }
  // 兼容仍带 URL 参数进来的旧链接（已不再主动生成）
  clearComposeQuery()
}

/** 难度文案（模板明细 → AI 补充要求文本用） */
const DIFF_TEXT = { 1: '简单', 2: '中等', 3: '困难' }

/** 模板明细 → 「单选 10 题 ×2 分（中等）」文本，作为 AI 组卷的题型/分值参考 */
function rulesToText(rules) {
  return rules
    .map((r) => {
      const type = typeMap[r.questionType] || '题型'
      const diff = DIFF_TEXT[Number(r.difficulty)] ? `（${DIFF_TEXT[Number(r.difficulty)]}）` : ''
      return `${type} ${r.count} 题 ×${r.score} 分${diff}`
    })
    .join('、')
}

/** 模板明细的难度分布 → AI 难度倾向（1偏基础 2均衡 3偏挑战，取出现次数最多的一档，无则均衡） */
function difficultyPreferenceOf(rules) {
  const count = {}
  rules.forEach((r) => {
    const d = Number(r.difficulty)
    if (d >= 1 && d <= 3) count[d] = (count[d] || 0) + 1
  })
  const top = Object.entries(count).sort((a, b) => b[1] - a[1])[0]
  return top ? Number(top[0]) : 2
}

/**
 * 根据模板 ID 加载模板详情，按模板的组卷方式决定进入组卷页后做什么：
 *  - 1 手动组卷：不预填规则，教师到左侧题库逐题挑选
 *  - 2 随机组卷：预填组卷明细并打开随机抽题弹窗
 *  - 3 AI 智能组卷：明细转成题型/分值参考、模板补充说明写入「补充要求」，打开 AI 弹窗
 */
async function applyTemplate(tplId) {
  try {
    const res = await getTemplateDetail(tplId)
    const tpl = res.data
    if (!tpl) return
    const paperType = Number(tpl.paperType) || 2
    const rawRules = tpl.templateJson ? JSON.parse(tpl.templateJson) : []
    const rules = (Array.isArray(rawRules) ? rawRules : []).map((r) => ({
      questionType: r.questionType,
      difficulty: r.difficulty ?? null,
      count: r.count,
      score: r.score
    }))

    // ① 手动组卷：模板只提供试卷基本信息，提示教师自己挑题
    if (paperType === 1) {
      // 模板决定组卷方式，静默同步到试卷（不改则不请求）
      await syncPaperType(1, true)
      ElMessage.success(`已套用模板「${tpl.templateName}」，请从左侧题库挑选题目`)
      return
    }

    // ② 随机组卷：预填明细并打开随机抽题弹窗
    if (paperType === 2) {
      if (!rules.length) {
        ElMessage.warning('模板配置为空')
        return
      }
      randomRules.value = rules
      // 模板决定组卷方式，静默同步到试卷
      await syncPaperType(2, true)
      // 自动打开随机组卷弹窗，用户可调整后点击"开始抽题"
      randomVisible.value = true
      ElMessage.success(`已载入模板「${tpl.templateName}」的组卷配置`)
      return
    }

    // ③ AI 智能组卷：明细作为题型/分值参考，另带上模板保存的补充说明
    if (!(await ensureDraft())) return
    // 模板决定组卷方式，静默同步到试卷
    await syncPaperType(3, true)
    const requirementParts = []
    if (rules.length) requirementParts.push(`题型与分值参考：${rulesToText(rules)}`)
    if (tpl.aiRequirement && tpl.aiRequirement.trim()) requirementParts.push(tpl.aiRequirement.trim())
    aiForm.totalScore = Number(tpl.totalScore) > 0 ? Math.round(Number(tpl.totalScore)) : 100
    aiForm.difficultyPreference = difficultyPreferenceOf(rules)
    aiForm.knowledgeIds = []
    aiForm.requirement = requirementParts.join('；')
    aiVisible.value = true
    loadAiMeta()
    ElMessage.success(`已载入模板「${tpl.templateName}」的 AI 组卷配置`)
  } catch (e) {
    ElMessage.error(e?.msg || '加载模板失败')
  }
}

onMounted(init)

// ---------- 随机组卷 ----------
const randomVisible = ref(false)
const randoming = ref(false)
const randomRules = ref([])

const randomCount = computed(() =>
  randomRules.value.reduce((sum, r) => sum + (Number(r.count) || 0), 0)
)

const randomScore = computed(() =>
  randomRules.value
    .reduce((sum, r) => sum + (Number(r.count) || 0) * (Number(r.score) || 0), 0)
    .toFixed(1)
)

async function openRandomDialog() {
  if (!(await ensureDraft())) return
  // 手动组卷下点随机组卷 → 先确认是否更改组卷方式
  if (!(await confirmSwitchPaperType(2, '随机组卷'))) return
  // 每次打开都回填一组默认规则（单选10题2分 / 判断5题2分 / 简答2题10分），方便直接调整
  randomRules.value = [
    { questionType: 1, difficulty: null, count: 10, score: 2 },
    { questionType: 3, difficulty: null, count: 5, score: 2 },
    { questionType: 5, difficulty: null, count: 2, score: 10 }
  ]
  randomVisible.value = true
}

function addRandomRule() {
  randomRules.value.push({ questionType: 1, difficulty: null, count: 5, score: 2 })
}

function removeRandomRule(idx) {
  if (randomRules.value.length <= 1) {
    ElMessage.warning('至少保留一条规则')
    return
  }
  randomRules.value.splice(idx, 1)
}

async function handleRandomCompose() {
  // 校验
  const invalid = randomRules.value.find(
    (r) => !r.questionType || !r.count || r.count <= 0 || r.score == null
  )
  if (invalid) {
    ElMessage.warning('请完整填写每一条规则（题型、题数、每题分值）')
    return
  }
  try {
    await ElMessageBox.confirm(
      '随机抽题将覆盖当前已选题目，确认继续？',
      '随机组卷',
      { confirmButtonText: '开始抽题', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  randoming.value = true
  try {
    const res = await doRandomCompose()
    ElMessage.success(res.msg || '随机组卷成功')
    randomVisible.value = false
    // 重新加载试卷详情，回显抽到的题目
    await loadDetail()
  } catch (e) {
    const insufficient = e.res?.data?.insufficient
    if (insufficient && insufficient.length) {
      // 题库不足：弹出选择框（自动调整题数 / 前往添加题目）
      await handleInsufficient(insufficient)
    }
    // 其他错误：拦截器已提示
  } finally {
    randoming.value = false
  }
}

/** 按当前规则实际发起随机组卷请求 */
async function doRandomCompose() {
  return randomCompose(paperId, {
    rules: randomRules.value.map((r) => ({
      questionType: r.questionType,
      difficulty: r.difficulty ?? undefined,
      count: r.count,
      score: r.score
    }))
  })
}

/** 题库不足时弹出选择框：自动调整题数 / 前往添加题目 */
async function handleInsufficient(insufficient) {
  const lines = insufficient.map(
    (i) => `「${typeMap[i.questionType] || i.questionType}」需要 ${i.need} 题，题库仅 ${i.available} 题`
  )
  let action = ''
  try {
    await ElMessageBox.confirm(
      `以下题型题量不足，请选择处理方式：<br/>${lines.join('<br/>')}`,
      '题库不足',
      {
        confirmButtonText: '自动调整题数',
        cancelButtonText: '前往添加题目',
        type: 'warning',
        dangerouslyUseHTMLString: true,
        closeOnClickModal: false
      }
    )
    action = 'adjust'
  } catch (e) {
    // e === 'cancel' 表示点击"前往添加题目"，e === 'close' 表示直接关闭弹窗
    action = e === 'cancel' ? 'goto' : 'close'
  }
  if (action === 'adjust') {
    await autoAdjustAndRetry(insufficient)
  } else if (action === 'goto') {
    goAddQuestions(insufficient)
  }
}

/** 把不足题型的题数自动调整为可用数量后重新抽题 */
async function autoAdjustAndRetry(insufficient) {
  const availableMap = {}
  insufficient.forEach((i) => { availableMap[i.questionType] = i.available })
  const adjusted = randomRules.value
    .map((r) =>
      availableMap[r.questionType] != null
        ? { ...r, count: Math.min(r.count, availableMap[r.questionType]) }
        : r
    )
    .filter((r) => r.count > 0) // 可用为 0 的规则直接剔除
  if (adjusted.length === 0) {
    ElMessage.warning('所选题型当前均无可用题目，请先前往添加题目')
    return
  }
  randomRules.value = adjusted
  ElMessage.success('已自动调整题数，重新抽题中…')
  randoming.value = true
  try {
    const res = await doRandomCompose()
    ElMessage.success(res.msg || '随机组卷成功')
    randomVisible.value = false
    await loadDetail()
  } catch (e) {
    const insufficient2 = e.res?.data?.insufficient
    if (insufficient2 && insufficient2.length) {
      await handleInsufficient(insufficient2)
    }
  } finally {
    randoming.value = false
  }
}

/** 跳转到题库管理页，带上学科和题型筛选，方便直接补题 */
function goAddQuestions(insufficient) {
  const query = { openAdd: 1 }
  if (paperInfo.value.courseId) {
    query.courseId = Number(paperInfo.value.courseId)
  }
  if (insufficient.length === 1) {
    query.questionType = insufficient[0].questionType
  }
  router.push({ path: '/questions', query })
}

// ---------- AI 智能组卷 ----------
const aiVisible = ref(false)
const aiComposing = ref(false)
const aiResultVisible = ref(false)
/** 组卷资源概览：题库题型/难度题量、知识点题量、大模型启用状态 */
const aiMeta = ref({
  aiEnabled: false,
  model: '',
  totalQuestions: 0,
  typeStats: [],
  knowledgeOptions: []
})
/** 组卷要求 */
const aiForm = reactive({
  totalScore: 100,
  difficultyPreference: 2,
  knowledgeIds: [],
  requirement: ''
})
/** AI 组卷方案（规则 + 题目清单 + 调整提示） */
const aiResult = ref({ rules: [], items: [], warnings: [], difficultyStats: [] })
/** 目标总分是否命中（容差与后端一致：1% 且不低于 1 分） */
const aiScoreHit = computed(() => {
  const target = Number(aiResult.value.targetScore)
  const actual = Number(aiResult.value.totalScore)
  if (!target) return true
  return Math.abs(target - actual) <= Math.max(1, target * 0.01)
})

async function openAiDialog() {
  if (!(await ensureDraft())) return
  // 手动组卷下点 AI 智能组卷 → 先确认是否更改组卷方式
  if (!(await confirmSwitchPaperType(3, 'AI智能组卷'))) return
  // 目标总分默认沿用当前试卷总分（已组卷时），否则 100
  const current = Number(totalScore.value)
  aiForm.totalScore = current > 0 ? Math.round(current) : 100
  aiForm.difficultyPreference = 2
  aiForm.knowledgeIds = []
  aiForm.requirement = ''
  aiVisible.value = true
  loadAiMeta()
}

/** 加载题库家底（打开弹窗时刷新一次，管理员刚补题也能立刻看到） */
async function loadAiMeta() {
  try {
    const res = await getAiComposeMeta(paperId)
    aiMeta.value = res.data || aiMeta.value
  } catch (e) {
    // 拦截器已提示
  }
}

/**
 * 方案报告 → 回到组卷配置：保留上次填写的目标总分/难度倾向/重点知识点/补充要求，
 * 并把题库家底重新拉一次，便于微调参数后重新生成方案
 */
function backToAiSettings() {
  aiResultVisible.value = false
  aiVisible.value = true
  loadAiMeta()
}

/**
 * 发起 AI 智能组卷（覆盖式）
 * @param {boolean} again true = 方案报告里点「再生成一次」，无需二次确认
 */
async function handleAiCompose(again = false) {
  if (!(await ensureDraft())) return
  if (!again) {
    try {
      await ElMessageBox.confirm(
        'AI 组卷将按方案重新抽题并覆盖当前已选题目，确认继续？',
        'AI 智能组卷',
        { confirmButtonText: '开始组卷', cancelButtonText: '取消', type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  aiComposing.value = true
  try {
    const res = await aiCompose(paperId, {
      totalScore: aiForm.totalScore,
      difficultyPreference: aiForm.difficultyPreference,
      knowledgeIds: aiForm.knowledgeIds.length ? aiForm.knowledgeIds : undefined,
      requirement: aiForm.requirement?.trim() || undefined
    })
    aiResult.value = res.data || { rules: [], items: [], warnings: [], difficultyStats: [] }
    aiVisible.value = false
    aiResultVisible.value = true
    // 回显抽到的题目（右侧已选列表）
    await loadDetail()
  } catch (e) {
    // 拦截器已提示（试卷未关联学科、题库不足等后端会给出具体原因）
  } finally {
    aiComposing.value = false
  }
}
</script>

<style scoped>
.compose {
  display: flex;
  flex-direction: column;
  /* 撑满视口高度（减去顶部 60 + 边距） */
  min-height: calc(100vh - 80px);
}
.compose-body {
  flex: 1;
  display: flex;
}
.compose-body :deep(.el-col) {
  display: flex;
  flex-direction: column;
}
.compose-body :deep(.el-col .el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.compose-body :deep(.el-col .el-card__body) {
  flex: 1;
  overflow: auto;
}
.head-card {
  margin-bottom: 16px;
  font-size: 15px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.paper-name {
  font-size: 20px;
  font-weight: 600;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.total {
  color: #606266;
  font-size: 15px;
}
.score {
  color: #e6a23c;
  font-size: 18px;
}
.card-title {
  font-weight: 600;
  font-size: 17px;
}
.tip {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
  margin-left: 6px;
}
.qb-search {
  margin-bottom: 4px;
}
.qb-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
}
.qb-tip {
  margin: 8px 0 6px;
  padding: 10px 14px;
  border-radius: 4px;
  background: #f0f9eb;
  color: #67c23a;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.qb-tip.warn {
  background: #fdf6ec;
  color: #e6a23c;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.added-tag {
  color: #909399;
  font-size: 12px;
}
.sel-card :deep(.el-card__body) {
  padding-top: 8px;
  padding-bottom: 8px;
}
.sel-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
  font-size: 15px;
}
.sel-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sel-item-ops {
  margin-left: auto;
}
.sel-content {
  margin: 8px 0;
  color: #303133;
  font-size: 16px;
  line-height: 1.6;
}
.sel-score {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 14px;
}

/* ========== 题库表格：整体放大 ========== */
.bank-table {
  font-size: 16px;
}
.bank-table :deep(th.el-table__cell) {
  font-size: 16px;
  font-weight: 600;
  background: #f5f7fa;
  padding: 12px 0;
}
.bank-table :deep(td.el-table__cell) {
  padding: 14px 0;
  font-size: 14px;
}
.bank-table :deep(.el-table__row) {
  font-size: 14px;
}
/* 操作按钮文字放大 */
.bank-table :deep(.el-button.is-link) {
  font-size: 16px;
}
/* 分页器字号 */
.pager {
  margin-top: 14px;
  font-size: 15px;
}
.pager :deep(.el-pagination__sizes .el-select .el-select__wrapper),
.pager :deep(.el-pagination__sizes) {
  font-size: 15px;
}
.pager :deep(.btn-prev),
.pager :deep(.btn-next),
.pager :deep(.el-pager li) {
  font-size: 15px;
}

/* 随机组卷 */
.random-tip {
  font-size: 13px;
  color: #909399;
  background: #f7f9fb;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 14px;
}
.random-rule {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px 0;
  margin-bottom: 10px;
}
.random-rule :deep(.el-form-item) {
  margin-bottom: 10px;
}
.random-ops {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.random-summary {
  font-size: 13px;
  color: #909399;
}

/* AI 智能组卷 */
.ai-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  background: linear-gradient(90deg, #eff6ff 0%, #f5f7fa 100%);
  border-left: 3px solid #409eff;
  border-radius: 4px;
}
.ai-tip-icon {
  flex-shrink: 0;
  margin-top: 3px;
  font-size: 16px;
  color: #409eff;
}
.ai-src-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.ai-src-hint {
  font-size: 13px;
  color: #909399;
}
.ai-form .ai-unit {
  margin-left: 8px;
  color: #606266;
}
.ai-opt-count {
  float: right;
  font-size: 12px;
  color: #909399;
}
.ai-opt-empty {
  padding: 8px 0;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
.ai-bank {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ai-bank-tag {
  margin: 0;
}

/* AI 组卷方案报告 */
.ai-strategy {
  margin-bottom: 12px;
}
.ai-strategy-text {
  margin-top: 4px;
  line-height: 1.7;
  color: #4b5563;
}
.ai-report-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.ai-rule-table {
  margin-bottom: 14px;
}
.ai-report-sub {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.ai-item-table {
  margin-bottom: 14px;
}
.ai-warn-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}
</style>
