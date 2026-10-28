<template>
  <div class="practice-paper">
    <!-- 筛选区 -->
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="query">
        <el-form-item label="名称">
          <el-input v-model="query.keyword" placeholder="输入作业名称" clearable style="width: 220px" />
        </el-form-item>
        <!-- 嵌入「课程信息 → 课程作业」抽屉时，课程由父组件锁定，不再给下拉 -->
        <el-form-item label="课程" v-if="!embedded">
          <el-select v-model="query.courseId" placeholder="全部课程" clearable style="width: 180px">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已停用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="openEdit(null)">
            <el-icon><Plus /></el-icon>新增作业
          </el-button>
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
        <el-table-column label="作业名称" prop="paperName" min-width="200" show-overflow-tooltip />
        <el-table-column label="课程" prop="courseName" width="140">
          <template #default="{ row }">{{ row.courseName || '—' }}</template>
        </el-table-column>
        <el-table-column label="作业形式" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.attachmentPath" type="warning" size="small">文件作业</el-tag>
            <el-tag v-else type="info" size="small">题库选题</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题目数" prop="questionCount" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.attachmentPath" class="zero-text">—</span>
            <span v-else>{{ row.questionCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总分" prop="totalScore" width="80" align="center" />
        <el-table-column label="状态" prop="status" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建教师" prop="createTeacherName" width="110" align="center" />
        <el-table-column label="作答人数" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.practiceUserCount > 0" class="stat-num">{{ row.practiceUserCount }}</span>
            <span v-else class="zero-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="作答次数" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.practiceCount > 0" class="stat-num">{{ row.practiceCount }}</span>
            <span v-else class="zero-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" width="340" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="previewPaper(row)">预览</el-button>
            <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              type="success" link size="small"
              :disabled="!hasContent(row)"
              @click="onPublish(row, 1)"
            >发布</el-button>
            <el-button
              v-if="row.status === 1"
              type="warning" link size="small"
              @click="onPublish(row, 2)"
            >停用</el-button>
            <el-popconfirm
              :title="row.hasPracticeRecords ? '已有学生提交记录，删除后无法恢复，确定删除吗？' : '确定删除这个作业吗？'"
              confirm-button-text="确定删除"
              cancel-button-text="取消"
              @confirm="onDelete(row)"
            >
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="isEdit ? '编辑作业' : '新增作业'"
      width="780px"
      destroy-on-close
      @close="onEditClose"
    >
      <!-- 作业形式：顶部 tab 切换（① 从题库选题 ② 上传作业文件） -->
      <el-tabs
        v-model="form.contentType"
        class="form-tabs"
        @tab-change="onContentTypeChange"
      >
        <el-tab-pane name="question">
          <template #label>
            <span class="tab-label"><el-icon><Document /></el-icon>从题库选题</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="file">
          <template #label>
            <span class="tab-label"><el-icon><Upload /></el-icon>上传作业文件</span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="作业名称" prop="paperName">
          <el-input v-model="form.paperName" maxlength="100" show-word-limit />
        </el-form-item>
        <!-- 从课程信息页进入时，作业默认归属本课程，不允许改到别的课程 -->
        <el-form-item label="所属课程">
          <el-select v-model="form.courseId" placeholder="请选择课程" clearable :disabled="embedded" style="width: 260px">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>

        <!-- 作业形式：跟随顶部 tab 固定展示（只读，切换用上方 tab） -->
        <el-form-item label="作业形式" required>
          <el-tag :type="form.contentType === 'file' ? 'warning' : 'primary'" effect="plain">
            {{ form.contentType === 'file' ? '上传作业文件' : '从题库选题' }}
          </el-tag>
          <span class="form-tip">
            {{ form.contentType === 'file'
              ? '作业就是一份文件，学生下载或在线查看后线下作答，不做在线判分'
              : '从题库选择题目组成作业，学生在线作答并自动判分' }}
          </span>
        </el-form-item>

        <!-- ① 题库选题：选题 / 随机选题 / 逐题调整分值 -->
        <template v-if="form.contentType === 'question'">
        <el-form-item label="题目" required>
          <div class="q-actions">
            <div class="q-summary">
              已选 <b class="num">{{ form.questions.length }}</b> 题 · 
              总分 <b class="num">{{ totalScore }}</b> 分
            </div>
            <el-button size="small" type="primary" @click="openPicker">
              <el-icon><Plus /></el-icon>选题
            </el-button>
            <el-button size="small" type="warning" :icon="MagicStick" @click="openRandomPick">
              随机选题
            </el-button>
            <el-popconfirm
              title="清空所有已选题目？"
              @confirm="form.questions = []"
            >
              <template #reference>
                <el-button size="small" type="danger" plain :disabled="form.questions.length === 0">
                  清空
                </el-button>
              </template>
            </el-popconfirm>
          </div>
        </el-form-item>
        <el-form-item label=" " :colon="false">
          <el-table :data="form.questions" size="small" border style="width: 100%">
            <el-table-column label="序号" type="index" width="60" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="题型" width="90" align="center">
              <template #default="{ row }">{{ questionTypeText(row.questionType) }}</template>
            </el-table-column>
            <el-table-column label="题干" prop="questionContent" show-overflow-tooltip min-width="280">
              <template #default="{ row }">
                <span v-if="row.questionContent">{{ row.questionContent }}</span>
                <el-tag v-else type="info">ID: {{ row.questionId }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="分值" width="130">
              <template #default="{ row }">
                <el-input-number v-model="row.score" :min="1" :max="100" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="removeQ($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        </template>

        <!-- ② 上传作业文件：作业就是一份文件，学生下载/在线查看后线下作答 -->
        <template v-else>
          <el-form-item label="作业文件" required v-loading="attachUploading">
            <div class="attach-area">
              <el-upload
                ref="attachUploadRef"
                class="attach-uploader"
                drag
                :show-file-list="false"
                :limit="1"
                :multiple="false"
                :disabled="attachUploading"
                accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md,.zip,.rar,.7z,.jpg,.jpeg,.png,.gif,.webp"
                :before-upload="beforeAttachmentUpload"
                :http-request="doUploadAttachment"
                :on-exceed="handleAttachmentExceed"
              >
                <el-icon class="el-icon--upload"><Upload /></el-icon>
                <div class="el-upload__text">
                  {{ attachUploading ? '上传中…' : '将作业文件拖到此处，或' }}<em v-if="!attachUploading">点击上传</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">
                    支持 Word / PDF / PPT / Excel / 图片 / 压缩包，单文件不超过 300MB；上传后学生可下载或在线查看，线下作答。
                  </div>
                </template>
              </el-upload>

              <!-- 已上传文件回显：唯一事实源 form.attachment -->
              <div v-if="form.attachment.attachmentPath" class="attach-file">
                <el-icon><Document /></el-icon>
                <span class="attach-name">{{ form.attachment.attachmentName }}</span>
                <span class="attach-size">{{ formatSize(form.attachment.attachmentSize) }}</span>
                <el-link type="primary" :href="form.attachment.attachmentPath" target="_blank">预览/下载</el-link>
                <el-button type="danger" link size="small" @click="removeAttachment">移除</el-button>
              </div>

              <div class="attach-tip">
                该形式作业不做在线判分：学生下载或在线查看作业文件后线下完成作答。
              </div>
            </div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="onEditClose">取消</el-button>
        <el-button :disabled="!canSave" type="primary" @click="onSave(false)">
          <el-icon><Document /></el-icon>保存为草稿
        </el-button>
        <el-button :disabled="!canSave" type="success" @click="onSave(true)">
          <el-icon><Promotion /></el-icon>保存并发布
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      title="预览作业"
      width="700px"
      destroy-on-close
    >
      <div v-if="previewData" class="preview-box">
        <div class="preview-header">
          <h3>{{ previewData.paperName }}</h3>
          <div class="preview-meta">
            <span>课程：{{ previewData.courseName || '—' }}</span>
            <span>题目数：{{ previewData.questionCount || 0 }}</span>
            <span>总分：{{ previewData.totalScore || 0 }} 分</span>
          </div>
          <p v-if="previewData.description" class="preview-desc">{{ previewData.description }}</p>
          <div v-if="previewData.attachmentPath" class="preview-attach">
            <el-icon><Document /></el-icon>
            <span class="preview-attach-name">{{ previewData.attachmentName || '作业文件' }}</span>
            <span class="attach-size">{{ formatSize(previewData.attachmentSize) }}</span>
            <el-link type="primary" :href="previewData.attachmentPath" target="_blank">预览/下载</el-link>
          </div>
        </div>
        <div class="preview-body" v-if="previewData.questions?.length">
          <div v-for="(q, idx) in previewData.questions" :key="q.id || q.questionId" class="preview-q">
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
          </div>
        </div>
        <el-empty v-else :description="previewData.attachmentPath ? '文件型作业：学生下载附件后线下作答' : '暂无题目'" />
      </div>
    </el-dialog>

    <!-- 选题弹窗：复用题库列表 + 多选 -->
    <el-dialog
      v-model="pickerVisible"
      title="从题库选择题目"
      width="960px"
      top="4vh"
      @close="pickerVisible = false"
    >
      <el-form :inline="true" :model="pickerQuery">
        <el-form-item label="题型">
          <el-select v-model="pickerQuery.questionType" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="d in questionTypes" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="pickerQuery.difficulty" clearable placeholder="全部" style="width: 100px">
            <el-option v-for="d in difficulties" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="pickerQuery.keyword" clearable placeholder="题干关键字" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPickerList">查询</el-button>
          <el-button @click="resetPickerQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="pickerTableRef"
        :data="pickerList"
        row-key="id"
        @selection-change="onPickerSelectionChange"
        height="460"
        stripe
        border
      >
        <el-table-column type="selection" width="50" :reserve-selection="true" />
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="题型" width="90" align="center">
          <template #default="{ row }">{{ questionTypeText(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="难度" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="diffTagType(row.difficulty)">
              {{ difficultyText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分值" prop="score" width="70" align="center" />
        <el-table-column label="题干" prop="content" min-width="380" show-overflow-tooltip />
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pickerQuery.pageNum"
          :page-size="10"
          :total="pickerTotal"
          layout="total, prev, pager, next, jumper"
          @current-change="loadPickerList"
        />
      </div>

      <template #footer>
        <div class="picker-footer">
          <span>本次已选 <b>{{ pickedQuestions.length }}</b> 题（不含之前已选择的）</span>
          <div>
            <el-button @click="closePicker">取消</el-button>
            <el-button type="primary" @click="confirmPick">确认添加</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 随机选题弹窗：按题型/难度/数量随机抽题 -->
    <el-dialog v-model="randomPickVisible" title="随机选题" width="720px" :close-on-click-modal="false">
      <div class="pick-tip">
        按「题型 + 难度 + 数量」从题库随机抽题，<b>追加到下方已选题目列表</b>；每个规则可在同一份作业中出现多次。
      </div>

      <div v-for="(rule, idx) in pickRules" :key="idx" class="pick-rule">
        <el-form inline @submit.prevent>
          <el-form-item label="题型">
            <el-select v-model="rule.questionType" placeholder="题型" style="width: 110px">
              <el-option v-for="(label, value) in pickTypeMap" :key="value" :label="label" :value="Number(value)" />
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
            <el-button link type="danger" :icon="Delete" @click="removePickRule(idx)">删除</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="pick-ops">
        <el-button :icon="Plus" @click="addPickRule">添加规则</el-button>
        <span class="pick-summary">
          共 {{ pickCount }} 题 · 总分 {{ pickScore }} 分
        </span>
      </div>

      <template #footer>
        <el-button @click="randomPickVisible = false">取消</el-button>
        <el-button type="primary" :loading="randomPicking" @click="handleRandomPick">开始抽题</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Document, Promotion, MagicStick, Delete, Upload
} from '@element-plus/icons-vue'
import { getQuestionList as listQuestion } from '@/api/question'
import { getCourseList as listCourse } from '@/api/course'
import {
  listPracticePaper,
  getPracticePaperDetail,
  savePracticePaper,
  publishPracticePaper,
  deletePracticePaper,
  randomPickPracticePaper,
  uploadPracticeAttachment
} from '@/api/practicePaper'

// ===== 基础 =====
/**
 * 两种复用入口：
 * 1) 独立页面（/practice/papers）：课程从路由 ?courseId= 带入，用户可自行切换筛选；
 * 2) 嵌入「课程信息 → 课程作业」抽屉：由父组件传入 courseId 锁定本课程（隐藏课程下拉，新增作业默认归属本课程）。
 */
const props = defineProps({
  courseId: { type: [Number, String], default: null },
  embedded: { type: Boolean, default: false }
})
const route = useRoute()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const query = reactive({ keyword: '', courseId: null, status: null })
const courses = ref([])

const questionTypes = [
  { label: '单选题', value: 1 },
  { label: '多选题', value: 2 },
  { label: '判断题', value: 3 },
  { label: '填空题', value: 4 },
  { label: '简答题', value: 5 },
  { label: '名词解释', value: 6 }
]
const difficulties = [
  { label: '简单', value: 1 },
  { label: '中等', value: 2 },
  { label: '困难', value: 3 }
]

function questionTypeText(t) {
  const m = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
  return m[t] || '未知'
}
function difficultyText(d) {
  const m = { 1: '简单', 2: '中等', 3: '困难' }
  return m[d] || '未知'
}
function diffTagType(d) {
  const m = { 1: 'success', 2: 'warning', 3: 'danger' }
  return m[d] || ''
}
function statusText(s) {
  const m = { 0: '草稿', 1: '已发布', 2: '已停用' }
  return m[s] || '未知'
}
function statusTagType(s) {
  const m = { 0: 'info', 1: 'success', 2: 'danger' }
  return m[s] || ''
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

// ===== 列表加载 =====
function loadList() {
  loading.value = true
  listPracticePaper({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    ...query
  }).then(res => {
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  }).catch(err => ElMessage.error(err?.message || '加载失败'))
    .finally(() => loading.value = false)
}

function resetQuery() {
  Object.assign(query, { keyword: '', courseId: null, status: null })
  pageNum.value = 1
  loadList()
}

onMounted(() => {
  // 嵌入课程信息页时以父组件传入的 courseId 为准；独立页面时读路由 ?courseId=xx
  const cid = Number(props.courseId ?? route.query.courseId)
  if (cid) {
    query.courseId = cid
  }
  listCourse().then(res => { courses.value = res.data?.rows || res.data || [] }).catch(() => {})
  loadList()
})

// ===== 发布/停用/删除 =====
async function onPublish(row, status) {
  try {
    await publishPracticePaper(row.id, status)
    ElMessage.success(status === 1 ? '发布成功' : '停用成功')
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '操作失败')
  }
}
async function onDelete(row) {
  try {
    await deletePracticePaper(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败')
  }
}

// ===== 新增/编辑 =====
const editVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null,
  paperName: '',
  courseId: null,
  description: '',
  /** 作业形式：question=从题库选题；file=上传作业文件 */
  contentType: 'question',
  questions: [],  // { questionId, score, sortNo, questionType?, questionContent? }
  /** 文件型作业的附件（由 /api/practice/paper/upload 返回后回填） */
  attachment: emptyAttachment()
})
const rules = {
  paperName: [{ required: true, message: '请输入作业名称', trigger: 'blur' }]
}

const totalScore = computed(() => form.questions.reduce((s, q) => s + (q.score || 0), 0))
/** 文件作业以「已上传附件」为内容，题库作业以「至少一题」为内容 */
const canSave = computed(() => {
  if (!form.paperName || attachUploading.value) return false
  return form.contentType === 'file'
    ? !!form.attachment.attachmentPath
    : form.questions.length > 0
})

/** 是否已配置作业内容（列表「发布」按钮可用性：题库选题 或 作业文件） */
function hasContent(row) {
  return (row?.questionCount || 0) > 0 || !!row?.attachmentPath
}

function openEdit(row) {
  isEdit.value = !!row
  if (row) {
    getPracticePaperDetail(row.id).then(res => {
      const d = res.data
      form.id = d.id
      form.paperName = d.paperName
      form.courseId = d.courseId
      form.description = d.description || ''
      // 题目排序
      const qs = (d.questions || []).map(q => ({
        questionId: q.questionId,
        score: q.score,
        sortNo: q.sortNo,
        questionType: q.questionType,
        questionContent: q.questionContent
      }))
      qs.sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0))
      form.questions = qs
      // 有附件 → 文件型作业；否则题库型作业
      form.contentType = d.attachmentPath ? 'file' : 'question'
      form.attachment = {
        attachmentName: d.attachmentName || '',
        attachmentPath: d.attachmentPath || '',
        attachmentSize: d.attachmentSize ?? null,
        attachmentType: d.attachmentType || ''
      }
      clearAttachUpload()
      if (d.hasPracticeRecords && d.hasPracticeRecords > 0) {
        ElMessage.warning('该作业已有学生提交记录，修改题目可能影响历史数据解读')
      }
      editVisible.value = true
    }).catch(err => ElMessage.error(err?.message || '获取详情失败'))
  } else {
    form.id = null
    form.paperName = ''
    // 嵌入课程信息页时，新增作业默认归属本课程
    form.courseId = props.embedded ? Number(props.courseId) : null
    form.description = ''
    form.contentType = 'question'
    form.questions = []
    form.attachment = emptyAttachment()
    clearAttachUpload()
    editVisible.value = true
  }
}

function onEditClose() {
  editVisible.value = false
}

function removeQ(index) {
  form.questions.splice(index, 1)
}

// ===== 文件型作业：作业附件上传 =====

const attachUploadRef = ref(null)
const attachUploading = ref(false)
/** 与后端白名单保持一致（后端仍会再校验一次） */
const ATTACH_EXT = ['pdf', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx',
  'txt', 'md', 'zip', 'rar', '7z', 'jpg', 'jpeg', 'png', 'gif', 'webp']
/** 与 application.properties 的 spring.servlet.multipart.max-file-size 保持一致 */
const ATTACH_MAX_MB = 300

function emptyAttachment() {
  return { attachmentName: '', attachmentPath: '', attachmentSize: null, attachmentType: '' }
}

/** 清空 el-upload 内部文件列表（自定义回显只认 form.attachment） */
function clearAttachUpload() {
  try { attachUploadRef.value?.clearFiles?.() } catch { /* ignore */ }
}

function resetAttachment() {
  form.attachment = emptyAttachment()
  clearAttachUpload()
}

/** 切换作业形式：清空另一侧内容，避免保存了界面上看不见的数据 */
function onContentTypeChange(val) {
  if (val === 'file') {
    form.questions = []
  } else {
    resetAttachment()
  }
}

/** 选择文件后先做一次友好校验，不合法直接中断上传 */
function beforeAttachmentUpload(rawFile) {
  const ext = (rawFile.name.split('.').pop() || '').toLowerCase()
  if (!ATTACH_EXT.includes(ext)) {
    ElMessage.error(`不支持的作业文件格式：.${ext}`)
    return false
  }
  if (rawFile.size > ATTACH_MAX_MB * 1024 * 1024) {
    ElMessage.error(`文件过大（>${ATTACH_MAX_MB}MB），请压缩后再上传`)
    return false
  }
  return true
}

/** 自定义上传：落盘后拿到访问路径，保存作业时随 payload 一起提交 */
async function doUploadAttachment(options) {
  const rawFile = options.file
  attachUploading.value = true
  try {
    const res = await uploadPracticeAttachment(rawFile)
    const d = res.data || {}
    form.attachment = {
      attachmentName: d.fileName || rawFile.name,
      attachmentPath: d.filePath || '',
      attachmentSize: d.fileSize ?? rawFile.size,
      attachmentType: d.fileType || ''
    }
    options.onSuccess?.(d)
    ElMessage.success('作业文件上传成功')
  } catch (e) {
    options.onError?.(e)
    ElMessage.error(e?.message || '作业文件上传失败')
    resetAttachment()
  } finally {
    attachUploading.value = false
  }
}

/** limit=1：再次选择文件时用新文件替换旧文件 */
function handleAttachmentExceed(files) {
  const rawFile = files?.[0]
  clearAttachUpload()
  if (rawFile) doUploadAttachment({ file: rawFile })
}

function removeAttachment() {
  resetAttachment()
}

/** 字节数 → 可读大小（后端已返回 fileSizeText，这里用于本地兜底/列表预览） */
function formatSize(bytes) {
  const b = Number(bytes)
  if (!Number.isFinite(b) || b <= 0) return ''
  if (b < 1024) return `${b} B`
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`
  return `${(b / 1024 / 1024).toFixed(1)} MB`
}

async function onSave(publish) {
  if (!canSave.value) {
    ElMessage.warning(form.contentType === 'file'
      ? '请填写作业名称并上传作业文件'
      : '请填写作业名称并至少选择 1 道题')
    return
  }
  if (formRef.value) {
    try { await formRef.value.validate() } catch { return }
  }
  try {
    const isFile = form.contentType === 'file'
    const payload = {
      id: form.id,
      paperName: form.paperName,
      courseId: form.courseId,
      description: form.description,
      targetStatus: publish ? 1 : 0,
      // 二选一：文件型作业不提交题目，题库型作业不提交附件
      questions: isFile ? [] : form.questions.map((q, i) => ({
        questionId: q.questionId,
        score: q.score,
        sortNo: i + 1
      })),
      attachmentName: isFile ? form.attachment.attachmentName : null,
      attachmentPath: isFile ? form.attachment.attachmentPath : null,
      attachmentSize: isFile ? form.attachment.attachmentSize : null,
      attachmentType: isFile ? form.attachment.attachmentType : null
    }
    const res = await savePracticePaper(payload)
    ElMessage.success(publish ? '保存并发布成功' : '保存草稿成功')
    editVisible.value = false
    loadList()
    return res
  } catch (err) {
    ElMessage.error(err?.message || '保存失败')
  }
}

// ===== 预览 =====
const previewVisible = ref(false)
const previewData = ref(null)

function previewPaper(row) {
  getPracticePaperDetail(row.id).then(res => {
    previewData.value = res.data
    previewVisible.value = true
  }).catch(err => ElMessage.error(err?.message || '获取详情失败'))
}

// ===== 选题弹窗 =====
const pickerVisible = ref(false)
const pickerList = ref([])
const pickerTotal = ref(0)
const pickerQuery = reactive({ pageNum: 1, keyword: '', questionType: null, difficulty: null })
const pickerTableRef = ref(null)
const pickedQuestions = ref([])

function openPicker() {
  pickedQuestions.value = []
  pickerQuery.pageNum = 1
  pickerVisible.value = true
  nextTick(() => loadPickerList())
}

function resetPickerQuery() {
  // 保留 reactive 对象引用，避免破坏响应式
  Object.assign(pickerQuery, { pageNum: 1, keyword: '', questionType: null, difficulty: null })
  pickerList.value = []
  pickerTotal.value = 0
}

function loadPickerList() {
  listQuestion({
    pageNum: pickerQuery.pageNum,
    pageSize: 10,
    keyword: pickerQuery.keyword,
    questionType: pickerQuery.questionType,
    difficulty: pickerQuery.difficulty
  }).then(res => {
    pickerList.value = res.data?.records || res.data?.rows || []
    pickerTotal.value = res.data?.total || 0
    // 回显已存在题目勾选状态
    nextTick(() => {
      if (pickerTableRef.value && typeof pickerTableRef.value.toggleRowSelection === 'function') {
        for (const q of pickerList.value) {
          const has = form.questions.some(x => x.questionId === q.id)
          if (has) pickerTableRef.value.toggleRowSelection(q, true)
        }
      }
    })
  }).catch(err => ElMessage.error(err?.message || '加载题库失败'))
}

function onPickerSelectionChange(selection) {
  // 保留当前批次选择（排除已经在编辑表单中的老题）
  const oldIds = new Set(form.questions.map(q => q.questionId))
  pickedQuestions.value = selection.filter(q => !oldIds.has(q.id))
}

function confirmPick() {
  const existingIds = new Set(form.questions.map(q => q.questionId))
  let added = 0
  for (const q of pickedQuestions.value) {
    if (!existingIds.has(q.id)) {
      form.questions.push({
        questionId: q.id,
        score: q.score || 5,
        sortNo: form.questions.length + 1,
        questionType: q.questionType,
        questionContent: q.content
      })
      added++
    }
  }
  if (added > 0) ElMessage.success(`已添加 ${added} 道题目`)
  else ElMessage.warning('未选择新题目或已在列表中')
  closePicker()
}

function closePicker() {
  pickerVisible.value = false
}

// ===== 随机选题 =====
const randomPickVisible = ref(false)
const randomPicking = ref(false)
const pickTypeMap = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题', 6: '名词解释' }
const pickRules = ref([
  { questionType: 1, difficulty: null, count: 10, score: 2 },
])

const pickCount = computed(() => pickRules.value.reduce((s, r) => s + (r.count || 0), 0))
const pickScore = computed(() => pickRules.value.reduce((s, r) => s + (r.count || 0) * (r.score || 0), 0))

function addPickRule() {
  pickRules.value.push({ questionType: 1, difficulty: null, count: 5, score: 2 })
}

function removePickRule(idx) {
  pickRules.value.splice(idx, 1)
}

function openRandomPick() {
  if (!form.courseId) {
    ElMessage.warning('请先为作业选择所属课程，随机选题将基于该课程题库')
    return
  }
  pickRules.value = [{ questionType: 1, difficulty: null, count: 10, score: 2 }]
  randomPickVisible.value = true
}

async function handleRandomPick() {
  const rules = pickRules.value.filter(r => r.count > 0)
  if (rules.length === 0) {
    ElMessage.warning('请至少保留一条有效抽题规则')
    return
  }
  randomPicking.value = true
  try {
    const res = await randomPickPracticePaper({
      courseId: form.courseId,
      rules: rules.map(r => ({ questionType: r.questionType, difficulty: r.difficulty || null, count: r.count, score: r.score }))
    })
    const questions = res.data || []
    if (questions.length === 0) {
      ElMessage.warning('当前题库暂无符合条件的题目')
      return
    }
    const existingIds = new Set(form.questions.map(q => q.questionId))
    let added = 0
    for (const q of questions) {
      if (!existingIds.has(q.id)) {
        form.questions.push({
          questionId: q.id,
          score: Number(q.score) || 2,
          sortNo: form.questions.length + 1,
          questionType: q.questionType,
          questionContent: q.content
        })
        existingIds.add(q.id)
        added++
      }
    }
    ElMessage.success(`已随机抽取 ${questions.length} 题，新增 ${added} 题`)
    randomPickVisible.value = false
  } catch (e) {
    const insufficient = e.res?.data?.insufficient
    if (insufficient && insufficient.length) {
      const lines = insufficient.map(
        (i) => `「${pickTypeMap[i.questionType] || i.questionType}」需要 ${i.need} 题，题库仅 ${i.available} 题`
      )
      ElMessage.warning(lines.join('<br/>'))
    } else {
      ElMessage.error(e?.message || '随机选题失败')
    }
  } finally {
    randomPicking.value = false
  }
}
</script>

<style scoped>
.practice-paper { padding: 4px; }
.mb-4 { margin-bottom: 16px; }
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.q-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0 12px;
}
.q-summary { font-size: 13px; color: #606266; }
.q-summary .num { color: #409eff; font-size: 14px; }

/* 预览 */
.preview-box { padding: 4px; }
.preview-header {
  padding: 16px;
  background: linear-gradient(135deg, #ecf5ff, #f0f9eb);
  border-radius: 8px;
  margin-bottom: 16px;
  text-align: center;
}
.preview-header h3 { margin: 0 0 8px; color: #303133; }
.preview-meta {
  display: flex;
  gap: 20px;
  justify-content: center;
  font-size: 13px;
  color: #606266;
}
.preview-desc {
  margin: 10px 0 0;
  color: #909399;
  font-size: 13px;
}
.preview-body { display: flex; flex-direction: column; gap: 20px; }
.preview-q {
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.q-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.q-index {
  width: 26px; height: 26px;
  background: #409eff;
  color: #fff;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 13px;
}
.q-score { color: #e6a23c; font-size: 13px; }
.q-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  margin-bottom: 10px;
}
.q-options {
  padding-left: 8px;
  color: #606266;
  line-height: 2;
  font-size: 13px;
}

.picker-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.picker-footer b { color: #409eff; margin: 0 4px; }

/* 随机选题 */
.pick-tip {
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fdf6ec;
  border-radius: 6px;
  color: #606266;
  font-size: 13px;
}
.pick-rule { margin-bottom: 4px; }
.pick-ops {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.pick-summary { color: #909399; font-size: 13px; }

/* 统计数字样式 */
.stat-num { color: #409eff; font-weight: 600; }
.zero-text { color: #c0c4cc; }

/* 文件型作业：附件上传区 */
.attach-area { width: 100%; }
.attach-uploader { width: 100%; }
.attach-uploader :deep(.el-upload-dragger) { padding: 18px 10px; }
.attach-file {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}
.attach-file .attach-name { color: #303133; font-weight: 600; word-break: break-all; }
.attach-size { color: #909399; font-size: 12px; }
.attach-tip { margin-top: 8px; font-size: 12px; color: #909399; }
.preview-attach {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
}
.preview-attach-name { color: #303133; }

/* 作业形式：顶部 tab 切换 */
.form-tabs { margin-bottom: 2px; }
.form-tabs :deep(.el-tabs__header) { margin-bottom: 18px; }
.form-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background-color: #e4e7ed; }
.form-tabs :deep(.el-tabs__item) { font-size: 14px; }
.tab-label { display: inline-flex; align-items: center; gap: 5px; }
.form-tip { margin-left: 8px; font-size: 12px; color: #909399; }
</style>
