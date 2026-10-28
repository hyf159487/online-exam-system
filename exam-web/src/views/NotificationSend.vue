<template>
  <div class="notice-send">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">发布通知</span>
          <span class="card-desc">{{ isAdmin ? '向教师或学生推送站内消息：支持全体学生、指定班级、指定考试考生、全体教师' : '向学生推送站内消息：支持指定班级、指定考试考生' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="120px"
        class="notice-form"
      >
        <el-form-item label="通知标题" prop="title">
          <el-input
            v-model="formData.title"
            placeholder="请输入通知标题，如：期末考试提醒"
            maxlength="100"
            show-word-limit
            clearable
          />
        </el-form-item>

        <el-form-item label="通知内容" prop="content">
          <el-input
            v-model="formData.content"
            type="textarea"
            :rows="8"
            placeholder="请输入通知正文内容..."
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="接收对象" prop="receiveType">
          <el-radio-group v-model="formData.receiveType" @change="onReceiveTypeChange">
            <el-radio :value="2">指定班级</el-radio>
            <el-radio :value="3">指定考试考生</el-radio>
            <el-radio v-if="isAdmin" :value="1">全部学生</el-radio>
            <el-radio v-if="isAdmin" :value="4">全体教师</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 指定班级 -->
        <el-form-item
          v-if="formData.receiveType === 2"
          label="选择班级"
          prop="targetClassIds"
        >
          <el-select
            v-model="formData.targetClassIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择班级"
            style="width: 100%"
          >
            <el-option
              v-for="cls in classOptions"
              :key="cls.id"
              :label="cls.className + (cls.grade ? '（' + cls.grade + '）' : '')"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>

        <!-- 指定考试 -->
        <el-form-item
          v-if="formData.receiveType === 3"
          label="选择考试"
          prop="targetExamId"
        >
          <el-select
            v-model="formData.targetExamId"
            placeholder="请选择考试"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="exam in examOptions"
              :key="exam.id"
              :label="exam.examName + ' - ' + (exam.courseName || '')"
              :value="exam.id"
            >
              <div class="exam-option">
                <span>{{ exam.examName }}</span>
                <span class="exam-sub">{{ exam.courseName || '' }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <!-- 预览 -->
        <el-form-item label="发送预览">
          <div class="preview-box">
            <div class="preview-row">
              <span class="preview-label">接收范围：</span>
              <span class="preview-value">{{ previewRangeText }}</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="sending" @click="handleSubmit">
            <el-icon><Promotion /></el-icon>发送通知
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { sendNotice } from '@/api/notice'
import { getExamList } from '@/api/statsQuestion'
import request from '@/utils/request'

const formRef = ref(null)
const sending = ref(false)
const classOptions = ref([])
const examOptions = ref([])

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.userInfo?.role === 2)

const formData = ref({
  title: '',
  content: '',
  receiveType: 2,
  targetClassIds: [],
  targetExamId: null
})

const rules = {
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }],
  receiveType: [{ required: true, message: '请选择接收对象', trigger: 'change' }],
  targetClassIds: [
    {
      type: 'array',
      required: true,
      message: '请至少选择一个班级',
      trigger: 'change',
      validator: (_rule, value, callback) => {
        if (formData.value.receiveType === 2 && (!value || value.length === 0)) {
          callback(new Error('请至少选择一个班级'))
        } else {
          callback()
        }
      }
    }
  ],
  targetExamId: [
    {
      required: true,
      message: '请选择考试',
      trigger: 'change',
      validator: (_rule, value, callback) => {
        if (formData.value.receiveType === 3 && !value) {
          callback(new Error('请选择考试'))
        } else {
          callback()
        }
      }
    }
  ]
}

const previewRangeText = computed(() => {
  const t = formData.value.receiveType
  if (t === 1) return '全部学生'
  if (t === 4) return '全体教师'
  if (t === 2) {
    const ids = formData.value.targetClassIds || []
    if (ids.length === 0) return '指定班级（未选择）'
    const names = []
    for (const id of ids) {
      const cls = classOptions.value.find(c => c.id === id)
      if (cls) names.push(cls.className)
    }
    return `指定班级（${names.join('、') || ids.length + '个班级'}）`
  }
  if (t === 3) {
    const id = formData.value.targetExamId
    if (!id) return '指定考试（未选择）'
    const exam = examOptions.value.find(e => e.id === id)
    return `指定考试：${exam ? exam.examName : '考试ID=' + id}`
  }
  return '未设置'
})

function onReceiveTypeChange() {
  formData.value.targetClassIds = []
  formData.value.targetExamId = null
}

function loadOptions() {
  // 加载班级列表
  request.get('/api/class').then(res => {
    classOptions.value = res.data || []
  }).catch(() => {})

  // 加载考试列表（复用统计模块的接口）
  getExamList().then(res => {
    examOptions.value = res.data || []
  }).catch(() => {})
}

function handleReset() {
  formData.value = {
    title: '',
    content: '',
    receiveType: 2,
    targetClassIds: [],
    targetExamId: null
  }
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要发送此通知吗？\n${previewRangeText.value}`,
      '发送确认',
      { confirmButtonText: '确定发送', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  sending.value = true
  try {
    const payload = {
      title: formData.value.title,
      content: formData.value.content,
      receiveType: formData.value.receiveType
    }
    if (formData.value.receiveType === 2) {
      payload.targetClassIds = formData.value.targetClassIds
    } else if (formData.value.receiveType === 3) {
      payload.targetExamId = formData.value.targetExamId
    }
    await sendNotice(payload)
    ElMessage.success('发送成功！')
    handleReset()
  } catch (err) {
    ElMessage.error(err?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  loadOptions()
})
</script>

<style scoped>
/* 铺满内容区全宽：不再限制卡片最大宽度，表单控件也随容器拉伸 */
.notice-send {
  padding: 4px;
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

.notice-form {
  padding: 8px 0;
}

.preview-box {
  padding: 12px 16px;
  background: linear-gradient(135deg, #ecf5ff, #f0f9eb);
  border: 1px dashed #a0cfff;
  border-radius: 8px;
  width: 100%;
  box-sizing: border-box;
}

.preview-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-label {
  color: #606266;
  font-size: 13px;
}

.preview-value {
  color: #409eff;
  font-weight: 600;
  font-size: 14px;
}

.exam-option {
  display: flex;
  flex-direction: column;
}

.exam-sub {
  font-size: 12px;
  color: #909399;
}
</style>
