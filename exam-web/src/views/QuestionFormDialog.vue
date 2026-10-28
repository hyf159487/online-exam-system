<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="isEdit ? '编辑题目' : '新增题目'"
    width="720px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <!-- 学科 -->
      <el-form-item label="学科" prop="courseId">
        <el-select v-model="form.courseId" placeholder="请选择学科" style="width: 280px" filterable @change="handleCourseChange">
          <el-option
            v-for="c in courseList"
            :key="c.id"
            :label="c.courseName"
            :value="c.id"
          />
        </el-select>
      </el-form-item>

      <!-- 知识点：选择学科后自动加载该学科下的知识点（可选 1~3 个，可 AI 智能匹配） -->
      <el-form-item label="知识点">
        <div class="knowledge-wrap">
          <el-select
            v-model="form.knowledgeIds"
            placeholder="请先选择学科，再选知识点（可选 1~3 个）"
            style="width: 320px"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :multiple-limit="3"
            :loading="knowledgeLoading"
            :disabled="!form.courseId"
          >
            <el-option
              v-for="k in knowledgeList"
              :key="k.id"
              :label="k.pointName"
              :value="k.id"
            />
          </el-select>
          <el-tooltip
            content="根据题干/选项/答案/解析自动推荐 1~3 个知识点"
            placement="top"
            :show-after="300"
          >
            <el-button
              :icon="MagicStick"
              :loading="aiMatching"
              :disabled="!form.courseId || !form.content || !form.content.trim()"
              @click="handleAiMatch"
            >AI 匹配</el-button>
          </el-tooltip>
        </div>
      </el-form-item>

      <!-- 题型 -->
      <el-form-item label="题型" prop="questionType">
        <el-select v-model="form.questionType" placeholder="请选择题型" style="width: 160px" @change="handleTypeChange">
          <el-option
            v-for="item in questionTypeOptions"
            :key="item.itemValue"
            :label="item.itemLabel"
            :value="Number(item.itemValue)"
          />
        </el-select>
      </el-form-item>

      <!-- 题干 -->
      <el-form-item label="题干" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="3"
          placeholder="请输入题干内容"
        />
      </el-form-item>

      <!-- 选项编辑器（仅单选/多选） -->
      <el-form-item
        v-if="form.questionType === 1 || form.questionType === 2"
        label="选项"
        required
      >
        <div class="options-wrapper">
          <div v-for="(opt, idx) in options" :key="idx" class="option-row">
            <!-- 单选：radio 选正确答案 -->
            <el-radio v-if="form.questionType === 1" v-model="singleCorrect" :value="idx">
              {{ letter(idx) }}
            </el-radio>
            <!-- 多选：checkbox 选正确答案 -->
            <el-checkbox v-else v-model="opt.correct">
              {{ letter(idx) }}
            </el-checkbox>

            <el-input
              v-model="opt.text"
              :placeholder="`选项 ${letter(idx)} 内容`"
              style="flex: 1"
            />

            <el-button
              type="danger"
              :icon="Delete"
              circle
              size="small"
              :disabled="options.length <= 2"
              @click="removeOption(idx)"
            />
          </div>
          <el-button type="primary" plain :icon="Plus" size="small" @click="addOption">
            添加选项
          </el-button>
        </div>
      </el-form-item>

      <!-- 答案 -->
      <el-form-item label="答案" prop="answer">
        <!-- 判断题 -->
        <el-radio-group v-if="form.questionType === 3" v-model="form.answer">
          <el-radio value="T">正确</el-radio>
          <el-radio value="F">错误</el-radio>
        </el-radio-group>
        <!-- 填空题 -->
        <el-input
          v-else-if="form.questionType === 4"
          v-model="form.answer"
          placeholder="请输入答案（多个空用 | 分隔）"
        />
        <!-- 简答题 / 名词解释 -->
        <el-input
          v-else-if="form.questionType === 5 || form.questionType === 6"
          v-model="form.answer"
          type="textarea"
          :rows="3"
          placeholder="请输入参考答案"
        />
        <!-- 单选/多选：答案由选项区选择，这里只读显示 -->
        <el-input
          v-else
          :model-value="selectionAnswer"
          disabled
          placeholder="请在上方选项中选择正确答案"
        />
      </el-form-item>

      <!-- 难度 + 分值 -->
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="难度" prop="difficulty">
            <el-radio-group v-model="form.difficulty">
              <el-radio-button
                v-for="item in difficultyOptions"
                :key="item.itemValue"
                :value="Number(item.itemValue)"
              >{{ item.itemLabel }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="分值" prop="score">
            <el-input-number v-model="form.score" :min="0.5" :max="100" :step="0.5" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 解析 -->
      <el-form-item label="解析">
        <el-input
          v-model="form.analysis"
          type="textarea"
          :rows="2"
          placeholder="答案解析（选填）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存' : '新增' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { Delete, Plus, MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { createQuestion, updateQuestion, aiMatchKnowledge } from '@/api/question'
import { getCourseList } from '@/api/course'
import { getKnowledgePointList } from '@/api/knowledgePoint'
import { getDictItemsByCode } from '@/api/dict'

const props = defineProps({
  modelValue: Boolean,
  /** 编辑时传入题目对象；新增时传 null */
  question: { type: Object, default: null },
  /** 从随机组卷跳转过来时新增弹窗的预填信息（科目、题型） */
  preset: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref()
const submitting = ref(false)

/** 是否编辑模式 */
const isEdit = computed(() => !!props.question?.id)

/** 学科下拉数据 */
const courseList = ref([])

/** 知识点下拉数据与加载状态（选学科后按学科加载） */
const knowledgeList = ref([])
const knowledgeLoading = ref(false)

/** 学科切换：清空已选知识点并重新加载该学科知识点 */
function handleCourseChange() {
  form.knowledgeIds = []
  loadKnowledgeList(form.courseId)
}

/** 按学科加载知识点列表（无知识点时置空数组，下拉显示空提示） */
async function loadKnowledgeList(courseId) {
  knowledgeList.value = []
  if (!courseId) {
    form.knowledgeIds = []
    return
  }
  knowledgeLoading.value = true
  try {
    const res = await getKnowledgePointList(courseId)
    knowledgeList.value = res.data || []
    // 若回显的知识点里有不属于当前学科（课程被换过）的，自动剔除，避免存了错课程的知识点
    const cur = form.knowledgeIds || []
    form.knowledgeIds = cur.filter((id) => knowledgeList.value.some((k) => k.id === id))
  } catch (e) {
    knowledgeList.value = []
    form.knowledgeIds = []
  } finally {
    knowledgeLoading.value = false
  }
}

/** 是否正在 AI 匹配知识点 */
const aiMatching = ref(false)

/** 为 AI 匹配组装选项文本（选择题拼接，非选择题返回空串） */
function buildOptionsForMatch() {
  if (form.questionType !== 1 && form.questionType !== 2) return ''
  const texts = options.value
    .map((o) => (o.text && o.text.trim()) || '')
    .filter(Boolean)
  return texts.length ? texts.join(' ') : ''
}

/**
 * AI 智能匹配知识点：拿着当前填写的题干/选项/答案/解析，构造临时题目
 * （负 id 仅用于结果对应，不入库）调用匹配接口，推荐 1~3 个知识点回填到表单。
 */
async function handleAiMatch() {
  if (!form.courseId) {
    ElMessage.warning('请先选择学科')
    return
  }
  if (!form.content || !form.content.trim()) {
    ElMessage.warning('请先填写题干，再 AI 匹配知识点')
    return
  }
  const temp = {
    id: -1, // 临时 id：匹配结果将按此 id 对应回填，不影响真实入库
    courseId: form.courseId,
    questionType: form.questionType,
    content: form.content,
    options: buildOptionsForMatch(),
    answer:
      form.questionType === 1 || form.questionType === 2
        ? selectionAnswer.value || ''
        : form.answer || '',
    analysis: form.analysis || ''
  }
  aiMatching.value = true
  try {
    const res = await aiMatchKnowledge([temp])
    const list = res?.data || []
    const item = list.find((r) => r.questionId === -1) || list[0]
    if (item && item.knowledgeIds && item.knowledgeIds.length) {
      // 只保留当前学科下真实存在的知识点（防止匹配到其它课程的知识点）
      const valid = item.knowledgeIds.filter((id) =>
        knowledgeList.value.some((k) => k.id === id)
      )
      form.knowledgeIds = valid.slice(0, 3)
      ElMessage.success(
        valid.length
          ? `AI 匹配到 ${valid.length} 个知识点：${(item.knowledgeNames || []).join('、')}`
          : 'AI 匹配完成，未找到当前学科的合适知识点，可手动选择'
      )
    } else {
      ElMessage.info('AI 匹配完成，未找到合适的知识点，可手动从下拉中选择')
    }
  } catch (e) {
    ElMessage.error('AI 匹配失败：' + (e?.message || '未知错误'))
  } finally {
    aiMatching.value = false
  }
}

/** 字典下拉数据 */
const questionTypeOptions = ref([])
const difficultyOptions = ref([])

onMounted(() => {
  getCourseList()
    .then((res) => { courseList.value = res.data || [] })
    .catch((err) => console.error('获取学科列表失败:', err))

  // 加载字典选项
  getDictItemsByCode('question_type')
    .then((res) => { questionTypeOptions.value = res.data || [] })
    .catch(() => {
      questionTypeOptions.value = [
        { itemLabel: '单选题', itemValue: '1' },
        { itemLabel: '多选题', itemValue: '2' },
        { itemLabel: '判断题', itemValue: '3' },
        { itemLabel: '填空题', itemValue: '4' },
        { itemLabel: '简答题', itemValue: '5' },
        { itemLabel: '名词解释', itemValue: '6' }
      ]
    })
  getDictItemsByCode('difficulty')
    .then((res) => { difficultyOptions.value = res.data || [] })
    .catch(() => {
      difficultyOptions.value = [
        { itemLabel: '简单', itemValue: '1' },
        { itemLabel: '中等', itemValue: '2' },
        { itemLabel: '困难', itemValue: '3' }
      ]
    })
})

/** 表单数据 */
const form = reactive({
  id: null,
  courseId: null,
  questionType: 1,
  content: '',
  options: null,      // 提交给后端的 JSON 字符串
  answer: '',
  analysis: '',
  difficulty: 2,
  score: 2.0,
  knowledgeIds: []    // 绑定知识点（选学科后多选，1~3 个，第一个为主知识点）
})

/** 选项数组（仅单选/多选用） */
const options = ref([
  { text: '', correct: false },
  { text: '', correct: false },
  { text: '', correct: false },
  { text: '', correct: false }
])

/** 单选题选中的选项索引 */
const singleCorrect = ref(0)

/** 字母映射：0→A, 1→B, 2→C, 3→D ... */
function letter(idx) {
  return String.fromCharCode(65 + idx)
}

/** 单选/多选的答案文本（用于只读显示） */
const selectionAnswer = computed(() => {
  if (form.questionType === 1) {
    // 单选
    return singleCorrect.value !== null ? letter(singleCorrect.value) : ''
  }
  if (form.questionType === 2) {
    // 多选
    const selected = options.value
      .map((o, i) => (o.correct ? letter(i) : null))
      .filter(Boolean)
    return selected.join(',')
  }
  return ''
})

/** 题型切换时重置选项和答案 */
function handleTypeChange() {
  if (form.questionType === 1 || form.questionType === 2) {
    // 切到选择题：如果选项为空，初始化 4 个
    if (options.value.length === 0) {
      options.value = [
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false }
      ]
    }
    singleCorrect.value = 0
  }
  form.answer = ''
}

function addOption() {
  options.value.push({ text: '', correct: false })
}

function removeOption(idx) {
  options.value.splice(idx, 1)
  // 如果删的是单选选中的项，重置选中
  if (form.questionType === 1 && singleCorrect.value === idx) {
    singleCorrect.value = 0
  }
}

/** 表单校验规则 */
const rules = {
  courseId: [{ required: true, message: '请选择学科', trigger: 'change' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  content: [{ required: true, message: '题干不能为空', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  score: [{ required: true, message: '请输入分值', trigger: 'blur' }],
  answer: [
    {
      validator: (rule, value, callback) => {
        // 单选/多选的答案从选项区来，不校验 answer 字段本身
        if (form.questionType === 1 || form.questionType === 2) {
          callback()
          return
        }
        if (!value) {
          callback(new Error('答案不能为空'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** 监听 props.question 变化，回显表单 */
watch(
  () => props.question,
  (q) => {
    if (q) {
      // 编辑模式：回显
      form.id = q.id
      form.questionType = q.questionType
      form.content = q.content
      form.answer = q.answer || ''
      form.analysis = q.analysis || ''
      form.difficulty = q.difficulty
      form.score = q.score
      form.courseId = q.courseId
      // 多知识点：优先用完整集合回显（列表接口已返回 knowledgeIds）；历史单知识点兼容为单元素数组
      form.knowledgeIds =
        q.knowledgeIds && q.knowledgeIds.length
          ? [...q.knowledgeIds]
          : q.knowledgeId
            ? [q.knowledgeId]
            : []

      // 加载该学科的知识点下拉（回显当前绑定的知识点）
      loadKnowledgeList(q.courseId)

      // 解析选项
      if (q.options) {
        try {
          const arr = JSON.parse(q.options)
          options.value = arr.map((text) => ({ text, correct: false }))
          // 回显正确答案
          if (form.questionType === 1) {
            // 单选：answer = "A" → 索引 0
            const idx = q.answer ? q.answer.charCodeAt(0) - 65 : 0
            singleCorrect.value = idx
          } else if (form.questionType === 2) {
            // 多选：answer = "A,B,C" → 勾选
            const corrects = (q.answer || '').split(',').map((s) => s.trim())
            options.value.forEach((o, i) => {
              o.correct = corrects.includes(letter(i))
            })
          }
        } catch (e) {
          console.warn('选项解析失败:', e)
        }
      } else {
        options.value = [
          { text: '', correct: false },
          { text: '', correct: false },
          { text: '', correct: false },
          { text: '', correct: false }
        ]
      }
    } else {
      // 新增模式：重置
      form.id = null
      form.courseId = null
      form.questionType = 1
      form.content = ''
      form.options = null
      form.answer = ''
      form.analysis = ''
      form.difficulty = 2
      form.score = 2.0
      form.courseId = 1
      form.knowledgeIds = []
      knowledgeList.value = []
      options.value = [
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false }
      ]
      singleCorrect.value = 0
    }
  },
  { immediate: true }
)

/** 弹窗打开且为新增模式时，应用预设的科目/题型（如从随机组卷跳转过来） */
watch(
  () => props.modelValue,
  (val) => {
    if (val && !isEdit.value && props.preset) {
      if (props.preset.courseId) {
        form.courseId = props.preset.courseId
        loadKnowledgeList(form.courseId)
      }
      if (props.preset.questionType) {
        form.questionType = props.preset.questionType
        handleTypeChange()
      }
    }
  }
)

/** 提交表单 */
async function handleSubmit() {
  try {
    // 先做表单基础校验
    await formRef.value.validate()

    // 选择题：校验选项内容和答案
    if (form.questionType === 1 || form.questionType === 2) {
      const emptyOpt = options.value.find((o) => !o.text || !o.text.trim())
      if (emptyOpt) {
        ElMessage.warning('请填写所有选项内容')
        return
      }
      if (!selectionAnswer.value) {
        ElMessage.warning('请选择正确答案')
        return
      }
      // 组装 options JSON 和 answer
      form.options = JSON.stringify(options.value.map((o) => o.text))
      form.answer = selectionAnswer.value
    } else {
      // 非选择题：options 为 null
      form.options = null
    }

    submitting.value = true

    if (isEdit.value) {
      await updateQuestion({ ...form })
      ElMessage.success('更新成功，已提交审核，请等待管理员审核')
      emit('success', { isAdd: false })
    } else {
      const res = await createQuestion({ ...form })
      ElMessage.success('新增成功，已提交审核，请等待管理员审核')
      // 把后端返回的新题 id 和题干带回去，父组件用于展示操作记录并支持"撤回新增"
      emit('success', { isAdd: true, id: res?.data, content: form.content })
    }

    emit('update:modelValue', false)
  } catch (err) {
    if (err?.message) {
      console.error('提交失败:', err)
      ElMessage.error('提交失败：' + (err.message || '未知错误'))
    }
    // validate 失败时不报错（表单会自己显示错误提示）
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* 知识点选择 + AI 匹配按钮并排 */
.knowledge-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.options-wrapper {
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.option-row :deep(.el-input) {
  flex: 1;
}
</style>
