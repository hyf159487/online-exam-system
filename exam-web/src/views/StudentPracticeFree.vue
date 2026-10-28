<template>
  <div class="practice-free">
    <div class="page-title">
      <h2>{{ pageTitle }}</h2>
      <span class="tip">{{ pageTip }}</span>
      <!-- 从「我的错题 / 我的收藏」跳进来、或从右侧画像「错题待复习」卡片点进来时给一条明确回程，
           避免困在刷题页：前者回来源页，后者回本页「按学科自由刷题」的选择界面 -->
      <el-button v-if="isFromCollection" class="back-btn" size="small" @click="goBackFromPractice">
        ← {{ backText }}
      </el-button>
    </div>

    <!-- 子状态机：select（筛选条件）| doing（逐题刷题）| done（本轮统计） -->
    <template v-if="freeStep === 'select' && !isFromCollection">
      <div class="select-layout">
      <el-card shadow="never" class="select-card">
        <div class="section-label">选择要练习的学科</div>
        <div class="select-row">
          <el-select
            v-model="courseId"
            placeholder="请选择要练习的学科（可搜索）"
            clearable
            filterable
            remote
            :remote-method="freeRemoteSearch"
            :loading="freeCourseLoading"
            style="width: 320px"
            size="large"
            @change="handleFreeCourseChange"
            @clear="handleFreeCourseChange(null)"
            @visible-change="handleFreeVisible"
          >
            <el-option
              v-for="c in freeCourseViewOptions"
              :key="c.id"
              :label="courseLabel(c)"
              :value="c.id"
            />
          </el-select>
          <span v-if="courseStats" class="course-stats">
            本学科已练 <b>{{ courseStats.practicedQuestion }}</b> 题
            <span class="stat-sep">｜</span>
            题库共 <b>{{ courseStats.totalQuestion }}</b> 题
          </span>
        </div>

        <div class="section-label" style="margin-top: 24px">刷题条件设置</div>
        <div class="filter-group">
          <div class="filter-row">
            <span class="filter-label">题型</span>
            <el-checkbox-group v-model="freeQuestionTypes" class="filter-box">
              <el-checkbox v-for="t in QUESTION_TYPE_OPTIONS" :key="t.value" :value="t.value">
                {{ t.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <div class="filter-row">
            <span class="filter-label">难度</span>
            <el-checkbox-group v-model="freeDifficultyLevels" class="filter-box">
              <el-checkbox v-for="d in DIFFICULTY_LEVELS" :key="d.value" :value="d.value">
                {{ d.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <div class="filter-row">
            <span class="filter-label">题量</span>
            <el-input-number
              v-model="freeCount"
              :min="5"
              :max="50"
              :step="5"
              controls-position="right"
              style="width: 140px"
            />
            <span class="filter-hint">5~50 题（默认 10 题）</span>
          </div>

          <div class="filter-row">
            <span class="filter-label">知识点</span>
            <el-select
              v-model="freeKnowledgeId"
              placeholder="全部知识点（可选）"
              clearable
              filterable
              :loading="knowledgeLoading"
              style="width: 320px"
            >
              <el-option v-for="kp in freeKnowledgeOptions" :key="kp.id" :label="kp.pointName" :value="kp.id" />
            </el-select>
          </div>
        </div>

        <div class="free-tip">系统将从题库随机抽取题目，可自定义题型、难度和题量；勾选知识点则只刷该知识点下的题目</div>

        <div class="start-btn">
          <el-button type="primary" size="large" :disabled="!courseId" @click="startFreePractice">
            开始练习
          </el-button>
        </div>
      </el-card>

      <!-- 右侧：选中学科后的题库画像（题型 / 难度分布 + 我的练习进度），把页面右侧的空白利用起来 -->
      <el-card ref="profileCardRef" shadow="never" class="profile-card">
        <el-empty
          v-if="!courseId"
          description="选择学科后，这里会展示该学科的题型分布、难度分布与你的练习进度"
          :image-size="86"
        />
        <div v-else-if="profileLoading" class="profile-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          正在统计题库画像…
        </div>
        <template v-else-if="courseStats">
          <div class="profile-head">
            <span class="profile-title">{{ courseName || '该学科' }}</span>
            <el-tag size="small" type="info" effect="plain">题库共 {{ courseStats.totalQuestion }} 题</el-tag>
          </div>

          <div class="metric-row">
            <div class="metric">
              <div class="metric-num">{{ courseStats.practicedQuestion }}</div>
              <div class="metric-label">我已练 / 题</div>
            </div>
            <div class="metric">
              <div class="metric-num">{{ courseStats.coverageRate }}%</div>
              <div class="metric-label">题库覆盖率</div>
            </div>
            <div class="metric">
              <div class="metric-num">{{ courseStats.myAccuracy === null ? '--' : courseStats.myAccuracy + '%' }}</div>
              <div class="metric-label">我的正确率</div>
            </div>
            <!-- 错题待复习：同样大小的格子，做成可点卡片——悬停浮出「点击练习」，点击二次确认后开一轮错题强化 -->
            <div
              class="metric metric-action"
              :class="{ disabled: !courseStats.wrongQuestionCount }"
              @click="startWrongReview"
            >
              <div class="metric-num warn">{{ courseStats.wrongQuestionCount }}</div>
              <div class="metric-label">错题待复习</div>
              <div class="metric-mask">
                {{ courseStats.wrongQuestionCount ? '点击练习' : '暂无错题' }}
              </div>
            </div>
          </div>
          <div class="profile-summary">
            累计作答 <b>{{ courseStats.myAnswered }}</b> 次，答对 <b>{{ courseStats.myCorrect }}</b> 次
            <span v-if="courseStats.myAnswered">（正确率 {{ courseStats.myAccuracy }}%）</span>
          </div>

          <template v-if="courseStats.totalQuestion">
            <div class="block-label">题型分布</div>
            <div ref="typeChartRef" class="chart-box"></div>

            <div class="block-label">难度分布</div>
            <div ref="levelChartRef" class="chart-box chart-box-sm"></div>

            <template v-if="courseStats.knowledgeStats && courseStats.knowledgeStats.length">
              <div class="block-label">知识点题量 Top{{ courseStats.knowledgeStats.length }}</div>
              <div class="kp-list">
                <div v-for="kp in courseStats.knowledgeStats" :key="kp.knowledgeId" class="kp-item">
                  <span class="kp-name" :title="kp.knowledgeName">{{ kp.knowledgeName }}</span>
                  <span class="kp-bar"><i :style="{ width: kpBarWidth(kp) }"></i></span>
                  <span class="kp-count">{{ kp.count }}</span>
                </div>
              </div>
            </template>
          </template>
          <el-empty v-else description="该学科题库暂无审核通过的题目" :image-size="80" />
        </template>
        <el-empty v-else description="题库画像暂时加载失败，可稍后重试" :image-size="80" />
      </el-card>
      </div>
    </template>

    <!-- 错题 / 收藏来源抽不到题时的兜底：给一条明确的回程 -->
    <template v-else-if="freeStep === 'select' && isFromCollection">
      <el-card shadow="never" class="select-card">
        <el-empty :description="emptyText">
          <el-button type="primary" @click="goBackFromPractice">
            {{ backText }}
          </el-button>
        </el-empty>
      </el-card>
    </template>

    <template v-else-if="freeStep === 'doing' && freeCurrentQ">
      <el-card shadow="never" class="progress-card">
        <div class="doing-header">
          <div class="doing-nav">
            <el-button size="small" :disabled="freeIndex === 0" @click="prevQuestion">上一题</el-button>
            <el-button size="small" type="primary" plain :disabled="freeIndex === freeQuestions.length - 1" @click="handleNext">下一题</el-button>
          </div>
          <span class="doing-pos">第 <b>{{ freeIndex + 1 }}</b> / {{ freeQuestions.length }} 题</span>
          <el-progress
            class="doing-bar"
            :percentage="Math.round((freeIndex / freeQuestions.length) * 100)"
            :stroke-width="10"
          />
          <span class="doing-correct">已答对 <b>{{ correctCount }}</b> 题</span>
        </div>
      </el-card>

      <el-card shadow="never" class="q-card">
        <div class="q-meta">
          <el-tag>{{ typeText(freeCurrentQ.questionType) }}</el-tag>
          <el-tag type="info" effect="plain">{{ cardCourseLabel }}</el-tag>
          <el-tag v-if="freeCurrentQ.score" type="warning" effect="plain">分值 {{ freeCurrentQ.score }}</el-tag>
          <!-- 收藏按钮：标记重点题，集中到「我的收藏」复习（错题 + 收藏两套复习体系） -->
          <el-button
            class="q-fav"
            size="small"
            :type="isCurrentFav ? 'warning' : 'default'"
            :icon="isCurrentFav ? StarFilled : Star"
            plain
            @click="toggleFavorite"
          >
            {{ isCurrentFav ? '已收藏' : '收藏' }}
          </el-button>
        </div>

        <!-- 做题过程中随时切换「答题 / 背题」 -->
        <div class="mode-switch">
          <span class="mode-label">学习模式</span>
          <el-radio-group v-model="freeMode" size="small">
            <el-radio-button value="answer">答题模式</el-radio-button>
            <el-radio-button value="review">背题模式</el-radio-button>
          </el-radio-group>
          <span class="mode-hint">
            {{ freeMode === 'answer' ? '自由作答，提交后自动判分，答错收录错题本' : '直接展示答案与解析，可随时 AI 智能讲解' }}
          </span>
        </div>

        <div v-if="freeSubmitted && currentQuestionStat" class="question-stat">
          我已做过 <b>{{ currentQuestionStat.doneCount }}</b> 次，正确率 <b>{{ questionCorrectRate }}</b>%
        </div>

        <div class="q-content">{{ freeCurrentQ.content }}</div>

        <!-- ===== 答题模式：作答输入 + 判分反馈 ===== -->
        <template v-if="freeMode === 'answer'">
          <el-radio-group
            v-if="freeCurrentQ.questionType === 1"
            v-model="answer"
            :disabled="freeSubmitted"
            class="opt-group"
          >
            <el-radio v-for="(o, i) in freeOptionList" :key="i" :value="letter(i)" class="opt-item">
              {{ letter(i) }}. {{ o }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group
            v-else-if="freeCurrentQ.questionType === 2"
            v-model="multiAnswer"
            :disabled="freeSubmitted"
            class="opt-group"
          >
            <el-checkbox v-for="(o, i) in freeOptionList" :key="i" :value="letter(i)" class="opt-item">
              {{ letter(i) }}. {{ o }}
            </el-checkbox>
          </el-checkbox-group>

          <el-radio-group
            v-else-if="freeCurrentQ.questionType === 3"
            v-model="answer"
            :disabled="freeSubmitted"
            class="opt-group"
          >
            <el-radio value="T" class="opt-item">正确（T）</el-radio>
            <el-radio value="F" class="opt-item">错误（F）</el-radio>
          </el-radio-group>

          <el-input
            v-else-if="freeCurrentQ.questionType === 4"
            v-model="answer"
            :disabled="freeSubmitted"
            placeholder="请输入你的答案"
            class="blank-input"
          />

          <el-input
            v-else
            v-model="answer"
            :disabled="freeSubmitted"
            type="textarea"
            :rows="4"
            placeholder="请输入你的答案（主观题不自动判分，可对照参考答案）"
          />

          <template v-if="freeSubmitted">
            <div class="result-banner" :class="resultClass">
              <template v-if="freeCurrentQ.questionType === 5 || freeCurrentQ.questionType === 6">
                📝 主观题不自动判分，请对照参考答案自行评估（不计入错题本）
              </template>
              <template v-else-if="lastResult.correct">
                ✅ 回答正确！
                <span v-if="isFromWrong">已连续答对 {{ lastResult.correctCount ?? 1 }} 次</span>
                <span v-if="lastResult.removed">，达到移除条件，已自动移出错题本 🎉</span>
                <span v-else-if="!isFromWrong">即将自动进入下一题…</span>
              </template>
              <template v-else>
                ❌ 回答错误。
                <span v-if="lastResult.collected">本题已自动收录进「我的错题」</span>
                <span v-else-if="isFromWrong">连续答对次数已清零</span>
              </template>
            </div>
            <div class="ans-line">
              <span class="ans-label">我的答案：</span><span :class="lastResult.correct ? 'ans-ok' : 'ans-bad'">{{ myAnswerText || '（未作答）' }}</span>
            </div>
            <div class="ans-line">
              <span class="ans-label">{{ freeCurrentQ.questionType === 5 || freeCurrentQ.questionType === 6 ? '参考答案：' : '正确答案：' }}</span><span class="ans-ok">{{ lastResult.correctAnswer }}</span>
            </div>
            <div v-if="lastResult.analysis" class="analysis-line">
              <span class="ans-label">解析：</span>{{ lastResult.analysis }}
            </div>
          </template>
        </template>

        <!-- ===== 背题模式：直接展示答案与解析 + AI 智能讲解 ===== -->
        <template v-else-if="freeMode === 'review'">
          <div v-if="freeOptionList.length" class="review-opts">
            <div v-for="(o, i) in freeOptionList" :key="i" class="review-opt">
              <span class="review-opt-key">{{ letter(i) }}</span>
              <span class="review-opt-text">{{ o }}</span>
            </div>
          </div>

          <div class="result-banner review">
            📖 背题模式：直接展示答案与解析，请对照自查；可点击「AI 智能讲解」深入理解
          </div>
          <div class="ans-line">
            <span class="ans-label">{{ isSubjectiveType(freeCurrentQ.questionType) ? '参考答案：' : '正确答案：' }}</span>
            <span class="ans-ok reveal-answer">{{ freeCurrentQ.answer || '（本题暂无答案）' }}</span>
          </div>
          <div v-if="freeCurrentQ.analysis" class="analysis-line">
            <span class="ans-label">解析：</span>{{ freeCurrentQ.analysis }}
          </div>
          <div v-else class="analysis-line">
            <span class="ans-label">解析：</span>本题暂无解析，可点击下方「AI 智能讲解」获取辅导
          </div>
        </template>

        <!-- ===== AI 智能讲解：背题模式与答题模式提交判分后均可使用（多轮对话） ===== -->
        <div v-if="aiExplain.visible" class="ai-explain">
          <div class="ai-header">
            <span class="ai-title">🤖 AI 智能讲解</span>
            <span v-if="aiExplain.source" class="ai-tag" :class="aiExplain.source === 'llm' ? 'llm' : 'local'">
              {{ aiExplain.source === 'llm' ? '大模型生成' : '本地解析引擎' }}
            </span>
            <el-button
              class="ai-restart"
              type="primary"
              size="small"
              plain
              :disabled="aiExplain.loading"
              @click="handlePrimaryAi"
            >
              {{ aiExplain.msgs.length ? '重新开始' : '生成讲解' }}
            </el-button>
          </div>

          <div ref="aiChatBodyRef" class="chat-body">
            <div v-if="!aiExplain.msgs.length" class="chat-empty">
              <p>点击「生成讲解」，AI 会针对本题给出解题思路、解析要点与易错点。</p>
              <p class="chat-empty-sub">之后可以继续追问，例如：为什么选 B？换个角度讲讲 / 出 2 道同类题…</p>
            </div>
            <template v-else>
              <div v-for="(m, i) in aiExplain.msgs" :key="i" class="msg" :class="m.role">
                <div class="msg-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
                <div
                  class="msg-bubble"
                  :class="{ markdown: m.role === 'assistant' }"
                  v-html="m.role === 'assistant' ? renderMarkdown(m.content) : m.content"
                ></div>
              </div>
              <div v-if="aiExplain.loading" class="msg assistant">
                <div class="msg-role">AI</div>
                <div class="msg-bubble markdown typing">正在思考…</div>
              </div>
            </template>
          </div>

          <div class="chat-input">
            <el-input
              v-model="aiExplain.input"
              type="textarea"
              :rows="2"
              resize="none"
              :disabled="aiExplain.loading"
              placeholder="输入你的问题，例如：这道题的知识点怎么理解？出 2 道同类题？"
              @keydown.enter.exact.prevent="sendAiChat()"
            />
            <div class="chat-actions">
              <span class="chat-tip">Enter 发送 / Shift+Enter 换行</span>
              <el-button size="small" :disabled="aiExplain.loading || !aiExplain.msgs.length" @click="clearAiChat">清空对话</el-button>
              <el-button type="primary" size="small" :loading="aiExplain.loading" :disabled="!aiExplain.input.trim()" @click="sendAiChat()">
                发送
              </el-button>
            </div>
          </div>
        </div>

        <div class="actions">
          <!-- 背题模式 -->
          <template v-if="freeMode === 'review'">
            <el-button
              type="primary"
              plain
              size="large"
              :loading="aiExplain.loading"
              @click="toggleAiExplain"
            >
              🤖 AI 智能讲解
            </el-button>
            <el-button type="primary" size="large" @click="handleNext">
              {{ freeIndex === freeQuestions.length - 1 ? '完成本轮' : '下一题' }}
            </el-button>
            <el-button size="large" @click="quitFreePractice">结束练习</el-button>
          </template>

          <!-- 答题模式 -->
          <template v-else>
            <el-button
              v-if="!freeSubmitted"
              type="primary"
              size="large"
              :disabled="!freeHasAnswer"
              @click="handleSubmit"
            >
              提交答案
            </el-button>
            <el-button v-else type="primary" size="large" @click="handleNext">
              {{ freeIndex === freeQuestions.length - 1 ? '查看本轮统计' : '下一题' }}
            </el-button>
            <el-button
              v-if="freeSubmitted"
              type="primary"
              plain
              size="large"
              :loading="aiExplain.loading"
              @click="toggleAiExplain"
            >
              🤖 AI 智能讲解
            </el-button>
            <el-button size="large" @click="quitFreePractice">结束练习</el-button>
          </template>
        </div>
      </el-card>
    </template>

    <template v-else-if="freeStep === 'done'">
      <el-card shadow="never" class="summary-card">
        <div class="summary-title">🎉 本轮练习完成！</div>

        <div class="summary-actions">
          <el-button v-if="freeWrongRecords.length" type="primary" plain @click="restartWrong">
            🔁 重新练习本轮错题（{{ freeWrongRecords.length }} 题）
          </el-button>
          <el-button type="primary" @click="restartSame">
            再练一轮{{ isFromCollection ? '' : `（${courseName}）` }}
          </el-button>
          <el-button v-if="!isFromCollection" @click="freeStep = 'select'">换个学科</el-button>
          <el-button v-else @click="goBackFromPractice">{{ backText }}</el-button>
          <el-button v-if="!isFromWrong" type="warning" @click="$router.push('/student/wrong-questions')">去看错题本</el-button>
        </div>

        <div class="summary-grid">
          <div class="stat-item">
            <div class="stat-num">{{ freeQuestions.length }}</div>
            <div class="stat-name">总题数</div>
          </div>
          <div class="stat-item ok">
            <div class="stat-num">{{ correctCount }}</div>
            <div class="stat-name">答对</div>
          </div>
          <div class="stat-item bad">
            <div class="stat-num">{{ doneTotal - correctCount }}</div>
            <div class="stat-name">答错</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ accuracy }}%</div>
            <div class="stat-name">正确率<small>（已作答）</small></div>
          </div>
        </div>

        <div v-if="freeWrongRecords.length" class="summary-sub">本轮错题（{{ freeWrongRecords.length }} 题）</div>
        <div v-for="(r, i) in freeWrongRecords" :key="r.question.id + '-' + i" class="wrong-item">
          <div class="wrong-q">
            <span class="wrong-no">{{ i + 1 }}.</span>
            {{ r.question.content }}
            <el-tag size="small" type="info" effect="plain" style="margin-left: 6px">{{ typeText(r.question.questionType) }}</el-tag>
          </div>
          <div class="wrong-answer">
            <span class="ans-label">你的答案：</span>
            <span class="ans-bad">{{ formatAnswer(r.userAnswer) || '（未作答）' }}</span>
          </div>
          <div class="wrong-answer">
            <span class="ans-label">{{ r.question.questionType === 5 || r.question.questionType === 6 ? '参考答案：' : '正确答案：' }}</span>
            <span class="ans-ok">{{ r.correctAnswer }}</span>
          </div>
          <div v-if="r.analysis" class="wrong-analysis">
            <span class="ans-label">解析：</span>{{ r.analysis }}
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, reactive, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, StarFilled, Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getPracticeQuestions, submitPractice, getPracticeKnowledgePoints, getCoursePracticeStats,
  getQuestionPracticeStat, chatPracticeQuestion
} from '@/api/studentPractice'
import { getJoinedCourses } from '@/api/course'
import { getFavoriteQuestionIds, addFavorite, removeFavoriteByQuestion } from '@/api/studentFavorite'

const TYPE_MAP = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
const typeText = (t) => TYPE_MAP[t] || '未知'
const letter = (i) => String.fromCharCode(65 + i)

/** 学科选项显示文本：名称 + 编码 */
function courseLabel(c) {
  return c.courseCode ? `${c.courseName}（${c.courseCode}）` : c.courseName
}

/**
 * 学科下拉远程搜索器：输入关键字实时请求后端 /api/student/course/joined，
 * 只返回【当前学生已加入的课程】，按名称/编码模糊查询，空关键字返回全部已加入课程；
 * 已选项始终保留在候选里，避免收起后回显变成裸 ID。
 */
function createCourseRemoteSearch() {
  const options = ref([])     // 远端返回的候选课程（仅已加入）
  const selected = ref(null)  // 当前选中的课程对象
  const loading = ref(false)
  let timer = null

  const viewOptions = computed(() => {
    const list = options.value.slice()
    const cur = selected.value
    if (cur && !list.some((c) => c.id === cur.id)) {
      list.unshift(cur)
    }
    return list
  })

  /** 输入关键字请求后端；空关键字取默认前 50 条 */
  function search(keyword) {
    const kw = (keyword || '').trim()
    clearTimeout(timer)
    timer = setTimeout(async () => {
      loading.value = true
      try {
        const res = await getJoinedCourses(kw)
        options.value = res.data || []
      } catch {
        options.value = []
      } finally {
        loading.value = false
      }
    }, kw ? 250 : 0)
  }

  /** 选中/清空时同步已选对象 */
  function onSelect(val) {
    selected.value = viewOptions.value.find((c) => c.id === val) || null
  }

  return { options, selected, loading, viewOptions, search, onSelect }
}

// ---------- 学科 remote 搜索 ----------
const freeCourseSearch = createCourseRemoteSearch()
const freeCourseLoading = freeCourseSearch.loading
const freeCourseViewOptions = freeCourseSearch.viewOptions
function freeRemoteSearch(kw) { freeCourseSearch.search(kw) }
function handleFreeCourseChange(val) {
  freeCourseSearch.onSelect(val)
}
function handleFreeVisible(visible) {
  if (visible && freeCourseSearch.options.value.length === 0) {
    freeCourseSearch.search('') // 首次展开时拉默认列表
  }
}

// ========== 练习来源：normal=按学科自由刷题 / wrong=错题强化 / favorite=收藏题练习 ==========
// 「我的错题 / 我的收藏」页上方的「开始练习」入口会带 query 跳到本页，
// 复用这一整套做题界面（答题/背题切换、收藏、AI 讲解、结果统计），不用在操作列一题一题做。
const route = useRoute()
const router = useRouter()
const practiceSource = ref('normal')
const isFromWrong = computed(() => practiceSource.value === 'wrong')
const isFromFavorite = computed(() => practiceSource.value === 'favorite')
/** 是否由错题本 / 收藏页跳转而来（而不是自由刷题） */
const isFromCollection = computed(() => practiceSource.value !== 'normal')

/**
 * 是否由本页右侧画像卡片的「错题待复习」格子点进来的。
 * 它和「我的错题 / 我的收藏」共用同一套错题练习界面，但返回出口不一样：
 * 卡片是本页自己的入口，返回应该回到本页「按学科自由刷题」的选择界面，而不是错题本。
 */
const enterFromProfile = ref(false)
/**
 * 卡片入口这一轮的抽题条件：放本地而不写进 URL。
 * 写进 URL 会让 route.fullPath 变化，StudentLayout 的 :key 立刻把整个页面重建，
 * 已选学科和画像全丢，返回来自然回不到刚才那张卡片。
 */
const cardWrongParams = ref(null)
/** 返回按钮文案：三种入口各自回哪儿，直接写在按钮上 */
const backText = computed(() => {
  if (enterFromProfile.value) return '返回按学科刷题'
  return isFromWrong.value ? '返回我的错题' : '返回我的收藏'
})

const pageTitle = computed(() =>
  isFromWrong.value ? '错题强化练习' : isFromFavorite.value ? '收藏题练习' : '按学科自由刷题'
)
const pageTip = computed(() => {
  if (isFromWrong.value) return '从你的错题中随机抽题；答对会累计连续答对次数，达到移除策略阈值时自动移出错题本'
  if (isFromFavorite.value) return '从你收藏的试题中随机抽题，集中攻克重点题'
  return '自选学科、题型，随机抽取试题练习'
})
const emptyText = computed(() =>
  isFromWrong.value ? '没有符合条件的错题，换个条件再试试' : '没有符合条件的收藏题，换个条件再试试'
)

// ========== 自由刷题：筛选 / 答题 / 统计 ==========
const freeStep = ref('select')
const courseId = ref(null)
/** 当前学科练习画像：题库家底（题型/难度/知识点分布）+ 我的练习进度 */
const courseStats = ref(null)
/** 画像加载中（切学科时右侧卡片显示占位，避免闪现上一个学科的数据） */
const profileLoading = ref(false)
/** 画像图表容器 */
const typeChartRef = ref(null)
const levelChartRef = ref(null)
/** ECharts 实例放普通变量：放进 ref 会被 Vue 深度代理，图表内部状态可能异常 */
let typeChart = null
let levelChart = null
/** 右侧画像卡：卡片入口进入错题练习后，返回时把这张卡滚回可见位置 */
const profileCardRef = ref(null)
/** 当前题目该学生的作答统计：{ doneCount, correctCount } */
const currentQuestionStat = ref(null)
const freeQuestions = ref([])
const freeIndex = ref(0)
const answer = ref('')
const multiAnswer = ref([])
const freeSubmitted = ref(false)
const lastResult = ref(null)
const correctCount = ref(0)
const doneTotal = ref(0)
/** 本轮作答记录（用于错题汇总） */
const freeRecord = ref([])

// ---------- 收藏：把重点题标记进「我的收藏」（与错题本并列的另一套复习体系）----------
/** 当前学生已收藏的试题 ID 列表（本轮题目卡片上显示「已收藏」状态） */
const favIds = ref([])

/** 加载已收藏题目 ID（每轮练习开始时拉一次，收藏量级小，一次取全量更省往返） */
async function loadFavIds() {
  try {
    const res = await getFavoriteQuestionIds()
    favIds.value = res.data || []
  } catch {
    /* 收藏状态加载失败不影响刷题，保持空列表即可 */
  }
}

/** 收藏 / 取消收藏当前题目（收藏为幂等操作，重复点击不会报错） */
async function toggleFavorite() {
  const q = freeCurrentQ.value
  if (!q) return
  try {
    if (favIds.value.includes(q.id)) {
      await removeFavoriteByQuestion(q.id)
      favIds.value = favIds.value.filter((id) => id !== q.id)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(q.id)
      favIds.value = [...favIds.value, q.id]
      ElMessage.success('已加入「我的收藏」，可在侧边「我的收藏」中集中复习')
    }
  } catch (e) {
    ElMessage.error(e?.msg || e?.message || '操作失败')
  }
}

// ---- 刷题条件设置 ----
/** 题型选项（默认全选） */
const QUESTION_TYPE_OPTIONS = [
  { value: 1, label: '单选题' },
  { value: 2, label: '多选题' },
  { value: 3, label: '判断题' },
  { value: 4, label: '填空题' },
  { value: 5, label: '简答题' },
]
/** 三档难度 → difficulty 值集合（简单=1,2 / 中等=3 / 困难=4,5） */
const DIFFICULTY_LEVELS = [
  { value: 'easy', label: '简单', range: [1, 2] },
  { value: 'medium', label: '中等', range: [3] },
  { value: 'hard', label: '困难', range: [4, 5] },
]
const freeQuestionTypes = ref([1, 2, 3, 4, 5])
const freeDifficultyLevels = ref(['easy', 'medium', 'hard'])
const freeCount = ref(10)
const freeKnowledgeId = ref(null)
const freeKnowledgeOptions = ref([])
const knowledgeLoading = ref(false)
const FREE_COND_KEY = 'student_free_practice_conditions'

/** 学习模式：answer=答题模式（默认）/ review=背题模式（做题过程中随时切换） */
const freeMode = ref('answer')

const aiExplain = reactive({
  visible: false,   // AI 讲解面板是否展开
  loading: false,
  source: '',       // 最新一条 AI 回复来源：llm / local
  msgs: [],         // [{ role: 'user' | 'assistant', content }]
  input: '',
})
const aiChatBodyRef = ref(null)

const freeCurrentQ = computed(() => freeQuestions.value[freeIndex.value] || null)
const courseName = computed(() => freeCourseSearch.selected.value?.courseName || '')

/** 题目卡片上的分类标签：来源模式可能跨学科，直接显示练习类型 */
const cardCourseLabel = computed(() => {
  if (isFromWrong.value) return '错题强化'
  if (isFromFavorite.value) return '收藏题'
  return courseName.value
})

/** 当前题是否已收藏（题目卡片上的收藏按钮状态） */
const isCurrentFav = computed(() => !!freeCurrentQ.value && favIds.value.includes(freeCurrentQ.value.id))

/** 当前题目该学生的正确率（%），无作答记录时为 0 */
const questionCorrectRate = computed(() => {
  const s = currentQuestionStat.value
  if (!s || !s.doneCount) return 0
  return Math.round((s.correctCount / s.doneCount) * 100)
})

const freeOptionList = computed(() => {
  if (!freeCurrentQ.value?.options) return []
  try {
    const arr = JSON.parse(freeCurrentQ.value.options)
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
})

const freeHasAnswer = computed(() => {
  if (!freeCurrentQ.value) return false
  if (freeCurrentQ.value.questionType === 2) return multiAnswer.value.length > 0
  return answer.value.trim() !== ''
})

const myAnswerText = computed(() => {
  if (!freeCurrentQ.value) return ''
  if (freeCurrentQ.value.questionType === 2) return multiAnswer.value.slice().sort().join(',')
  if (freeCurrentQ.value.questionType === 3) return answer.value === 'T' ? '正确（T）' : answer.value === 'F' ? '错误（F）' : ''
  return answer.value
})

const resultClass = computed(() => {
  if (freeCurrentQ.value?.questionType === 5 || freeCurrentQ.value?.questionType === 6) return 'neutral'
  return lastResult.value?.correct ? 'ok' : 'bad'
})

const accuracy = computed(() => doneTotal.value ? Math.round((correctCount.value / doneTotal.value) * 100) : 0)

/** 本轮答错的题目（用于结果页「错题汇总」） */
const freeWrongRecords = computed(() => freeRecord.value.filter((r) => !r.correct))

/** 多选题答案拼接格式化 */
function formatAnswer(ans) {
  if (!ans) return ''
  return String(ans).split(',').filter(Boolean).join('、')
}

// 学科变化时加载该课程知识点 + 已练习题数统计 + 清空已选知识点
watch(courseId, (val) => {
  freeKnowledgeId.value = null
  // 错题 / 收藏来源用不到知识点与题库统计，省掉这两次请求
  if (practiceSource.value !== 'normal') {
    freeKnowledgeOptions.value = []
    courseStats.value = null
    return
  }
  if (val) {
    loadKnowledgePoints(val)
    loadCourseStats(val)
  } else {
    freeKnowledgeOptions.value = []
    courseStats.value = null
    disposeProfileCharts()
  }
})

// 切回答题模式且当前题尚未提交时，关闭 AI 讲解面板，避免提前看到答案
watch(freeMode, (v) => {
  if (v === 'answer' && !freeSubmitted.value) resetReviewQuestion()
})

// 回到「选择学科」页时刷新练习统计，把刚做完的题计入「已练习题数」
watch(freeStep, (v) => {
  if (v === 'select' && practiceSource.value === 'normal') loadCourseStats(courseId.value)
})

function resetFreeQuestion() {
  answer.value = ''
  multiAnswer.value = []
  freeSubmitted.value = false
  lastResult.value = null
  currentQuestionStat.value = null
}

/* ==================== 背题模式 ==================== */

const isSubjectiveType = (t) => t === 5 || t === 6

/** 进入新题时重置背题展示与 AI 对话 */
function resetReviewQuestion() {
  aiExplain.visible = false
  aiExplain.loading = false
  aiExplain.source = ''
  aiExplain.msgs = []
  aiExplain.input = ''
}

function scrollAiChatToBottom() {
  nextTick(() => {
    const el = aiChatBodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 展开/收起 AI 讲解面板；首次展开且无消息时自动发起讲解 */
function toggleAiExplain() {
  if (aiExplain.loading) return
  // 展开讲解时取消「答对自动跳下一题」，让用户有充足时间研究本题
  clearTimeout(autoNextTimer)
  autoNextTimer = null
  aiExplain.visible = !aiExplain.visible
  if (aiExplain.visible && !aiExplain.msgs.length) handlePrimaryAi()
}

/** 头部按钮：无对话=「生成讲解」（发默认开场白）；有对话=「重新开始」（清空重来） */
function handlePrimaryAi() {
  if (aiExplain.loading || !freeCurrentQ.value) return
  if (!aiExplain.msgs.length) {
    sendAiChat('请帮我讲解这道题：说明考查的核心知识点，给出正确的解题思路与完整解析，并补充这道题的关键记忆点与易错点，最后给出学习建议。')
  } else {
    clearAiChat()
  }
}

function clearAiChat() {
  aiExplain.msgs = []
  aiExplain.input = ''
  aiExplain.source = ''
  scrollAiChatToBottom()
}

/** 发送一轮提问（首问或追问都走同一接口，带上前面的历史上下文） */
async function sendAiChat(text) {
  const q = (text ?? aiExplain.input).trim()
  if (!q || aiExplain.loading || !freeCurrentQ.value) return
  aiExplain.msgs.push({ role: 'user', content: q })
  aiExplain.input = ''
  aiExplain.loading = true
  aiExplain.source = ''
  scrollAiChatToBottom()
  const history = aiExplain.msgs.slice(0, -1).map((m) => ({ role: m.role, content: m.content }))
  try {
    const res = await chatPracticeQuestion(freeCurrentQ.value.id, { question: q, history })
    const content = res.data?.content || '（AI 未返回讲解内容，请稍后重试）'
    aiExplain.msgs.push({ role: 'assistant', content })
    aiExplain.source = res.data?.source || ''
  } catch (err) {
    aiExplain.msgs.push({ role: 'assistant', content: '讲解生成失败：' + (err.msg || err.message || '网络异常') })
  } finally {
    aiExplain.loading = false
    scrollAiChatToBottom()
  }
}

/** 简易 Markdown 渲染（标题/加粗/行内代码/列表/换行），内容来自后端 AI，转义后渲染 */
function renderMarkdown(text) {
  if (!text) return ''
  return text
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
}

/** 校验刷题条件：题型至少一种、题量在 5~50 */
function validateFreeConditions() {
  if (!freeQuestionTypes.value.length) {
    ElMessage.warning('请至少选择一种题型')
    return false
  }
  const n = Number(freeCount.value)
  if (!n || n < 5 || n > 50) {
    ElMessage.warning('题目数量需在 5~50 之间')
    return false
  }
  return true
}

/** 把前端勾选的「难度档位」展开为 difficulty 值集合 */
function buildDifficulties() {
  return freeDifficultyLevels.value
    .map((level) => DIFFICULTY_LEVELS.find((d) => d.value === level)?.range || [])
    .flat()
}

/** 保存刷题条件到本地（下次打开自动沿用） */
function saveFreeConditions() {
  try {
    localStorage.setItem(FREE_COND_KEY, JSON.stringify({
      questionTypes: freeQuestionTypes.value,
      difficultyLevels: freeDifficultyLevels.value,
      count: freeCount.value,
    }))
  } catch {}
}

/** 读取上次保存的刷题条件 */
function loadFreeConditions() {
  try {
    const raw = localStorage.getItem(FREE_COND_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (Array.isArray(saved.questionTypes) && saved.questionTypes.length) {
      freeQuestionTypes.value = saved.questionTypes
    }
    if (Array.isArray(saved.difficultyLevels) && saved.difficultyLevels.length) {
      freeDifficultyLevels.value = saved.difficultyLevels
    }
    if (saved.count >= 5 && saved.count <= 50) {
      freeCount.value = saved.count
    }
  } catch {}
}

/** 加载某课程的知识点（供知识点下拉选择） */
async function loadKnowledgePoints(cid) {
  if (!cid) { freeKnowledgeOptions.value = []; return }
  knowledgeLoading.value = true
  try {
    const res = await getPracticeKnowledgePoints(cid)
    freeKnowledgeOptions.value = res.data || []
  } catch {
    freeKnowledgeOptions.value = []
  } finally {
    knowledgeLoading.value = false
  }
}

/** 加载某学科练习画像：题库家底 + 我的练习进度（右侧三块内容的统一数据源） */
async function loadCourseStats(cid) {
  if (!cid) {
    courseStats.value = null
    disposeProfileCharts()
    return
  }
  profileLoading.value = true
  try {
    const res = await getCoursePracticeStats(cid)
    courseStats.value = res.data || null
    renderProfileCharts()
  } catch {
    courseStats.value = null
    disposeProfileCharts()
  } finally {
    profileLoading.value = false
  }
}

/* ==================== 学科题库画像（右侧图表） ==================== */

const TYPE_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9b6cf0', '#909399']
/** 难度档配色：1=简单 2=中等 3=困难 */
const LEVEL_COLORS = { 1: '#67c23a', 2: '#e6a23c', 3: '#f56c6c' }

/** 知识点条形的相对宽度：以榜首题量为 100%，下限 8% 保证短条也看得见 */
function kpBarWidth(kp) {
  const top = courseStats.value?.knowledgeStats?.[0]?.count || 1
  return `${Math.max(8, Math.round((kp.count / top) * 100))}%`
}

/** 当前勾选的题型：右侧图表把没勾的题型淡化，让「筛选项」和「分布图」对得上 */
const selectedTypeSet = computed(() => new Set(freeQuestionTypes.value))
/** 当前勾选的难度档（easy/medium/hard → 1/2/3，与后端难度档口径一致） */
const selectedLevelSet = computed(() => {
  const levelOf = { easy: 1, medium: 2, hard: 3 }
  return new Set(freeDifficultyLevels.value.map((v) => levelOf[v]).filter(Boolean))
})

/** 渲染画像的两张图（题型环形 + 难度条形）；没有数据时销毁实例，避免残留上一个学科的图 */
function renderProfileCharts() {
  const s = courseStats.value
  nextTick(() => {
    if (!s || !s.totalQuestion || !typeChartRef.value) {
      disposeProfileCharts()
      return
    }
    // 容器可能因 v-if 重建（清空学科后再选），实例挂在旧 DOM 上会导致图表不显示
    if (typeChart && typeChart.getDom() !== typeChartRef.value) {
      typeChart.dispose()
      typeChart = null
    }
    if (!typeChart) typeChart = echarts.init(typeChartRef.value)
    typeChart.setOption({
      color: TYPE_COLORS,
      tooltip: { trigger: 'item', formatter: '{b}：{c} 道（{d}%）' },
      legend: {
        bottom: 0,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        textStyle: { fontSize: 12, color: '#606266' }
      },
      series: [
        {
          type: 'pie',
          radius: ['46%', '68%'],
          center: ['50%', '38%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 3 },
          label: { show: false },
          labelLine: { show: false },
          data: (s.typeStats || []).map((t) => ({
            name: t.questionTypeText,
            value: t.count,
            itemStyle: { opacity: selectedTypeSet.value.has(t.questionType) ? 1 : 0.25 }
          }))
        }
      ],
      // 环心直接写题库总量，比图例再找一遍「加起来多少」直观
      graphic: [
        {
          type: 'text',
          left: 'center',
          top: '30%',
          style: { text: String(s.totalQuestion), fontSize: 22, fontWeight: 700, fill: '#303133' }
        },
        {
          type: 'text',
          left: 'center',
          top: '43%',
          style: { text: '题库题量', fontSize: 12, fill: '#909399' }
        }
      ]
    }, true)

    if (!levelChartRef.value) return
    if (levelChart && levelChart.getDom() !== levelChartRef.value) {
      levelChart.dispose()
      levelChart = null
    }
    if (!levelChart) levelChart = echarts.init(levelChartRef.value)
    // 纵向按「困难 → 简单」排，难度递减更直观；占比后端算好，避免各端口径不一致
    const levels = [...(s.levelStats || [])].reverse()
    const maxCount = Math.max(1, ...levels.map((l) => l.count))
    levelChart.setOption({
      grid: { left: 2, right: 8, top: 6, bottom: 2, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (p) => `${p[0].name}：${p[0].value} 道（${levels[p[0].dataIndex]?.ratio ?? 0}%）`
      },
      // 留出 45% 的横向余量给条尾的「N 道 · x%」标签，长条也不会压到容器边缘
      xAxis: { type: 'value', show: false, max: maxCount * 1.45 },
      yAxis: {
        type: 'category',
        data: levels.map((l) => l.levelText),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#606266', fontSize: 12 }
      },
      series: [
        {
          type: 'bar',
          barWidth: 14,
          data: levels.map((l) => ({
            value: l.count,
            itemStyle: {
              color: LEVEL_COLORS[l.level],
              borderRadius: [0, 7, 7, 0],
              opacity: selectedLevelSet.value.has(l.level) ? 1 : 0.25
            }
          })),
          label: {
            show: true,
            position: 'right',
            color: '#909399',
            fontSize: 12,
            formatter: (p) => `${levels[p.dataIndex]?.count ?? 0} 道 · ${levels[p.dataIndex]?.ratio ?? 0}%`
          }
        }
      ]
    }, true)
  })
}

/** 销毁画像图表实例（切学科 / 离开页面时调用，防止内存泄漏） */
function disposeProfileCharts() {
  if (typeChart) { typeChart.dispose(); typeChart = null }
  if (levelChart) { levelChart.dispose(); levelChart = null }
}

/** 窗口尺寸变化时让图表自适应（ECharts 实例不在响应式里，不会自动跟随容器） */
function handleProfileResize() {
  typeChart?.resize()
  levelChart?.resize()
}

// 勾选题型 / 难度后重绘画像图（仅在画像已有数据时重绘，避免空转）
watch([freeQuestionTypes, freeDifficultyLevels], () => {
  if (courseStats.value?.totalQuestion) renderProfileCharts()
}, { deep: true })

async function startFreePractice() {
  if (!courseId.value) return
  if (!validateFreeConditions()) return
  const params = { courseId: courseId.value, count: Number(freeCount.value) }
  params.questionTypes = freeQuestionTypes.value.join(',')             // 逗号分隔传给后端
  const diffs = buildDifficulties()
  if (diffs.length) params.difficulties = diffs.join(',')
  if (freeKnowledgeId.value) params.knowledgeId = freeKnowledgeId.value
  saveFreeConditions()
  try {
    const res = await getPracticeQuestions(params)
    const list = res.data || []
    if (!list.length) {
      ElMessage.warning('暂无满足条件的试题，请修改筛选条件')
      return
    }
    beginRound(list)
  } catch {}
}

/** 开始新一轮练习：统一初始化本轮状态（自由刷题 / 错题强化 / 收藏练习三条入口共用） */
function beginRound(list) {
  freeQuestions.value = list
  freeIndex.value = 0
  correctCount.value = 0
  doneTotal.value = 0
  freeRecord.value = []
  resetFreeQuestion()
  resetReviewQuestion()
  loadFavIds() // 同步本轮题目的收藏状态
  freeStep.value = 'doing'
}

/** 从「我的错题 / 我的收藏」跳转而来：按 URL 上的条件直接抽题开始做题 */
async function startFromSource() {
  const q = route.query
  const params = { source: practiceSource.value, count: Number(q.count) || 10 }
  if (q.courseId) params.courseId = Number(q.courseId)
  if (q.questionTypes) params.questionTypes = String(q.questionTypes)
  if (q.wrongCountMin) params.wrongCountMin = Number(q.wrongCountMin)
  if (q.wrongCountMax) params.wrongCountMax = Number(q.wrongCountMax)

  // 只回填学科：题型/题量不动，避免污染「按学科自由刷题」记住的条件
  courseId.value = params.courseId ?? null

  try {
    const res = await getPracticeQuestions(params)
    const list = res.data || []
    if (!list.length) {
      ElMessage.warning(emptyText.value)
      freeStep.value = 'select' // 落到空态卡片，保留返回来源页的出口
      return
    }
    beginRound(list)
  } catch {
    freeStep.value = 'select'
  }
}

/** 返回来源页面（我的错题 / 我的收藏） */
function goBackToSource() {
  router.push(isFromWrong.value ? '/student/wrong-questions' : '/student/favorites')
}

/** 从「错题待复习」卡片进来的：退回本页「按学科自由刷题」的选择界面，而不是错题本 */
async function returnToFreeSelect() {
  clearTimeout(autoNextTimer)
  enterFromProfile.value = false
  cardWrongParams.value = null
  practiceSource.value = 'normal'
  freeStep.value = 'select' // freeStep 的 watch 会顺带刷新该学科的题库画像（错题数可能刚变过）

  // 这里刻意不碰路由：StudentLayout 用 :key="route.fullPath" 渲染页面，
  // 只要 fullPath 一变整个组件就会被重建，已选学科、画像、滚动位置全被清空，
  // 也就回不到刚才那张卡片。同一路由下本地切换状态，原样保留最省事。
  await nextTick()
  // 画像刷新期间卡内会被「正在统计…」占位替换，所以滚整张画像卡（容器一直在），而不是其中的格子
  const el = profileCardRef.value?.$el || profileCardRef.value
  el?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

/**
 * 统一的返回出口：先看这一轮错题练习是从哪儿开的。
 * 卡片是本页自己的入口 → 回选学科界面；错题本 / 收藏页跳进来 → 回来源页。
 */
function goBackFromPractice() {
  if (enterFromProfile.value) {
    returnToFreeSelect()
    return
  }
  goBackToSource()
}

/** 卡片入口：按本地条件抽该学科错题开一轮（首次进入与「再练一轮」共用同一份条件） */
async function startCardWrongRound() {
  const p = cardWrongParams.value
  if (!p) return
  try {
    const res = await getPracticeQuestions({ source: 'wrong', courseId: p.courseId, count: p.count })
    const list = res.data || []
    if (!list.length) {
      ElMessage.warning('该学科暂无可抽的错题')
      returnToFreeSelect() // 抽不到就退回卡片所在的界面，别把页面卡在错题来源上
      return
    }
    beginRound(list)
  } catch {
    returnToFreeSelect()
  }
}

/** 右侧画像「错题待复习」卡片：确认后直接在当前页开一轮该学科的错题强化练习 */
async function startWrongReview() {
  const total = courseStats.value?.wrongQuestionCount || 0
  if (!total) {
    ElMessage.info('该学科暂无待复习的错题，先自由刷题积累一下吧')
    return
  }
  // 一轮最多 10 题，剩下的可以下一轮继续，避免一次刷到疲倦
  const count = Math.min(10, total)
  try {
    await ElMessageBox.confirm(
      `《${courseName.value}》有 ${total} 道错题待复习，现在开始错题强化练习？`,
      '错题强化练习',
      { confirmButtonText: `开始练习（${count} 题）`, cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return // 用户点了取消
  }

  cardWrongParams.value = { courseId: courseId.value, count }
  practiceSource.value = 'wrong'
  enterFromProfile.value = true // 记住入口是本页卡片：返回时回选学科界面，而不是错题本
  await startCardWrongRound()
}

async function handleSubmit() {
  if (!freeHasAnswer.value || freeSubmitted.value) return
  const q = freeCurrentQ.value
  const userAnswer = q.questionType === 2 ? multiAnswer.value.slice().sort().join(',') : answer.value
  try {
    const res = await submitPractice({
      questionId: q.id,
      userAnswer,
      // 来自「我的错题」的练习：后端判分后同步「连续答对次数」，达阈值自动移出错题本
      fromWrong: isFromWrong.value
    })
    lastResult.value = res.data || {}
    freeSubmitted.value = true
    doneTotal.value += 1
    if (res.data?.correct) correctCount.value += 1
    if (res.data?.removed) ElMessage.success('已达到移除策略，本题已自动移出错题本 🎉')
    // 记录本轮作答，用于做完后的「错题汇总」
    freeRecord.value.push({
      question: q,
      userAnswer,
      correct: !!res.data?.correct,
      correctAnswer: res.data?.correctAnswer || '',
      analysis: res.data?.analysis || '',
    })
    // 答对（客观题）→ 短暂展示反馈后自动进入下一题；答错 / 主观题停留在本题对照解析
    if (res.data?.correct && !isSubjectiveType(q.questionType)) autoNextSoon(q.id)
    // 答题后加载该生对此题的作答统计（已做过几次 / 正确率）
    loadQuestionStat(q.id)
  } catch {}
}

/** 加载当前题目该学生的作答统计（已做过几次 / 正确率） */
async function loadQuestionStat(qid) {
  if (!qid) { currentQuestionStat.value = null; return }
  try {
    const res = await getQuestionPracticeStat(qid)
    currentQuestionStat.value = res.data || null
  } catch {
    currentQuestionStat.value = null
  }
}

/** 「答对自动进入下一题」延时器 */
let autoNextTimer = null
function autoNextSoon(qid) {
  clearTimeout(autoNextTimer)
  autoNextTimer = setTimeout(() => {
    autoNextTimer = null
    // 已结束练习 / 切到背题 / 手动切过题（当前题目不一致）则不再自动跳
    if (freeStep.value !== 'doing' || freeMode.value !== 'answer') return
    if (!freeCurrentQ.value || freeCurrentQ.value.id !== qid) return
    handleNext()
  }, 2200)
}

function handleNext() {
  if (freeIndex.value >= freeQuestions.value.length - 1) {
    freeStep.value = 'done'
    return
  }
  freeIndex.value += 1
  resetFreeQuestion()
  resetReviewQuestion()
}

/** 进度栏「上一题」 */
function prevQuestion() {
  if (freeIndex.value <= 0) return
  freeIndex.value -= 1
  resetFreeQuestion()
  resetReviewQuestion()
}

function quitFreePractice() {
  clearTimeout(autoNextTimer)
  // 错题 / 收藏来源：走统一的返回出口（卡片进来→回选学科界面，页面跳进来→回来源页），
  // 避免中途退出时落到一个和入口对不上的界面
  if (isFromCollection.value) {
    goBackFromPractice()
    return
  }
  freeStep.value = 'select'
}

/** 再练一轮：自由刷题沿用当前条件；错题 / 收藏来源按 URL 条件重新抽题；卡片入口按本地条件重抽 */
function restartSame() {
  if (enterFromProfile.value) {
    startCardWrongRound() // 卡片入口的条件没写进 URL，直接复用本地记录
    return
  }
  if (isFromCollection.value) {
    startFromSource()
    return
  }
  startFreePractice()
}

/** 重新练习本轮错题：仅用本轮答错的题再开一轮，聚焦薄弱点 */
function restartWrong() {
  const wrong = freeWrongRecords.value.map((r) => r.question)
  if (!wrong.length) {
    ElMessage.info('本轮全部答对，暂无错题可练')
    return
  }
  beginRound(wrong)
}

onMounted(async () => {
  const src = String(route.query.source || '')
  // 从「我的错题 / 我的收藏」跳进来：直接按条件抽题开练
  if (src === 'wrong' || src === 'favorite') {
    practiceSource.value = src
    await startFromSource()
    return
  }
  loadFreeConditions() // 恢复上次刷题条件（题型/难度/题量）
})

onMounted(() => window.addEventListener('resize', handleProfileResize))
onUnmounted(() => {
  window.removeEventListener('resize', handleProfileResize)
  disposeProfileCharts()
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
.page-title h2 { margin: 0; font-size: 20px; color: #303133; }
.tip { font-size: 13px; color: #909399; }
.back-btn { margin-left: auto; }

/* 筛选区：左侧条件卡 + 右侧学科画像卡，宽屏并排、窄屏自动堆叠 */
.select-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 16px;
  align-items: start;
}
.section-label { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 14px; }
.select-card { margin-bottom: 0; }
@media (max-width: 1280px) {
  .select-layout { grid-template-columns: 1fr; }
  .select-card { margin-bottom: 16px; }
}

/* 右侧：学科题库画像 */
.profile-card { min-height: 340px; }
.profile-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 240px;
  font-size: 14px;
  color: #909399;
}
.profile-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.profile-title { font-size: 16px; font-weight: 600; color: #303133; }
.metric-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-top: 14px;
}
.metric {
  padding: 10px 4px;
  text-align: center;
  background: #f7f8fa;
  border-radius: 8px;
}
.metric-num { font-size: 20px; font-weight: 700; color: #409eff; line-height: 1.2; }
.metric-num.warn { color: #f56c6c; }
.metric-label { margin-top: 4px; font-size: 12px; color: #909399; }

/* 可点指标卡（错题待复习）：格子大小位置不变，悬停时整格浮出「点击练习」提示 */
.metric-action {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  transition: background-color 0.2s, box-shadow 0.2s;
}
.metric-action:not(.disabled):hover {
  background: #ecf5ff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.2);
}
.metric-action.disabled { cursor: not-allowed; }
.metric-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: rgba(64, 158, 255, 0.92);
  opacity: 0;
  transition: opacity 0.2s;
}
.metric-action:not(.disabled):hover .metric-mask { opacity: 1; }
.metric-action.disabled .metric-mask { background: rgba(144, 147, 153, 0.88); }
.metric-action.disabled:hover .metric-mask { opacity: 1; }
.profile-summary { margin-top: 10px; font-size: 13px; color: #909399; }
.profile-summary b { color: #303133; }

.block-label { margin: 16px 0 6px; font-size: 13px; font-weight: 600; color: #606266; }
.chart-box { width: 100%; height: 210px; }
.chart-box-sm { height: 120px; }

.kp-list { display: flex; flex-direction: column; gap: 8px; }
.kp-item { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.kp-name {
  width: 96px;
  flex-shrink: 0;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kp-bar { flex: 1; height: 8px; background: #f0f2f5; border-radius: 4px; overflow: hidden; }
.kp-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #79bbff, #409eff);
  border-radius: 4px;
}
.kp-count { width: 34px; flex-shrink: 0; text-align: right; color: #909399; }
.select-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.course-stats { font-size: 14px; color: #606266; white-space: nowrap; }
.course-stats b { color: #409eff; font-size: 16px; margin: 0 2px; }
.stat-sep { margin: 0 10px; color: #dcdfe6; }
.start-btn { margin-top: 18px; }

/* 学科下拉：选项显示名称+编码 */
.course-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.course-code {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

/* 刷题条件设置面板 */
.filter-group {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  background: #f7f8fa;
  border-radius: 8px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.filter-label {
  width: 56px;
  flex-shrink: 0;
  font-size: 14px;
  color: #606266;
}
.filter-box {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 8px;
}
.filter-hint {
  font-size: 13px;
  color: #909399;
}
.free-tip {
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

/* 进度 */
.progress-card { margin-bottom: 16px; }
.doing-header { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.doing-pos { font-size: 15px; color: #303133; white-space: nowrap; }
.doing-pos b { color: #409eff; font-size: 17px; }
.doing-nav { margin-right: auto; display: flex; gap: 10px; }
.doing-bar { flex: 1; min-width: 200px; }
.doing-correct { font-size: 14px; color: #606266; white-space: nowrap; }

.q-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 14px; }
.q-meta .q-fav { margin-left: auto; }
.question-stat { font-size: 13px; color: #909399; margin-bottom: 10px; }
.question-stat b { color: #409eff; font-size: 15px; margin: 0 2px; }
.q-content {
  font-size: 17px; font-weight: 600; color: #303133;
  line-height: 1.7; margin-bottom: 20px;
}
.opt-group {
  display: flex; flex-direction: column; align-items: flex-start;
  gap: 4px; width: 100%;
}
.opt-item { height: auto; padding: 6px 0; margin-right: 0; font-size: 15px; }
.blank-input { max-width: 420px; }

/* 做题中「答题 / 背题」切换条 */
.mode-switch {
  display: flex; align-items: center; flex-wrap: wrap; gap: 10px;
  margin: 14px 0 18px; padding: 10px 14px;
  background: #f7f9fc; border: 1px solid #eef1f6; border-radius: 8px;
}
.mode-label { font-size: 13px; color: #909399; }
.mode-hint { font-size: 12px; color: #c0c4cc; }

.result-banner {
  margin-top: 20px; padding: 12px 16px; border-radius: 6px;
  font-size: 15px; font-weight: 600;
}
.result-banner.ok { background: #f0f9eb; color: #67c23a; border: 1px solid #e1f3d8; }
.result-banner.bad { background: #fef0f0; color: #f56c6c; border: 1px solid #fde2e2; }
.result-banner.neutral { background: #fdf6ec; color: #e6a23c; border: 1px solid #faecd8; }
.result-banner.review { background: #f0f9eb; color: #529b2e; border: 1px solid #d6edc3; }

/* ===== 背题模式 ===== */
.review-opts {
  display: flex; flex-direction: column; gap: 8px; width: 100%;
}
.review-opt {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 10px 14px; border: 1px solid #e4e7ed; border-radius: 8px;
  background: #fafafa; font-size: 15px; line-height: 1.7; color: #303133;
}
.review-opt-key {
  flex-shrink: 0; width: 20px; height: 20px; margin-top: 1px;
  background: #409eff; color: #fff; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.review-opt-text { flex: 1; white-space: pre-wrap; }
.reveal-answer { font-size: 16px; }

/* ===== AI 智能讲解（背题模式，多轮对话） ===== */
.ai-explain {
  margin-top: 16px; border: 1px solid #e4e7ed; border-radius: 10px;
  overflow: hidden; background: #fff;
}
.ai-header {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px;
  background: linear-gradient(90deg, #f5f7ff, #f0f4ff); border-bottom: 1px solid #ebeef5;
}
.ai-title { font-size: 14px; font-weight: 700; color: #5b7cfa; }
.ai-tag {
  padding: 1px 8px; border-radius: 10px; font-size: 12px;
  background: #f0f2f5; color: #909399;
}
.ai-tag.llm { background: #ecf5ff; color: #409eff; }
.ai-tag.local { background: #fdf6ec; color: #e6a23c; }
.ai-restart { margin-left: auto; }
.chat-body {
  max-height: 340px; overflow-y: auto; padding: 12px 14px;
  background: #fafbfc;
}
.chat-empty { text-align: center; color: #909399; font-size: 13px; padding: 18px 8px; }
.chat-empty-sub { font-size: 12px; margin-top: 6px; color: #c0c4cc; }
.msg { display: flex; gap: 8px; margin-bottom: 12px; }
.msg.user { justify-content: flex-end; }
.msg-role {
  flex-shrink: 0; width: 30px; height: 30px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600; color: #fff;
}
.msg.user .msg-role { background: #409eff; order: 2; }
.msg.assistant .msg-role { background: #5b7cfa; }
.msg-bubble {
  max-width: 82%; padding: 10px 12px; border-radius: 8px; font-size: 14px;
  line-height: 1.7; white-space: pre-wrap; word-break: break-word; background: #fff;
  border: 1px solid #e4e7ed; color: #303133;
}
.msg.user .msg-bubble { background: #409eff; color: #fff; border: none; }
.msg-bubble.typing { color: #909399; }
.chat-input { padding: 10px 14px; border-top: 1px solid #ebeef5; background: #fff; }
.chat-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.chat-tip { margin-right: auto; font-size: 12px; color: #c0c4cc; }

/* AI 讲解 Markdown 基础样式 */
.markdown { white-space: normal; }
.markdown .md-h2 { font-size: 16px; margin: 6px 0 4px; color: #303133; }
.markdown .md-h3 { font-size: 15px; margin: 6px 0 4px; color: #303133; }
.markdown .md-h4 { font-size: 14px; margin: 6px 0 4px; color: #409eff; }
.markdown .md-li { margin: 2px 0 2px 16px; list-style: disc; }
.markdown code { background: #f2f4f7; padding: 1px 5px; border-radius: 4px; font-size: 13px; color: #c45656; }
.markdown pre.md-code { background: #f6f8fa; padding: 10px; border-radius: 6px; overflow-x: auto; font-size: 13px; line-height: 1.6; margin: 6px 0; }
.markdown strong { font-weight: 700; }

.ans-line, .analysis-line {
  margin-top: 10px; font-size: 14px; color: #303133; line-height: 1.7;
}
.analysis-line { background: #f5f7fa; padding: 10px 14px; border-radius: 6px; }
.ans-label { display: inline-block; min-width: 72px; color: #909399; }
.ans-ok { color: #67c23a; font-weight: 600; }
.ans-bad { color: #f56c6c; font-weight: 600; }
.actions { margin-top: 24px; display: flex; gap: 8px; flex-wrap: wrap; }

/* 统计卡 */
.summary-card { max-width: 860px; }
.summary-title {
  font-size: 20px; font-weight: 600; text-align: center;
  color: #303133; margin-bottom: 24px;
}
.summary-grid {
  display: flex; justify-content: space-around; margin-bottom: 20px;
}
.stat-item { text-align: center; }
.stat-num { font-size: 32px; font-weight: 700; color: #303133; }
.stat-item.ok .stat-num { color: #67c23a; }
.stat-item.bad .stat-num { color: #f56c6c; }
.stat-name { margin-top: 6px; font-size: 13px; color: #909399; }
.summary-actions { display: flex; justify-content: center; align-items: center; gap: 12px; flex-wrap: nowrap; margin-bottom: 22px; }

/* 结果页：错题汇总 */
.summary-sub {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #e4e7ed;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.wrong-item {
  background: #fdf6f6;
  border: 1px solid #fde2e2;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
}
.wrong-q {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
  margin-bottom: 8px;
}
.wrong-no {
  font-weight: 700;
  color: #f56c6c;
  margin-right: 4px;
}
.wrong-answer {
  font-size: 13px;
  color: #606266;
  line-height: 1.9;
}
.wrong-analysis {
  margin-top: 6px;
  padding-top: 8px;
  border-top: 1px dashed #fde2e2;
  font-size: 13px;
  color: #909399;
  line-height: 1.7;
}
</style>
