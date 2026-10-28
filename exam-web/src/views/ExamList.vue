<template>
  <div class="exam-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query">
        <el-form-item label="考试名称">
          <el-input
            v-model="query.keyword"
            placeholder="名称模糊搜索"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <!-- 嵌入「课程信息 → 课程考试」抽屉时，学科由父组件锁定，不再给下拉 -->
        <el-form-item label="学科" v-if="!embedded">
          <el-select v-model="query.courseId" placeholder="全部学科" clearable style="width: 150px" @change="handleSearch">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="query.runtimeStatus" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
            <el-option label="已撤回" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">发布考试</el-button>
        <!-- 批量操作采用「先进入勾选模式」：默认不显示勾选框，点按钮后才出现，避免误勾 -->
        <template v-if="!batchMode">
          <el-button type="success" :icon="Select" @click="enterBatchMode('publish')">批量发布</el-button>
          <el-button type="danger" plain :icon="Delete" @click="enterBatchMode('delete')">批量删除</el-button>
        </template>
        <template v-else>
          <el-button :icon="Close" @click="exitBatchMode">
            退出批量{{ batchAction === 'publish' ? '发布' : '删除' }}
          </el-button>
          <el-button
            v-if="batchAction === 'publish'"
            type="success"
            :disabled="!selectedRows.length"
            :loading="batchPublishing"
            @click="handleBatchPublish"
          >确认发布{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}</el-button>
          <el-button
            v-else
            type="danger"
            :disabled="!selectedRows.length"
            :loading="batchDeleting"
            @click="handleBatchDelete"
          >确认删除{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}</el-button>
          <span class="toolbar-tip" :class="batchAction === 'delete' ? 'hint-danger' : 'hint-success'">
            已进入勾选模式：勾选考试后点「确认{{ batchAction === 'publish' ? '发布' : '删除' }}」
            <template v-if="selectedRows.length">，已勾选 {{ selectedRows.length }} 场</template>
          </span>
        </template>
      </div>

      <el-table ref="tableRef" :data="tableData" v-loading="loading" border style="width: 100%" :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }" @selection-change="onSelectionChange">
        <!-- 勾选列仅在批量模式下渲染，默认不显示 -->
        <el-table-column v-if="batchMode" type="selection" width="45" align="center" />
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (query.pageNum - 1) * query.pageSize + $index + 1 }}
          </template>
        </el-table-column>

        <el-table-column prop="examName" label="考试名称" min-width="180" show-overflow-tooltip />

        <el-table-column prop="courseName" label="学科" width="130">
          <template #default="{ row }">{{ row.courseName || '-' }}</template>
        </el-table-column>

        <el-table-column prop="paperName" label="试卷" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.paperName || '-' }}
            <el-tag v-if="row.questionCount != null" size="small" type="info" style="margin-left: 4px">
              {{ row.questionCount }} 题
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="runtimeStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.runtimeStatus)" :effect="row.runtimeStatus === 1 ? 'dark' : 'plain'">
              {{ statusText(row.runtimeStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="totalScore" label="总分" width="70" align="center" />

        <el-table-column prop="duration" label="时长" width="80" align="center">
          <template #default="{ row }">{{ row.duration }} 分钟</template>
        </el-table-column>

        <el-table-column prop="startTime" label="开始时间" width="190" align="center" show-overflow-tooltip />
        <el-table-column prop="endTime" label="结束时间" width="190" align="center" show-overflow-tooltip />

        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <!-- 已撤回 → 重新发布（直接发布）；已结束 → 重新发布（设置时长）；其他 → 撤回 -->
            <el-button
              v-if="row.runtimeStatus === 3"
              type="success"
              link
              size="small"
              @click="handleToggleStatus(row, 1)"
            >重新发布</el-button>
            <el-button
              v-else-if="row.runtimeStatus === 2"
              type="success"
              link
              size="small"
              @click="openRepublish(row)"
            >重新发布</el-button>
            <el-button
              v-else
              type="warning"
              link
              size="small"
              :disabled="row.runtimeStatus === 2"
              @click="handleToggleStatus(row, 0)"
            >撤回</el-button>
            <!-- 仅进行中的考试可提前结束 -->
            <el-button
              v-if="row.runtimeStatus === 1"
              type="danger"
              link
              size="small"
              @click="handleFinish(row)"
            >提前结束</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[5, 10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 发布/编辑考试对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑考试' : '发布考试'"
      width="600px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="考试名称" prop="examName">
          <el-input
            v-model="form.examName"
            placeholder="选择试卷后自动填充，可修改"
            maxlength="100"
            @input="examNameAuto = false"
          />
          <div v-if="examNameAuto && form.examName" class="name-hint">
            已默认取所选试卷名称，可直接修改
          </div>
        </el-form-item>

        <el-form-item label="选择试卷" prop="paperId">
          <el-select
            v-model="form.paperId"
            placeholder="请选择试卷（仅限已发布且已组卷的）"
            style="width: 100%"
            filterable
            @change="handlePaperChange"
          >
            <el-option
              v-for="p in paperList"
              :key="p.id"
              :label="`${p.paperName}（${p.totalScore}分 / ${p.questionCount}题 / 已发布）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="时间设置" required>
          <el-radio-group v-model="form.scheduleMode" style="margin-right: 16px">
            <el-radio-button value="window">时间窗口</el-radio-button>
            <el-radio-button value="days">发布天数</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.scheduleMode === 'window'" label="时间窗口" required>
          <el-col :span="11">
            <el-form-item prop="startTime">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                placeholder="开始时间"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="2" class="time-sep">至</el-col>
          <el-col :span="11">
            <el-form-item prop="endTime">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                placeholder="结束时间"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-form-item>

        <el-form-item v-else label="发布天数" required>
          <div style="display: flex; align-items: center; gap: 8px">
            <el-input-number v-model="form.publishDays" :min="1" :max="365" :precision="0" controls-position="right" />
            <span>天</span>
          </div>
          <div class="days-tip">考试将立即开始，结束时间 = 发布时刻 + N 天</div>
        </el-form-item>

        <el-form-item label="考试时长" prop="duration">
          <el-input-number v-model="form.duration" :min="10" :max="300" :step="10" />
          <span class="unit">分钟（默认取试卷时长，可改）</span>
        </el-form-item>

        <el-alert
          title="总分 / 及格分自动从所选试卷快照，无需填写"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '发布' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 重新发布（已结束的考试）：发布天数 / 自定义时间窗口 -->
    <el-dialog v-model="republishVisible" title="重新发布考试" width="520px">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="考试名称">{{ republishRow?.examName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原结束时间">{{ republishRow?.endTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="时间设置" required>
          <el-radio-group v-model="republishMode" style="margin-right: 16px">
            <el-radio-button value="window">时间窗口</el-radio-button>
            <el-radio-button value="days">发布天数</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="republishMode === 'window'" label="时间窗口" required>
          <el-col :span="11">
            <el-date-picker
              v-model="republishForm.startTime"
              type="datetime"
              placeholder="开始时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-col>
          <el-col :span="2" class="time-sep">至</el-col>
          <el-col :span="11">
            <el-date-picker
              v-model="republishForm.endTime"
              type="datetime"
              placeholder="结束时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-col>
        </el-form-item>

        <el-form-item v-else label="发布天数" required>
          <div style="display: flex; align-items: center; gap: 8px">
            <el-input-number v-model="republishDays" :min="1" :max="365" :precision="0" controls-position="right" />
            <span>天</span>
          </div>
          <div class="days-tip">结束时间 = 当前时刻 + N 天，开始时间保持不变</div>
        </el-form-item>
      </el-form>
      <el-alert
        title="重新发布后考试立即恢复进行，已交卷学生的成绩不受影响"
        type="info"
        :closable="false"
        show-icon
      />
      <template #footer>
        <el-button @click="republishVisible = false">取消</el-button>
        <el-button type="primary" :loading="republishSubmitting" @click="submitRepublish">确定重新发布</el-button>
      </template>
    </el-dialog>

    <!-- 查看：考试信息 + 试卷内容（题目列表） -->
    <el-dialog v-model="viewVisible" title="查看考试" width="760px" top="4vh" destroy-on-close>
      <div v-loading="viewLoading">
        <el-descriptions v-if="viewExam" :column="2" border size="small">
          <el-descriptions-item label="考试名称" :span="2">{{ viewExam.examName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(viewExam.runtimeStatus) }}</el-descriptions-item>
          <el-descriptions-item label="考试时长">{{ viewExam.duration }} 分钟</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ viewExam.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ viewExam.endTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="viewPaper" class="preview-box" style="margin-top: 16px">
          <div class="preview-header">
            <h3>{{ viewPaper.paperName }}</h3>
            <div class="preview-meta">
              <span>题目数：{{ viewPaper.questionCount || viewPaper.questions?.length || 0 }}</span>
              <span>总分：{{ viewPaper.totalScore || 0 }} 分</span>
            </div>
            <p v-if="viewPaper.description" class="preview-desc">{{ viewPaper.description }}</p>
          </div>
          <div class="preview-body" v-if="viewPaper.questions?.length">
            <div v-for="(q, idx) in viewPaper.questions" :key="idx" class="preview-q">
              <div class="q-title">
                <span class="q-index">{{ idx + 1 }}</span>
                <el-tag size="small" effect="plain">{{ questionTypeText(q.questionType) }}</el-tag>
                <span class="q-score">（{{ q.score }}分）</span>
              </div>
              <div class="q-content">{{ q.questionContent }}</div>
              <div v-if="q.options" class="q-options">
                <div v-for="(opt, oi) in splitOptions(q.options)" :key="oi">
                  {{ letter(oi) }}. {{ opt }}
                </div>
              </div>
              <div v-if="q.answer" class="q-answer">参考答案：{{ q.answer }}</div>
            </div>
          </div>
          <el-empty v-else description="该试卷暂无题目" :image-size="80" />
        </div>
      </div>
      <template #footer>
        <el-button @click="viewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Refresh, Plus, Delete, Close, Select } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElInputNumber } from 'element-plus'
import { h } from 'vue'
import { getExamPage, getExamById, createExam, updateExam, updateExamStatus, finishExam, republishExam, deleteExam } from '@/api/exam'
import { getPaperList, getPaperDetail } from '@/api/examPaper'
import { getCourseList } from '@/api/course'
import { useDefaultPageSize } from '@/composables/useDefaultPageSize'

const route = useRoute()

/**
 * 两种复用入口：
 * 1) 独立页面（/exams）：学科从路由 ?courseId= 带入，用户可自行切换筛选；
 * 2) 嵌入「课程信息 → 课程考试」抽屉：由父组件传入 courseId 锁定本课程（隐藏学科下拉）。
 */
const props = defineProps({
  courseId: { type: [Number, String], default: null },
  embedded: { type: Boolean, default: false }
})

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  runtimeStatus: null,
  courseId: null
})

// 试卷下拉数据
const paperList = ref([])

// 学科下拉数据
const courses = ref([])
async function fetchCourses() {
  try {
    const res = await getCourseList()
    courses.value = res.data || []
  } catch (err) {
    console.error('获取学科列表失败:', err)
  }
}

// 对话框状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()

// 考试名称是否仍处于「跟随试卷名称」的自动状态：用户一旦手动输入过就不再被试卷覆盖
const examNameAuto = ref(true)

const form = reactive({
  id: null,
  examName: '',
  paperId: null,
  startTime: null,
  endTime: null,
  duration: 60,
  // 时间设置模式：window=时间窗口（默认）/ days=发布天数（立即开始，结束=当前+N天）
  scheduleMode: 'window',
  publishDays: 7
})

const rules = {
  examName: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  startTime: [{
    required: true,
    validator: (rule, value, callback) => {
      if (!value) return callback(new Error('请选择开始时间'))
      if (form.endTime && value >= form.endTime) return callback(new Error('开始时间必须早于结束时间'))
      callback()
    },
    trigger: 'change'
  }],
  endTime: [{
    required: true,
    validator: (rule, value, callback) => {
      if (!value) return callback(new Error('请选择结束时间'))
      if (form.startTime && value <= form.startTime) return callback(new Error('结束时间必须晚于开始时间'))
      callback()
    },
    trigger: 'change'
  }],
  duration: [{ required: true, message: '请填写考试时长', trigger: 'blur' }]
}

// 运行状态映射
function statusText(s) {
  return { 0: '未开始', 1: '进行中', 2: '已结束', 3: '已撤回' }[s] ?? '未知'
}
function statusTag(s) {
  return { 0: 'info', 1: 'success', 2: 'danger', 3: 'warning' }[s] ?? 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getExamPage(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (err) {
    console.error('查询失败:', err)
  } finally {
    loading.value = false
  }
}

/** 加载试卷下拉：仅"已发布且已组卷（题目数>0）"的试卷可用于发布考试；按当前学科过滤，考卷与考试同课程 */
async function fetchPapers() {
  try {
    const res = await getPaperList({ pageNum: 1, pageSize: 100, status: 1, courseId: query.courseId || undefined })
    paperList.value = (res.data?.records || []).filter(p => (p.questionCount ?? 0) > 0)
  } catch (err) {
    console.error('获取试卷列表失败:', err)
  }
}

function handleSearch() {
  query.pageNum = 1
  fetchData()
  fetchPapers() // 学科筛选变化后，可用试卷下拉同步切换为该学科
}

function handleReset() {
  query.keyword = ''
  query.runtimeStatus = null
  query.pageNum = 1
  fetchData()
}

/** 选择试卷：考试名称默认取试卷名称（用户已手动改过则不覆盖），仍可继续修改 */
function handlePaperChange(paperId) {
  if (!examNameAuto.value) return
  const paper = paperList.value.find(p => p.id === paperId)
  if (!paper?.paperName) return
  form.examName = paper.paperName
  // 名称已自动补上，顺手清掉「请输入考试名称」的校验红字
  formRef.value?.validateField?.('examName')?.catch?.(() => {})
}

/** 发布考试 */
function handleAdd() {
  isEdit.value = false
  examNameAuto.value = true
  Object.assign(form, {
    id: null,
    examName: '',
    paperId: null,
    startTime: null,
    endTime: null,
    duration: 60,
    scheduleMode: 'window',
    publishDays: 7
  })
  dialogVisible.value = true
}

/** 编辑：先拉详情回显 */
async function handleEdit(row) {
  try {
    const res = await getExamById(row.id)
    const e = res.data
    isEdit.value = true
    // 编辑已有考试：保留原名称，改试卷时不再覆盖
    examNameAuto.value = false
    Object.assign(form, {
      id: e.id,
      examName: e.examName,
      paperId: e.paperId,
      startTime: e.startTime,
      endTime: e.endTime,
      duration: e.duration,
      scheduleMode: 'window',
      publishDays: 7
    })
    // 兜底：若原试卷已被下架/清空题目不在下拉中，补入当前试卷保证回显（保存时后端会校验合法性）
    if (e.paperId && !paperList.value.some(p => p.id === e.paperId)) {
      paperList.value.unshift({
        id: e.paperId,
        paperName: row.paperName || `试卷 #${e.paperId}`,
        totalScore: row.totalScore ?? 0,
        questionCount: row.questionCount ?? 0
      })
    }
    dialogVisible.value = true
  } catch (err) {
    ElMessage.error('获取考试详情失败')
    console.error(err)
  }
}

/** 撤回 / 重新发布 */
async function handleToggleStatus(row, status) {
  const action = status === 0 ? '撤回' : '重新发布'
  try {
    await ElMessageBox.confirm(
      `确定要${action}「${row.examName}」吗？`,
      `${action}确认`,
      { type: 'warning', confirmButtonText: `确定${action}`, cancelButtonText: '取消' }
    )
    await updateExamStatus(row.id, status)
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(`${action}失败`)
      console.error(err)
    }
  }
}

/** 提前结束：结束时间改为当前时刻，考试立即进入已结束 */
async function handleFinish(row) {
  try {
    await ElMessageBox.confirm(
      `确定要提前结束「${row.examName}」吗？结束后学生端立即停止作答，未交卷学生将被强制收卷，该操作不可恢复！`,
      '提前结束确认',
      { type: 'warning', confirmButtonText: '确定结束', cancelButtonText: '取消' }
    )
    await finishExam(row.id)
    ElMessage.success('考试已提前结束')
    fetchData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('提前结束失败')
      console.error(err)
    }
  }
}

/** 重新发布（已结束的考试）：发布天数 / 自定义时间窗口两种模式 */
const republishVisible = ref(false)
const republishSubmitting = ref(false)
const republishRow = ref(null)
const republishDays = ref(7)
// days=发布天数（结束=当前+N天）/ window=自定义时间窗口（与发布考试窗口规则一致）
const republishMode = ref('days')
const republishForm = reactive({ startTime: null, endTime: null })

function openRepublish(row) {
  republishRow.value = row
  republishDays.value = 7
  republishMode.value = 'days'
  republishForm.startTime = null
  republishForm.endTime = null
  republishVisible.value = true
}

async function submitRepublish() {
  if (!republishRow.value) return
  let payload
  if (republishMode.value === 'days') {
    // 模式一：发布天数
    if (!republishDays.value || republishDays.value < 1) {
      ElMessage.warning('发布天数至少为 1 天')
      return
    }
    payload = { days: republishDays.value }
  } else {
    // 模式二：自定义时间窗口
    if (!republishForm.startTime || !republishForm.endTime) {
      ElMessage.warning('请选择开始时间和结束时间')
      return
    }
    if (republishForm.startTime >= republishForm.endTime) {
      ElMessage.warning('开始时间必须早于结束时间')
      return
    }
    if (republishForm.endTime <= formatLocal(new Date())) {
      ElMessage.warning('结束时间必须晚于当前时间，否则无法重新发布')
      return
    }
    payload = { startTime: republishForm.startTime, endTime: republishForm.endTime }
  }
  republishSubmitting.value = true
  try {
    await republishExam(republishRow.value.id, payload)
    ElMessage.success('重新发布成功')
    republishVisible.value = false
    fetchData()
  } catch (err) {
    console.error('重新发布失败:', err)
  } finally {
    republishSubmitting.value = false
  }
}

/** 删除 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${row.examName}」吗？删除后不可恢复！`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
    await deleteExam(row.id)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && query.pageNum > 1) {
      query.pageNum--
    }
    fetchData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(err)
    }
  }
}

/** ===== 批量操作（复用现有单条接口，循环调用，逐条结果统计） ===== */
const selectedRows = ref([])
const batchPublishing = ref(false)
const batchDeleting = ref(false)

/** 勾选模式：默认不显示勾选框，点「批量发布 / 批量删除」后才进入，避免误勾 */
const tableRef = ref(null)
const batchMode = ref(false)
const batchAction = ref('delete') // publish=批量发布，delete=批量删除

function onSelectionChange(rows) {
  selectedRows.value = rows
}

/** 清空勾选：表格内部选中态与外部行数组都要清，避免残留计数 */
function clearSelection() {
  tableRef.value?.clearSelection()
  selectedRows.value = []
}

/** 进入勾选模式：按 action 决定确认按钮是「确认发布」还是「确认删除」 */
function enterBatchMode(action) {
  batchAction.value = action
  batchMode.value = true
  clearSelection()
  ElMessage.info(action === 'publish' ? '请勾选需要发布的考试' : '请勾选需要删除的考试')
}

/** 退出勾选模式，勾选列随之隐藏 */
function exitBatchMode() {
  batchMode.value = false
  clearSelection()
}

/** 批量发布（两段式交互）：
 *  已撤回 → 直接发布；
 *  已结束 → 先弹确认框询问是否一并重新发布：确定 → 前往设置顺延天数；取消 → 跳过已结束的考试 */
const batchDaysState = reactive({ days: 7 })

async function handleBatchPublish() {
  const publishTargets = selectedRows.value.filter(r => r.status === 0)
  const republishTargets = selectedRows.value.filter(r => r.status === 1 && r.runtimeStatus === 2)
  if (!publishTargets.length && !republishTargets.length) {
    ElMessage.warning('选中的考试中没有"已撤回"或"已结束"状态的考试可发布')
    return
  }

  // 第一步：是否处理已结束的考试（取消 = 跳过它们）
  let includeRepublish = false
  if (republishTargets.length) {
    try {
      await ElMessageBox.confirm(
        h('div', null, [
          h('p', { style: 'margin:0 0 8px' }, `检测到 ${republishTargets.length} 场已结束的考试，是否一并重新发布？`),
          h('p', { style: 'margin:0;color:#909399;font-size:12px' }, '点击"跳过已结束"则忽略这些考试，仅发布其余已撤回的考试。')
        ]),
        '批量发布确认',
        {
          type: 'warning',
          confirmButtonText: `一并重新发布（${republishTargets.length} 场）`,
          cancelButtonText: '跳过已结束',
          // 官方开关：开启后点 × / ESC / 遮罩固定返回 'close'，与"跳过已结束"(cancel) 明确区分
          distinguishCancelAndClose: true
        }
      )
      includeRepublish = true
    } catch (action) {
      // 兼容不同版本 reject 值形态：字符串 'close' 或对象 { action: 'close' }
      const a = typeof action === 'string' ? action : action?.action
      if (a === 'close') return // 点 × / ESC / 遮罩 → 彻底关闭，不做任何批量操作
      includeRepublish = false // 点"跳过已结束"按钮 → 仅跳过已结束的，继续发布已撤回的
    }
  }

  // 第二步：确定后前往设置重新发布时长（取消设置同样跳过已结束的考试）
  let days = 7
  if (includeRepublish) {
    batchDaysState.days = 7
    try {
      const parts = [
        h('p', { style: 'margin:0 0 8px' }, `${republishTargets.length} 场已结束考试的结束时间将顺延为：当前时刻 + 天数`),
        h('div', { style: 'display:flex;align-items:center;gap:8px;margin-bottom:8px' }, [
          h(ElInputNumber, {
            modelValue: batchDaysState.days,
            'onUpdate:modelValue': (v) => { batchDaysState.days = v },
            min: 1,
            max: 365,
            precision: 0,
            controlsPosition: 'right'
          }),
          h('span', '天')
        ]),
        h('p', { style: 'margin:0;color:#909399;font-size:12px' }, '开始时间保持不变；已交卷学生的成绩不受影响')
      ]
      await ElMessageBox.confirm(h('div', null, parts), '设置重新发布时长', {
        type: 'info',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true
      })
      days = batchDaysState.days
    } catch (action) {
      // × / ESC / 遮罩 → 彻底关闭整个批量流程；"取消"按钮 → 跳过已结束的，继续发布已撤回的
      const a = typeof action === 'string' ? action : action?.action
      if (a === 'close') return
      includeRepublish = false
    }
  }

  // 汇总最终执行目标
  const finalRepublish = includeRepublish ? republishTargets : []
  if (!publishTargets.length && !finalRepublish.length) {
    ElMessage.info('已跳过全部已结束考试，没有需要发布的考试')
    return
  }

  batchPublishing.value = true
  try {
    const tasks = [
      ...publishTargets.map(r => updateExamStatus(r.id, 1)),
      ...finalRepublish.map(r => republishExam(r.id, { days }))
    ]
    const results = await Promise.allSettled(tasks)
    const ok = results.filter(x => x.status === 'fulfilled').length
    const fail = results.length - ok
    if (fail === 0) ElMessage.success(`批量发布成功（${ok} 场）`)
    else ElMessage.warning(`发布成功 ${ok} 场，失败 ${fail} 场（可能试卷下无题目或已被删除）`)
    // 保留勾选模式，清掉本次勾选，方便继续处理下一批
    clearSelection()
    fetchData()
  } catch (err) {
    console.error('批量发布失败:', err)
  } finally {
    batchPublishing.value = false
  }
}

/** 批量删除：逐条删除，进行中/已结束等不允许删除的会失败并统计 */
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedRows.value.length} 场考试吗？删除后不可恢复，进行中的考试将被拒绝删除！`,
      '批量删除确认',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  batchDeleting.value = true
  try {
    const results = await Promise.allSettled(selectedRows.value.map(r => deleteExam(r.id)))
    const ok = results.filter(x => x.status === 'fulfilled').length
    const fail = results.length - ok
    if (fail === 0) ElMessage.success(`批量删除成功（${ok} 场）`)
    else ElMessage.warning(`删除成功 ${ok} 场，失败 ${fail} 场（进行中或已产生成绩的考试不可删除）`)
    // 删除完成自动退出勾选模式，勾选框随之隐藏
    exitBatchMode()
    fetchData()
  } catch (err) {
    console.error('批量删除失败:', err)
  } finally {
    batchDeleting.value = false
  }
}

/** ===== 查看：考试信息 + 试卷内容 ===== */
const viewVisible = ref(false)
const viewLoading = ref(false)
const viewExam = ref(null)
const viewPaper = ref(null)

async function handleView(row) {
  viewVisible.value = true
  viewLoading.value = true
  viewExam.value = row
  viewPaper.value = null
  try {
    if (row.paperId) {
      const res = await getPaperDetail(row.paperId)
      viewPaper.value = res.data
    }
  } catch (err) {
    console.error('获取试卷详情失败:', err)
    ElMessage.error('获取试卷详情失败')
  } finally {
    viewLoading.value = false
  }
}

/** 题型/选项渲染帮助函数（与试卷预览保持一致） */
const typeMap = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
function questionTypeText(t) {
  return typeMap[t] || '未知'
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

/** Date → yyyy-MM-dd HH:mm:ss（与后端 LocalDateTime 解析格式一致） */
function formatLocal(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 提交表单 */
async function handleSubmit() {
  // 发布天数模式：先换算出时间窗口（考试立即开始，结束 = 当前时刻 + N 天），再走统一校验
  if (form.scheduleMode === 'days') {
    if (!form.publishDays || form.publishDays < 1) {
      ElMessage.warning('发布天数至少为 1 天')
      return
    }
    const now = new Date()
    form.startTime = formatLocal(now)
    form.endTime = formatLocal(new Date(now.getTime() + form.publishDays * 24 * 60 * 60 * 1000))
  }
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  // 剔除纯前端字段（scheduleMode/publishDays）后再提交
  const { scheduleMode, publishDays, ...payload } = form
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateExam({ ...payload })
      ElMessage.success('保存成功')
    } else {
      await createExam({ ...payload })
      ElMessage.success('发布成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (err) {
    // 错误提示已由 axios 响应拦截器统一弹出，这里不再重复弹（否则同一条错误会看到两个红条）
    console.error('提交考试失败:', err)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    query.pageSize = await useDefaultPageSize()
  } catch {}
  // 嵌入课程信息页时以父组件传入的 courseId 为准；独立页面时读路由 ?courseId=xx
  const cid = Number(props.courseId ?? route.query.courseId)
  if (cid) {
    query.courseId = cid
  }
  fetchData()
  fetchPapers()
  fetchCourses()
})
</script>

<style scoped>
.table-toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-tip {
  font-size: 13px;
  color: #909399;
}

/* 勾选模式下的提示：删除用告警色、发布用成功色，和普通说明文字区分开 */
.toolbar-tip.hint-danger {
  color: var(--el-color-danger);
}

.toolbar-tip.hint-success {
  color: var(--el-color-success);
}

.time-sep {
  text-align: center;
  color: #909399;
}

/* ===== 查看弹窗：试卷内容预览 ===== */
.preview-box { padding: 4px; }
.preview-header { border-bottom: 1px solid #ebeef5; padding-bottom: 12px; margin-bottom: 16px; }
.preview-header h3 { margin: 0 0 8px; color: #303133; }
.preview-meta {
  display: flex;
  gap: 20px;
  color: #909399;
  font-size: 13px;
}
.preview-desc { margin: 8px 0 0; color: #606266; font-size: 13px; }
.preview-body { display: flex; flex-direction: column; gap: 20px; max-height: 52vh; overflow-y: auto; padding-right: 4px; }
.preview-q { border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 14px; }
.q-title { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.q-index {
  display: inline-block;
  min-width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  background: #409eff;
  color: #fff;
  border-radius: 4px;
  font-size: 12px;
}
.q-score { color: #909399; font-size: 13px; }
.q-content { color: #303133; line-height: 1.6; margin-bottom: 8px; white-space: pre-wrap; }
.q-options { color: #606266; line-height: 1.8; padding-left: 4px; }
.q-answer { margin-top: 8px; color: #67c23a; font-size: 13px; }

.unit {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
}

.days-tip {
  width: 100%;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

/* 考试名称默认跟随试卷名称时的提示 */
.name-hint {
  width: 100%;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
