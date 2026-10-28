<template>
  <div class="practice-paper">
    <div class="page-title">
      <h2>作业列表</h2>
      <span class="tip">查看教师发布的作业，点击开始做题；标记「附件作业」的先下载作业文件、线下作答，完成后点「提交」回传作业文件</span>
      <!-- 结果页的操作按钮：统一放到页面顶部，长列表也不用往下翻 -->
      <div v-if="paperState === 'result' && paperResult" class="top-actions">
        <el-button
          type="primary"
          :title="`重新做一遍整张卷子《${currentPaper?.paperName || ''}》`"
          @click="restartSamePaper"
        >再做一次</el-button>
        <el-button @click="backToPaperList">返回作业列表</el-button>
        <el-button type="warning" @click="$router.push('/student/wrong-questions')">去看错题本</el-button>
      </div>
    </div>

    <!-- 子状态机：list（列表）| doing（整套答题）| result（整套结果） -->
    <template v-if="paperState === 'list'">
      <el-card shadow="never">
        <div class="paper-filter">
          <el-select
            v-model="paperCourseId"
            placeholder="全部学科（可搜索）"
            clearable
            filterable
            remote
            :remote-method="paperRemoteSearch"
            :loading="paperCourseLoading"
            style="width: 220px"
            @change="handlePaperCourseChange"
            @clear="handlePaperCourseChange(null)"
            @visible-change="handlePaperVisible"
          >
            <el-option
              v-for="c in paperCourseViewOptions"
              :key="c.id"
              :label="courseLabel(c)"
              :value="c.id"
            />
          </el-select>
          <el-button type="primary" plain @click="loadPapers">刷新</el-button>
          <span v-if="paperList.length === 0" class="empty-tip">
            暂无已发布的作业，请等待教师发布
          </span>
        </div>
        <el-table v-if="paperList.length > 0" :data="paperList" stripe border class="my-paper-table" style="margin-top: 12px">
          <!-- 序号：第一列；翻页后跨页连续（第 2 页从 11 开始），不是 Element 默认的每页从 1 开始 -->
          <el-table-column label="序号" width="70" align="center">
            <template #default="{ $index }">
              {{ (paperPageNum - 1) * paperPageSize + $index + 1 }}
            </template>
          </el-table-column>
          <el-table-column label="作业名称" min-width="240">
            <template #default="{ row }">
              <div class="paper-name">{{ row.paperName }}</div>
              <div v-if="row.description" class="paper-desc">{{ row.description }}</div>
            </template>
          </el-table-column>
          <!-- 作业类型：附件作业 = 下载/在线查看文件后线下作答；在线作业 = 从题库选题在线判分 -->
          <el-table-column label="作业类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="isAttachmentPaper(row) ? 'warning' : 'primary'" effect="plain">
                {{ isAttachmentPaper(row) ? '附件作业' : '在线作业' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="courseName" label="学科" width="140" />
          <el-table-column prop="questionCount" label="题目数" width="90" align="center">
            <template #default="{ row }">
              <span v-if="isAttachmentPaper(row)" class="zero">—</span>
              <el-tag v-else size="small" type="primary" effect="plain">{{ row.questionCount || 0 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="作业文件" min-width="180">
            <template #default="{ row }">
              <el-link v-if="isAttachmentPaper(row)" type="primary" :href="row.attachmentPath" target="_blank">
                {{ row.attachmentName || '下载' }}
              </el-link>
              <span v-else class="zero">—</span>
            </template>
          </el-table-column>
          <el-table-column label="总分" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.totalScore != null">{{ row.totalScore }}</span>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTeacherName" label="创建教师" width="120" />
          <!-- 在线作业显示练习次数；附件作业显示「我提交了吗 / 批改了吗」（悬浮看提交详情与教师评语） -->
          <el-table-column label="我的练习/提交" width="130" align="center">
            <template #default="{ row }">
              <el-tooltip v-if="isAttachmentPaper(row)" placement="top" effect="dark" :disabled="!row.mySubmitId">
                <template #content>
                  <div>提交文件：{{ row.mySubmitAttachmentName || '-' }}</div>
                  <div>提交时间：{{ row.mySubmitTime || '-' }}</div>
                  <div v-if="row.mySubmitRemark">我的备注：{{ row.mySubmitRemark }}</div>
                  <div v-if="row.mySubmitStatus === 2">教师评分：{{ row.mySubmitScore }} 分</div>
                  <div v-if="row.mySubmitStatus === 2 && row.mySubmitOpinion">教师评语：{{ row.mySubmitOpinion }}</div>
                </template>
                <el-tag size="small" :type="submitStatusType(row)" effect="plain">
                  {{ submitStatusText(row) }}
                </el-tag>
              </el-tooltip>
              <template v-else>
                <span v-if="row.myPracticeCount > 0" class="my-count">{{ row.myPracticeCount }} 次</span>
                <span v-else class="zero">未练过</span>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <div class="op-cell">
                <!-- 在线作业：有题目 → 在线作答 -->
                <el-button v-if="row.questionCount > 0" type="primary" link @click="startPaperPractice(row)">
                  开始做题
                </el-button>
                <!-- 附件作业：下载作业文件 → 线下作答 → 提交自己的作业文件 -->
                <template v-else-if="isAttachmentPaper(row)">
                  <el-link type="primary" :href="row.attachmentPath" target="_blank">下载</el-link>
                  <el-button type="primary" link @click="openSubmitDialog(row)">
                    {{ row.mySubmitId ? '重新提交' : '提交' }}
                  </el-button>
                </template>
                <span v-else class="zero">—</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="paperTotal > 0" class="pagination-wrap">
          <el-pagination
            v-model:current-page="paperPageNum"
            v-model:page-size="paperPageSize"
            :total="paperTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handlePaperSizeChange"
            @current-change="loadPapers"
          />
        </div>
      </el-card>

      <!-- 文件型作业：提交作业文件（下载 → 线下作答 → 回传；同一作业重复提交覆盖上一版） -->
      <el-dialog
        v-model="submitVisible"
        :title="`提交作业 · ${submitPaper?.paperName || ''}`"
        width="540px"
        @closed="resetSubmitForm"
      >
        <div v-if="submitPaper" class="submit-body">
          <div class="submit-row">
            <span class="submit-label">作业文件：</span>
            <el-link type="primary" :href="submitPaper.attachmentPath" target="_blank">
              {{ submitPaper.attachmentName || '下载' }}
            </el-link>
          </div>

          <!-- 已提交过：展示上一版提交与教师批改结果，并提示会被覆盖 -->
          <div v-if="submitPaper.mySubmitId" class="submit-last">
            <div class="last-title">
              <span>上次提交</span>
              <el-tag size="small" :type="submitStatusType(submitPaper)" effect="plain">
                {{ submitStatusText(submitPaper) }}
              </el-tag>
            </div>
            <div class="submit-row">
              <span class="submit-label">文件：</span>{{ submitPaper.mySubmitAttachmentName || '-' }}
            </div>
            <div class="submit-row">
              <span class="submit-label">时间：</span>{{ submitPaper.mySubmitTime || '-' }}
            </div>
            <div v-if="submitPaper.mySubmitRemark" class="submit-row">
              <span class="submit-label">备注：</span>{{ submitPaper.mySubmitRemark }}
            </div>
            <div v-if="submitPaper.mySubmitStatus === 2" class="submit-row">
              <span class="submit-label">批改：</span>
              <b class="last-score">{{ submitPaper.mySubmitScore }} 分</b>
              <span v-if="submitPaper.mySubmitOpinion" class="last-opinion">
                评语：{{ submitPaper.mySubmitOpinion }}
              </span>
            </div>
            <div class="submit-tip">重新提交会覆盖上一次的作业文件，并重新进入教师的待批改列表。</div>
          </div>

          <el-upload
            class="submit-upload"
            drag
            :auto-upload="false"
            :limit="1"
            :accept="SUBMIT_ACCEPT"
            :file-list="submitFileList"
            :on-change="handleSubmitFileChange"
            :on-exceed="handleSubmitExceed"
            :on-remove="handleSubmitFileRemove"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">把作业文件拖到这里，或 <em>点击选择文件</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 pdf/word/ppt/excel/txt/zip/rar/图片，单个文件不超过 300MB</div>
            </template>
          </el-upload>

          <el-input
            v-model="submitRemark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="提交备注（选填，例如：已完成全部习题，第 5 题不太确定）"
            style="margin-top: 10px"
          />
        </div>
        <template #footer>
          <el-button @click="submitVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="doSubmitAttachment">
            {{ submitPaper?.mySubmitId ? '确认重新提交' : '确认提交' }}
          </el-button>
        </template>
      </el-dialog>
    </template>

    <template v-else-if="paperState === 'doing'">
      <el-card shadow="never" class="progress-card">
        <div class="doing-header">
          <el-tag type="warning" effect="dark">{{ currentPaper?.paperName }}</el-tag>
          <span class="doing-pos">第 <b>{{ paperIndex + 1 }}</b> / {{ paperQuestions.length }} 题</span>
          <el-progress
            class="doing-bar"
            :percentage="Math.round(((paperIndex + 1) / paperQuestions.length) * 100)"
            :stroke-width="10"
          />
          <span class="paper-saved">已暂存 {{ paperSavedCount }}/{{ paperQuestions.length }}</span>
        </div>
      </el-card>
      <el-card shadow="never" class="q-card">
        <div class="q-meta">
          <el-tag>{{ typeText(currentPaperQ.questionType) }}</el-tag>
          <el-tag type="warning" effect="plain">分值 {{ currentPaperQ.score }}</el-tag>
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
        <div class="q-content">{{ currentPaperQ.questionContent }}</div>

        <el-radio-group
          v-if="currentPaperQ.questionType === 1"
          v-model="paperAnswers[currentPaperQ.questionId]"
          class="opt-group"
        >
          <el-radio
            v-for="(o, i) in paperOptionList"
            :key="i"
            :value="letter(i)"
            class="opt-item"
          >{{ letter(i) }}. {{ o }}</el-radio>
        </el-radio-group>

        <el-checkbox-group
          v-else-if="currentPaperQ.questionType === 2"
          v-model="paperMultiAnswers[currentPaperQ.questionId]"
          class="opt-group"
        >
          <el-checkbox
            v-for="(o, i) in paperOptionList"
            :key="i"
            :value="letter(i)"
            class="opt-item"
          >{{ letter(i) }}. {{ o }}</el-checkbox>
        </el-checkbox-group>

        <el-radio-group
          v-else-if="currentPaperQ.questionType === 3"
          v-model="paperAnswers[currentPaperQ.questionId]"
          class="opt-group"
        >
          <el-radio value="T" class="opt-item">正确（T）</el-radio>
          <el-radio value="F" class="opt-item">错误（F）</el-radio>
        </el-radio-group>

        <el-input
          v-else-if="currentPaperQ.questionType === 4"
          v-model="paperAnswers[currentPaperQ.questionId]"
          placeholder="请输入你的答案"
          class="blank-input"
        />

        <el-input
          v-else
          v-model="paperAnswers[currentPaperQ.questionId]"
          type="textarea"
          :rows="4"
          placeholder="请输入你的答案"
        />

        <div class="actions">
          <el-button
            v-if="paperIndex > 0"
            @click="paperIndex--"
          >上一题</el-button>
          <el-button
            v-if="paperIndex < paperQuestions.length - 1"
            type="primary"
            @click="paperIndex++"
          >下一题</el-button>
          <el-button
            v-else
            type="success"
            :disabled="paperSavedCount < paperQuestions.length"
            @click="submitWholePaper"
          >
            {{ paperSavedCount < paperQuestions.length ? `还剩 ${paperQuestions.length - paperSavedCount} 题未作答` : '提交整套' }}
          </el-button>
          <el-button @click="quitPaperPractice">退出练习</el-button>
        </div>
      </el-card>
    </template>

    <template v-else-if="paperState === 'result' && paperResult">
      <el-card shadow="never" class="summary-card">
        <div class="summary-title">🎉 《{{ currentPaper?.paperName }}》提交完成！</div>
        <div class="summary-grid">
          <div class="stat-item">
            <div class="stat-num">{{ paperResult.totalScore }}</div>
            <div class="stat-name">满分</div>
          </div>
          <div class="stat-item ok">
            <div class="stat-num">{{ paperResult.score }}</div>
            <div class="stat-name">得分</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ paperResult.correctCount }}</div>
            <div class="stat-name">答对</div>
          </div>
          <div class="stat-item bad">
            <div class="stat-num">{{ paperResult.wrongCount }}</div>
            <div class="stat-name">答错</div>
          </div>
        </div>
        <div v-if="paperResult.newlyCollected > 0" class="collect-tip">
          ✅ 本次答错的 <b>{{ paperResult.newlyCollected }}</b> 道客观题已自动收录进「我的错题」
        </div>
      </el-card>

      <el-card shadow="never" style="margin-top: 16px">
        <template #header>答题明细（点击展开/收起）</template>
        <el-collapse v-model="paperResultOpen" class="detail-collapse">
          <el-collapse-item
            v-for="(d, i) in paperResult.details"
            :key="d.questionId"
            :title="`第 ${i + 1} 题 · ${typeText(d.questionType)} · 分值 ${d.score}`"
            :name="i"
          >
            <div class="detail-q">{{ d.questionContent }}</div>
            <div class="ans-line">
              <span class="ans-label">我的答案：</span>
              <span :class="d.isCorrect ? 'ans-ok' : (d.questionType === 5 || d.questionType === 6 ? '' : 'ans-bad')">
                {{ d.userAnswer || '（未作答）' }}
              </span>
            </div>
            <div v-if="d.questionType !== 5 && d.questionType !== 6" class="ans-line">
              <span class="ans-label">正确答案：</span>
              <span class="ans-ok">{{ d.correctAnswer }}</span>
            </div>
            <div v-if="d.analysis" class="analysis-line">
              <span class="ans-label">解析：</span>{{ d.analysis }}
            </div>
            <div class="ans-line" style="margin-top: 8px">
              <span class="ans-label">本题得分：</span>
              <span :class="d.isCorrect ? 'ans-ok' : 'ans-bad'">{{ d.getScore }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
        <div class="bottom-tip">📌 再做一次 / 返回作业列表 / 去看错题本 等操作在页面顶部</div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, StarFilled, UploadFilled } from '@element-plus/icons-vue'
import {
  getPublishedPracticePapers, getPracticePaperDetail, submitPracticePaper,
  submitPaperAttachment
} from '@/api/studentPractice'
import { getJoinedCourses } from '@/api/course'
import { getFavoriteQuestionIds, addFavorite, removeFavoriteByQuestion } from '@/api/studentFavorite'

const TYPE_MAP = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
const typeText = (t) => TYPE_MAP[t] || '未知'
const letter = (i) => String.fromCharCode(65 + i)

/** 是否附件型作业：有附件即为文件型（该类型不排在线题目，学生下载后线下作答） */
const isAttachmentPaper = (row) => !!row?.attachmentPath

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
const paperCourseSearch = createCourseRemoteSearch()
const paperCourseLoading = paperCourseSearch.loading
const paperCourseViewOptions = paperCourseSearch.viewOptions
function paperRemoteSearch(kw) { paperCourseSearch.search(kw) }
function handlePaperCourseChange(val) {
  paperCourseSearch.onSelect(val)
  paperPageNum.value = 1
  loadPapers()
}
function handlePaperVisible(visible) {
  if (visible && paperCourseSearch.options.value.length === 0) {
    paperCourseSearch.search('') // 首次展开时拉默认列表
  }
}

// ========== 作业（原练习卷）：列表 / 答题 / 结果 ==========
const paperState = ref('list') // list | doing | result
const paperList = ref([])
const paperCourseId = ref(null)
const paperPageNum = ref(1)
const paperPageSize = ref(10)
const paperTotal = ref(0)
const currentPaper = ref(null)
const paperQuestions = ref([])
const paperIndex = ref(0)
const paperAnswers = reactive({})       // questionId -> userAnswer（单选/判断/填空/简答）
const paperMultiAnswers = reactive({})  // questionId -> [userAnswer]（多选）
const paperResult = ref(null)
const paperResultOpen = ref([])

/** 清空暂存答案 */
function clearPaperAnswers() {
  Object.keys(paperAnswers).forEach((k) => delete paperAnswers[k])
  Object.keys(paperMultiAnswers).forEach((k) => delete paperMultiAnswers[k])
}

// ---------- 收藏：把重点题标记进「我的收藏」（与错题本并列的另一套复习体系）----------
/** 当前学生已收藏的试题 ID 列表（本卷题目上显示「已收藏」状态） */
const favIds = ref([])

/** 加载已收藏题目 ID（每次开始做卷时拉一次，收藏量级小，一次取全量更省往返） */
async function loadFavIds() {
  try {
    const res = await getFavoriteQuestionIds()
    favIds.value = res.data || []
  } catch {
    /* 收藏状态加载失败不影响做题，保持空列表即可 */
  }
}

/** 收藏 / 取消收藏当前题目（收藏为幂等操作，重复点击不会报错） */
async function toggleFavorite() {
  const q = currentPaperQ.value
  if (!q) return
  try {
    if (favIds.value.includes(q.questionId)) {
      await removeFavoriteByQuestion(q.questionId)
      favIds.value = favIds.value.filter((id) => id !== q.questionId)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(q.questionId)
      favIds.value = [...favIds.value, q.questionId]
      ElMessage.success('已加入「我的收藏」，可在侧边「我的收藏」中集中复习')
    }
  } catch (e) {
    ElMessage.error(e?.msg || e?.message || '操作失败')
  }
}

/** 进入答题状态（题序归零 + 清空暂存答案） */
function beginPaperPractice(list) {
  paperQuestions.value = list
  paperIndex.value = 0
  clearPaperAnswers()
  paperResult.value = null
  paperResultOpen.value = []
  loadFavIds() // 同步本卷题目的收藏状态
  paperState.value = 'doing'
}

async function loadPapers() {
  try {
    const res = await getPublishedPracticePapers({
      courseId: paperCourseId.value || undefined,
      pageNum: paperPageNum.value,
      pageSize: paperPageSize.value
    })
    paperList.value = res.data?.records || []
    paperTotal.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error('加载作业失败')
  }
}

function handlePaperSizeChange() {
  paperPageNum.value = 1
  loadPapers()
}

const currentPaperQ = computed(() => paperQuestions.value[paperIndex.value] || {})

/** 当前题是否已收藏（题目卡片上的收藏按钮状态） */
const isCurrentFav = computed(() => !!currentPaperQ.value && favIds.value.includes(currentPaperQ.value.questionId))

const paperOptionList = computed(() => {
  const opts = currentPaperQ.value?.options
  if (!opts) return []
  try {
    const arr = typeof opts === 'string' ? JSON.parse(opts) : opts
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
})

// 统计已暂存的题数（paperAnswers 或 paperMultiAnswers 有值即算已作答）
const paperSavedCount = computed(() => {
  let n = 0
  for (const q of paperQuestions.value) {
    const qid = q.questionId
    if (q.questionType === 2) {
      const arr = paperMultiAnswers[qid]
      if (Array.isArray(arr) && arr.length > 0) n++
    } else {
      const v = paperAnswers[qid]
      if (v && String(v).trim() !== '') n++
    }
  }
  return n
})

// ---------- 文件型作业：提交作业文件 ----------
/** 提交文件类型白名单（与后端 ALLOWED_ATTACHMENT_EXT 保持一致） */
const SUBMIT_ACCEPT = '.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md,.zip,.rar,.7z,.jpg,.jpeg,.png,.gif,.webp'

const submitVisible = ref(false)
const submitPaper = ref(null)
const submitFile = ref(null)
const submitFileList = ref([])
const submitRemark = ref('')
const submitting = ref(false)

/** 提交状态文案 / 标签色：0未提交 1待批改 2已批改 */
const submitStatusText = (row) => {
  if (!row?.mySubmitId) return '未提交'
  return row.mySubmitStatus === 2 ? '已批改' : '待批改'
}
const submitStatusType = (row) => {
  if (!row?.mySubmitId) return 'info'
  return row.mySubmitStatus === 2 ? 'success' : 'warning'
}

/** 打开提交弹窗（回显上次提交与批改结果） */
function openSubmitDialog(row) {
  submitPaper.value = row
  submitFile.value = null
  submitFileList.value = []
  submitRemark.value = ''
  submitVisible.value = true
}

/** 弹窗关闭后清空选择，避免下次打开残留上一个文件 */
function resetSubmitForm() {
  submitFile.value = null
  submitFileList.value = []
  submitRemark.value = ''
}

function handleSubmitFileChange(file) {
  submitFile.value = file?.raw || null
  submitFileList.value = file?.raw ? [file] : []
}

/** 已选过文件再选一个：直接替换（limit=1 时 el-upload 走这里） */
function handleSubmitExceed(files) {
  const raw = files && files[0]
  if (!raw) return
  submitFile.value = raw
  submitFileList.value = [{ name: raw.name, raw, uid: Date.now() }]
}

function handleSubmitFileRemove() {
  submitFile.value = null
  submitFileList.value = []
}

/** 提交作业文件到后端（成功后刷新列表，状态变为「待批改」） */
async function doSubmitAttachment() {
  if (!submitFile.value) {
    ElMessage.warning('请先选择要提交的作业文件')
    return
  }
  submitting.value = true
  try {
    await submitPaperAttachment(submitPaper.value.id, submitFile.value, submitRemark.value)
    ElMessage.success('提交成功，等待教师批改')
    submitVisible.value = false
    loadPapers()
  } catch (e) {
    ElMessage.error(e?.msg || e?.message || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function startPaperPractice(row) {
  try {
    const res = await getPracticePaperDetail(row.id)
    const list = res.data || []
    if (!list.length) {
      ElMessage.warning('该作业暂无题目')
      return false
    }
    currentPaper.value = row
    beginPaperPractice(list)
    return true
  } catch (e) {
    ElMessage.error(e?.message || '加载作业失败')
    return false
  }
}

/** 返回作业列表：回到列表 */
function backToPaperList() {
  paperState.value = 'list'
  paperResult.value = null
  paperQuestions.value = []
  loadPapers()
}

async function submitWholePaper() {
  // 把 reactive 对象转换成后端需要的 answers 数组
  const answers = paperQuestions.value.map((q) => {
    const qid = q.questionId
    let ua
    if (q.questionType === 2) {
      const arr = paperMultiAnswers[qid] || []
      ua = arr.slice().sort().join(',')
    } else {
      ua = paperAnswers[qid] || ''
    }
    return { questionId: qid, userAnswer: ua }
  })
  try {
    const res = await submitPracticePaper(currentPaper.value.id, answers)
    paperResult.value = res.data
    // 默认展开全部
    paperResultOpen.value = paperQuestions.value.map((_, i) => i)
    paperState.value = 'result'
    ElMessage.success('提交成功')
  } catch (e) {
    ElMessage.error(e?.message || '提交失败')
  }
}

function quitPaperPractice() {
  if (confirm('确定退出吗？已作答的内容不会被保存。')) {
    paperState.value = 'list'
    currentPaper.value = null
    paperQuestions.value = []
  }
}

/** 「再做一次」：重新拉取整张卷子，题序归零、作答清空，从第一题重新开始 */
async function restartSamePaper() {
  const ok = await startPaperPractice(currentPaper.value)
  if (ok) ElMessage.info('已重新开始整份作业，作答已清空')
}

onMounted(loadPapers)
</script>

<style scoped>
.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
/* 结果页顶部操作区 */
.top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.top-actions .el-button + .el-button { margin-left: 0; }
.bottom-tip {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
.page-title h2 { margin: 0; font-size: 20px; color: #303133; }
.tip { font-size: 13px; color: #909399; }

/* 筛选栏 */
.paper-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.empty-tip { color: #c0c4cc; font-size: 13px; margin-left: 10px; }
.paper-name { font-weight: 600; color: #303133; }
.paper-desc { font-size: 12px; color: #909399; margin-top: 4px; }

/* 学生端练习次数标签 */
.my-count { color: #409eff; font-weight: 600; }
.zero { color: #c0c4cc; font-size: 13px; }

/* 操作列：按钮 / 链接统一水平居中 */
.op-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

/* 提交作业弹窗 */
.submit-body { font-size: 14px; color: #303133; }
.submit-row { margin-bottom: 8px; line-height: 1.7; }
.submit-label { display: inline-block; min-width: 68px; color: #909399; }
.submit-last {
  margin: 12px 0;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 6px;
  border-left: 3px solid #e6a23c;
}
.last-title {
  display: flex; align-items: center; gap: 8px;
  font-weight: 600; color: #303133; margin-bottom: 6px;
}
.last-score { color: #67c23a; margin-right: 10px; }
.last-opinion { color: #606266; font-size: 13px; }
.submit-tip { margin-top: 6px; font-size: 12px; color: #e6a23c; }
.submit-upload { margin-top: 4px; }
.submit-upload :deep(.el-upload-dragger) { padding: 18px 10px; }

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

/* 练习卷表格：表头与内容统一居中 */
.my-paper-table :deep(.el-table__header th.el-table__cell),
.my-paper-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

/* 进度 */
.progress-card { margin-bottom: 16px; }
.doing-header { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.doing-pos { font-size: 15px; color: #303133; white-space: nowrap; }
.doing-pos b { color: #409eff; font-size: 17px; }
.doing-bar { flex: 1; min-width: 200px; }
.paper-saved { font-size: 13px; color: #67c23a; white-space: nowrap; }

.q-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 14px; }
.q-meta .q-fav { margin-left: auto; }
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

.collect-tip {
  margin-bottom: 14px; padding: 10px 14px;
  background: #f0f9eb; color: #67c23a; border-radius: 6px; font-size: 14px;
}

/* 答题明细折叠 */
.detail-collapse { margin-top: 4px; }
.detail-q {
  font-weight: 600; color: #303133; line-height: 1.6;
  margin-bottom: 10px; padding: 10px 12px; background: #f8fafc;
  border-radius: 6px; font-size: 14px;
}
</style>
