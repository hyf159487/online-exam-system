<template>
  <div class="stats-export">
    <el-card shadow="never" class="export-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">学情报表导出</span>
          <span class="card-desc">将考试成绩、班级学情统计、试题质量统计导出为 Excel 文件，方便本地存档与线下分析</span>
        </div>
      </template>

      <!-- 筛选条件 -->
      <el-form :model="formData" label-width="120px" class="export-form">
        <el-form-item label="选择考试" required>
          <el-select
            v-model="formData.examId"
            placeholder="请选择考试"
            clearable
            filterable
            style="width: 100%"
            @change="onExamChange"
          >
            <el-option
              v-for="exam in examOptions"
              :key="exam.id"
              :label="exam.examName + ' - ' + (exam.courseName || '')"
              :value="exam.id"
            >
              <div class="exam-option">
                <span>{{ exam.examName }}</span>
                <span class="exam-sub">
                  {{ exam.courseName || '' }} · 参考 {{ exam.studentCount || 0 }} 人
                </span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="选择班级">
          <el-select
            v-model="formData.classId"
            placeholder="全部班级"
            clearable
            style="width: 100%"
            :disabled="!formData.examId"
          >
            <el-option
              v-for="cls in classOptions"
              :key="cls.classId"
              :label="cls.className"
              :value="cls.classId"
            />
          </el-select>
          <span class="form-tip">不选则导出全部班级数据</span>
        </el-form-item>

        <el-form-item label="报表类型" required>
          <el-radio-group v-model="formData.reportType">
            <el-radio :value="1">学生成绩报表</el-radio>
            <el-radio :value="2">班级学情统计报表</el-radio>
            <el-radio :value="3">试题质量报表</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 报表预览说明 -->
        <el-form-item label="报表内容">
          <div class="report-desc">
            <div v-if="formData.reportType === 1" class="desc-item">
              <el-icon class="desc-icon"><User /></el-icon>
              <div>
                <strong>学生成绩报表</strong>
                <p>包含：序号、姓名、学号、班级、总分、排名、交卷状态、交卷时间</p>
              </div>
            </div>
            <div v-else-if="formData.reportType === 2" class="desc-item">
              <el-icon class="desc-icon"><DataAnalysis /></el-icon>
              <div>
                <strong>班级学情统计报表</strong>
                <p>包含：参考人数、平均分、最高分、最低分、及格率、分数段分布</p>
              </div>
            </div>
            <div v-else-if="formData.reportType === 3" class="desc-item">
              <el-icon class="desc-icon"><Document /></el-icon>
              <div>
                <strong>试题质量报表</strong>
                <p>包含：题号、题型、题干摘要、满分、作答人数、正确/错误率、难度系数</p>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :disabled="!canExport"
            :loading="exporting"
            @click="handleExport"
          >
            <el-icon><Download /></el-icon>
            导出 Excel
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 提示卡片 -->
    <el-card shadow="never" class="tip-card">
      <template #header>
        <span class="card-title">使用说明</span>
      </template>
      <ul class="tip-list">
        <li><strong>权限说明：</strong>教师只能导出自己发布考试的数据，管理员可导出全部考试报表。</li>
        <li><strong>数据范围：</strong>仅导出已结束且学生已交卷的考试成绩数据。</li>
        <li><strong>报表内容：</strong>学生成绩报表包含每位学生的详细成绩排名；班级学情统计报表按班级维度汇总；试题质量报表分析每道试题的作答情况。</li>
        <li><strong>文件格式：</strong>导出文件为 .xlsx 格式，可直接用 Excel 打开编辑。</li>
      </ul>
    </el-card>

    <!-- 报表预览：选择筛选条件后自动加载，内容与导出的 Excel 完全一致 -->
    <el-card shadow="never" class="preview-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">报表预览</span>
          <span class="card-desc">
            {{ previewData
              ? previewData.title + '，共 ' + previewRows.length + ' 行（导出的 Excel 与预览内容一致）'
              : '选择考试、班级与报表类型后自动加载预览' }}
          </span>
        </div>
      </template>

      <div v-loading="previewLoading" class="preview-body">
        <template v-if="previewData && previewRows.length">
          <el-table
            :data="pagedPreviewRows"
            stripe
            border
            size="small"
            :scrollbar-always-on="true"
          >
            <!-- align="center" 同时作用于表头与内容：表头默认跟随列的对齐方式 -->
            <el-table-column
              v-for="(col, ci) in previewData.columns"
              :key="col.prop"
              :label="col.label"
              :min-width="col.width || 110"
              align="center"
              header-align="center"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row[ci] }}</template>
            </el-table-column>
          </el-table>

          <div class="preview-pager">
            <el-pagination
              v-model:current-page="previewPage"
              v-model:page-size="previewPageSize"
              :page-sizes="[10, 20, 50]"
              :total="previewRows.length"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="handlePreviewSizeChange"
            />
          </div>
        </template>

        <el-empty
          v-else-if="!previewLoading"
          :description="formData.examId
            ? '当前条件下没有可预览的数据（该考试可能还没有学生交卷）'
            : '请先选择考试，选择后这里会自动预览报表内容'"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, User, DataAnalysis, Document } from '@element-plus/icons-vue'
import { getExamList, getClassList } from '@/api/statsClass'
import { exportStats, downloadBlob, previewStats } from '@/api/statsExport'

const examOptions = ref([])
const classOptions = ref([])
const exporting = ref(false)

const formData = ref({
  examId: null,
  classId: null,
  reportType: 1
})

const canExport = computed(() => formData.value.examId && formData.value.reportType)

/* ===== 报表预览：筛选条件变化后自动刷新，内容与导出的 Excel 完全一致 ===== */
const previewLoading = ref(false)
const previewData = ref(null)
const previewPage = ref(1)
const previewPageSize = ref(10)
// 请求序号：快速切换条件时丢弃过期响应，避免旧数据覆盖新数据
let previewSeq = 0

const previewRows = computed(() => previewData.value?.rows || [])
const pagedPreviewRows = computed(() => {
  const start = (previewPage.value - 1) * previewPageSize.value
  return previewRows.value.slice(start, start + previewPageSize.value)
})

async function loadPreview() {
  // 未选考试 → 不请求，回到空态提示
  if (!formData.value.examId) {
    previewSeq++
    previewLoading.value = false
    previewData.value = null
    previewPage.value = 1
    return
  }
  const seq = ++previewSeq
  previewLoading.value = true
  try {
    const res = await previewStats({
      examId: formData.value.examId,
      classId: formData.value.classId || undefined,
      reportType: formData.value.reportType
    })
    if (seq !== previewSeq) return
    previewData.value = res.data || null
    previewPage.value = 1
  } catch {
    if (seq === previewSeq) previewData.value = null
  } finally {
    if (seq === previewSeq) previewLoading.value = false
  }
}

function handlePreviewSizeChange() {
  previewPage.value = 1
}

// 考试 / 班级 / 报表类型任一变化都重新预览
watch(
  () => [formData.value.examId, formData.value.classId, formData.value.reportType],
  () => loadPreview()
)

function onExamChange() {
  formData.value.classId = null
  classOptions.value = []
  if (formData.value.examId) {
    getClassList(formData.value.examId).then(res => {
      classOptions.value = res.data || []
    }).catch(() => {})
  }
}

function resetForm() {
  formData.value.examId = null
  formData.value.classId = null
  formData.value.reportType = 1
  classOptions.value = []
}

function getReportTypeName() {
  const names = {
    1: '学生成绩报表',
    2: '班级学情统计报表',
    3: '试题质量报表'
  }
  return names[formData.value.reportType] || '学情报表'
}

function getExamName() {
  const exam = examOptions.value.find(e => e.id === formData.value.examId)
  return exam ? exam.examName : '考试'
}

async function handleExport() {
  if (!canExport.value) return

  try {
    await ElMessageBox.confirm(
      `确定要导出「${getExamName()}」的${getReportTypeName()}吗？`,
      '导出确认',
      { confirmButtonText: '确定导出', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  exporting.value = true
  try {
    const params = {
      examId: formData.value.examId,
      reportType: formData.value.reportType
    }
    if (formData.value.classId) {
      params.classId = formData.value.classId
    }

    const response = await exportStats(params)
    const blob = response.data

    // 检查是否为错误响应（JSON格式）
    if (blob.type && blob.type.includes('json')) {
      const reader = new FileReader()
      reader.onload = (e) => {
        try {
          const err = JSON.parse(e.target.result)
          ElMessage.error(err.msg || '导出失败')
        } catch {
          ElMessage.error('导出失败')
        }
      }
      reader.readAsText(blob)
      return
    }

    // 生成文件名
    const examName = getExamName()
    const reportTypeName = getReportTypeName()
    const timestamp = new Date().toISOString().slice(0, 10)
    const filename = `${reportTypeName}_${examName}_${timestamp}.xlsx`

    downloadBlob(blob, filename)
    ElMessage.success('导出成功！')
  } catch (err) {
    if (err?.response?.data) {
      const reader = new FileReader()
      reader.onload = (e) => {
        try {
          const errData = JSON.parse(e.target.result)
          ElMessage.error(errData.msg || '导出失败')
        } catch {
          ElMessage.error('导出失败，请检查考试是否存在数据')
        }
      }
      reader.readAsText(err.response.data)
    } else {
      ElMessage.error('导出失败：' + (err.message || '网络错误'))
    }
  } finally {
    exporting.value = false
  }
}

function loadExams() {
  getExamList().then(res => {
    examOptions.value = res.data || []
  }).catch(err => {
    ElMessage.error(err?.message || '加载考试列表失败')
  })
}

onMounted(() => {
  loadExams()
})
</script>

<style scoped>
/* 铺满内容区全宽：不再限制最大宽度，表单控件随容器拉伸 */
.stats-export {
  padding: 4px;
}

.export-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.card-desc {
  font-size: 13px;
  color: #909399;
}

.export-form {
  padding: 8px 0;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  /* 下拉铺满后，说明文字另起一行，避免被挤压换行 */
  flex-basis: 100%;
  margin: 4px 0 0 0;
}

.report-desc {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  width: 100%;
  box-sizing: border-box;
}

.desc-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.desc-icon {
  font-size: 24px;
  color: #409eff;
  margin-top: 2px;
}

.desc-item strong {
  font-size: 14px;
  color: #303133;
}

.desc-item p {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.exam-option {
  display: flex;
  flex-direction: column;
}

.exam-sub {
  font-size: 12px;
  color: #909399;
}

.tip-card {
  background: #fdf6ec;
  border: 1px solid #faecd8;
}

.tip-list {
  margin: 0;
  padding-left: 20px;
  line-height: 2;
  font-size: 13px;
  color: #606266;
}

.tip-list strong {
  color: #303133;
}

/* ===== 报表预览卡片 ===== */
.preview-card {
  margin-top: 16px;
}

.preview-body {
  min-height: 100px;
}

.preview-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
