<template>
  <div class="paper-template-page">
    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="模板名称">
          <el-input v-model="query.templateName" placeholder="请输入模板名称" clearable style="width:200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="所属课程">
          <el-select v-model="query.courseId" placeholder="全部" clearable filterable style="width:180px">
            <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="openAddDialog">新增模板</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (query.pageNum - 1) * query.pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="templateName" label="模板名称" min-width="240" show-overflow-tooltip />
        <el-table-column prop="courseName" label="所属课程" width="140" show-overflow-tooltip />
        <el-table-column prop="totalScore" label="总分" width="80" align="center" />
        <el-table-column prop="totalQuestionCount" label="总题数" width="90" align="center" />
        <el-table-column label="组卷方式" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="paperTypeTagType(row.paperType)">{{ paperTypeLabel(row.paperType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="110" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link size="small" @click="handleUse(row)">使用模板</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无组卷模板" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑模板弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑模板' : '新增模板'" width="680px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="例如：期末测试模板" maxlength="50" />
        </el-form-item>
        <el-form-item label="所属课程">
          <el-select v-model="form.courseId" placeholder="请选择课程" filterable clearable style="width:100%">
            <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷总分" prop="totalScore">
          <el-input-number v-model="form.totalScore" :min="10" :max="300" :step="5" />
        </el-form-item>

        <el-form-item label="组卷方式" prop="paperType">
          <el-select v-model="form.paperType" style="width: 100%">
            <el-option v-for="o in paperTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div class="form-tip">{{ paperTypeTip }}</div>
        </el-form-item>

        <template v-if="!isManual">
          <el-divider content-position="left">{{ isAi ? '组卷明细配置（AI 组卷的题型 / 分值参考）' : '组卷明细配置' }}</el-divider>

          <div v-for="(rule, idx) in form.rules" :key="idx" class="rule-item">
            <el-form :inline="true">
              <el-form-item label="题型">
                <el-select v-model="rule.questionType" placeholder="题型" style="width:120px">
                  <el-option v-for="(label, value) in typeMap" :key="value" :label="label" :value="Number(value)" />
                </el-select>
              </el-form-item>
              <el-form-item label="题数">
                <el-input-number v-model="rule.count" :min="1" :max="200" style="width:120px" />
              </el-form-item>
              <el-form-item label="每题分">
                <el-input-number v-model="rule.score" :min="0.5" :max="100" :step="0.5" :precision="1" style="width:130px" />
              </el-form-item>
              <el-form-item label="难度">
                <el-select v-model="rule.difficulty" placeholder="不限" clearable style="width:110px">
                  <el-option label="简单" :value="1" />
                  <el-option label="中等" :value="2" />
                  <el-option label="困难" :value="3" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button link type="danger" :icon="Delete" @click="removeRule(idx)">删除</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="rule-ops">
            <el-button :icon="Plus" @click="addRule">添加规则</el-button>
            <span class="rule-summary">
              共 {{ ruleTotalCount }} 题 · 总分 {{ ruleTotalScore }} 分
            </span>
          </div>
        </template>

        <el-form-item v-if="isAi" label="AI 补充说明">
          <el-input
            v-model="form.aiRequirement"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例：侧重第三章与多线程，计算与综合分析类题目多一点，避免反复考察同一知识点"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 使用模板弹窗：选择要创建的试卷信息 -->
    <el-dialog v-model="useVisible" title="使用模板创建试卷" width="480px">
      <div v-if="currentTemplate" class="use-tip">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="模板名称">{{ currentTemplate.templateName }}</el-descriptions-item>
          <el-descriptions-item label="总分">{{ currentTemplate.totalScore }} 分</el-descriptions-item>
          <el-descriptions-item label="组卷方式">
            <el-tag size="small" :type="paperTypeTagType(currentTemplate.paperType)">
              {{ paperTypeLabel(currentTemplate.paperType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总题数">
            {{ isManualTemplate ? '手动选题' : `${currentTemplate.totalQuestionCount} 题` }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-form v-if="useForm" :model="useForm" :rules="useRules" ref="useFormRef" label-width="90px" style="margin-top: 12px">
        <el-form-item label="试卷名称" prop="paperName">
          <el-input v-model="useForm.paperName" placeholder="请输入试卷名称" />
        </el-form-item>
        <el-form-item label="所属课程">
          <el-select v-model="useForm.courseId" placeholder="请选择课程" filterable clearable style="width: 100%">
            <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试时长" prop="duration">
          <el-input-number v-model="useForm.duration" :min="10" :max="300" :step="10" />
          <span style="margin-left: 8px">分钟</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="useVisible = false">取消</el-button>
        <el-button type="primary" :loading="useLoading" @click="confirmUseTemplate">创建并组卷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { getTemplateList, getTemplateDetail, createTemplate, updateTemplate, deleteTemplate } from '@/api/paperTemplate'
import { getCourseList } from '@/api/course'
import { createPaper } from '@/api/examPaper'
import { usePaperTypeOptions } from '@/composables/usePaperTypeOptions'

const router = useRouter()

const typeMap = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }

/** 组卷方式选项（字典 paper_type：1手动 2随机 3AI智能）与展示工具 */
const { paperTypeOptions, loadPaperTypeOptions, paperTypeLabel, paperTypeTagType } = usePaperTypeOptions()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  templateName: '',
  courseId: null
})

const courseList = ref([])
const formVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  templateName: '',
  courseId: null,
  totalScore: 100,
  /** 组卷方式：1手动 2随机 3AI智能 */
  paperType: 2,
  rules: [{ questionType: 1, count: 10, score: 2, difficulty: 2 }],
  /** AI 智能组卷的补充说明（paperType=3 时可填） */
  aiRequirement: '',
  remark: ''
})

const rules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  totalScore: [{ required: true, message: '请输入总分', trigger: 'blur' }]
}

const useVisible = ref(false)
const useLoading = ref(false)
const useFormRef = ref()
const currentTemplate = ref(null)

const useForm = reactive({
  paperName: '',
  courseId: null,
  duration: 60
})

const useRules = {
  paperName: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
  duration: [{ required: true, message: '请输入考试时长', trigger: 'blur' }]
}

onMounted(() => {
  loadList()
  loadCourses()
  loadPaperTypeOptions()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getTemplateList(query)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    ElMessage.error(e?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCourses() {
  try {
    const res = await getCourseList()
    courseList.value = res.data || []
  } catch {}
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function resetQuery() {
  query.templateName = ''
  query.courseId = null
  query.pageNum = 1
  loadList()
}

function openAddDialog() {
  isEdit.value = false
  form.id = null
  form.templateName = ''
  form.courseId = null
  form.totalScore = 100
  form.paperType = 2
  form.rules = [{ questionType: 1, count: 10, score: 2, difficulty: 2 }]
  form.aiRequirement = ''
  form.remark = ''
  formVisible.value = true
}

async function handleEdit(row) {
  isEdit.value = true
  try {
    const res = await getTemplateDetail(row.id)
    const tpl = res.data
    form.id = tpl.id
    form.templateName = tpl.templateName
    form.courseId = tpl.courseId
    form.totalScore = tpl.totalScore
    form.paperType = Number(tpl.paperType) || 2
    form.aiRequirement = tpl.aiRequirement || ''
    form.remark = tpl.remark || ''
    // 解析 templateJson（手动组卷模板没有明细，回填一条默认规则备用）
    const tplRules = tpl.templateJson ? JSON.parse(tpl.templateJson) : []
    form.rules = Array.isArray(tplRules) && tplRules.length
      ? tplRules
      : [{ questionType: 1, count: 10, score: 2, difficulty: 2 }]
    formVisible.value = true
  } catch (e) {
    ElMessage.error(e?.msg || '加载失败')
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除模板「${row.templateName}」吗？`, '删除确认', {
    type: 'warning'
  }).then(async () => {
    try {
      await deleteTemplate(row.id)
      ElMessage.success('删除成功')
      loadList()
    } catch (e) {
      ElMessage.error(e?.msg || '删除失败')
    }
  }).catch(() => {})
}

function addRule() {
  form.rules.push({ questionType: 1, count: 5, score: 2, difficulty: 2 })
}

function removeRule(idx) {
  form.rules.splice(idx, 1)
}

const ruleTotalCount = computed(() => {
  return form.rules.reduce((sum, r) => sum + (r.count || 0), 0)
})

const ruleTotalScore = computed(() => {
  return form.rules.reduce((sum, r) => sum + (r.count || 0) * (r.score || 0), 0).toFixed(1)
})

/** 手动组卷：模板只保存试卷基本信息，不需要组卷明细 */
const isManual = computed(() => Number(form.paperType) === 1)
/** AI 智能组卷：除明细外还可填补充说明 */
const isAi = computed(() => Number(form.paperType) === 3)

/** 组卷方式说明：让教师清楚「使用模板」时会发生什么 */
const paperTypeTip = computed(() => {
  if (isManual.value) return '使用模板时只创建试卷（不含题目），题目到组卷页逐题挑选'
  if (isAi.value) return '使用模板时按下方明细 + AI 补充说明，由 AI 生成组卷方案并自动抽题'
  return '使用模板时按下方组卷明细自动抽题'
})

/** 「使用模板」弹窗里：当前模板是否为手动组卷（手动模板没有题数） */
const isManualTemplate = computed(() => Number(currentTemplate.value?.paperType) === 1)

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  // 手动组卷模板不需要明细；随机 / AI 智能组卷必须至少配置一条规则
  if (!isManual.value && !form.rules.length) {
    ElMessage.warning('请至少配置一条组卷规则')
    return
  }

  submitLoading.value = true
  try {
    const data = {
      id: form.id,
      templateName: form.templateName,
      courseId: form.courseId,
      totalScore: form.totalScore,
      paperType: form.paperType,
      // 手动组卷没有明细：题数记 0、明细存空数组，避免列表出现误导性的题数
      totalQuestionCount: isManual.value ? 0 : ruleTotalCount.value,
      templateJson: isManual.value ? '[]' : JSON.stringify(form.rules),
      // 补充说明只在 AI 智能组卷时保存
      aiRequirement: isAi.value ? (form.aiRequirement?.trim() || null) : null,
      remark: form.remark
    }

    if (isEdit.value) {
      await updateTemplate(data)
      ElMessage.success('修改成功')
    } else {
      await createTemplate(data)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    loadList()
  } catch (e) {
    ElMessage.error(e?.msg || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

function handleUse(row) {
  currentTemplate.value = row
  useForm.paperName = row.templateName.replace('模板', '') || '试卷'
  useForm.courseId = row.courseId
  useForm.duration = 60
  useVisible.value = true
}

async function confirmUseTemplate() {
  if (!useFormRef.value) return
  try {
    await useFormRef.value.validate()
  } catch {
    return
  }
  useLoading.value = true
  try {
    // 创建试卷：组卷方式沿用模板，这样列表标签与之后点「组卷」的行为才一致
    // 及格分不用传：后端统一按「满分 × 60%」自动换算
    const paperData = {
      paperName: useForm.paperName,
      courseId: useForm.courseId,
      paperType: Number(currentTemplate.value?.paperType) || 2,
      duration: useForm.duration
    }
    const res = await createPaper(paperData)
    const paperId = res.data
    useVisible.value = false
    // 跳转到组卷页并带上 templateId：组卷页按模板的组卷方式决定是否弹出随机 / AI 弹窗
    router.push({
      path: `/papers/${paperId}/compose`,
      query: { templateId: currentTemplate.value.id }
    })
  } catch (e) {
    ElMessage.error(e?.msg || '创建试卷失败')
  } finally {
    useLoading.value = false
  }
}
</script>

<style scoped>
.filter-card { margin-bottom: 12px; }
.table-toolbar { margin-bottom: 12px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.rule-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #fafafa;
}
.rule-ops {
  margin-top: 8px;
  padding: 8px 0;
  display: flex;
  align-items: center;
  gap: 16px;
}
.rule-summary { color: #909399; font-size: 13px; }
.form-tip { color: #909399; font-size: 12px; line-height: 1.5; margin-top: 2px; }
.use-tip { margin-bottom: 8px; }
</style>
