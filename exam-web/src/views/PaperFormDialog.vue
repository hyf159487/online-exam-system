<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑试卷' : '新增试卷'"
    width="520px"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="试卷名称" prop="paperName">
        <el-input v-model="form.paperName" placeholder="如：Java 基础期中考试卷" maxlength="100" />
      </el-form-item>

      <el-form-item label="所属学科" prop="courseId">
        <el-select v-model="form.courseId" placeholder="请选择学科" style="width: 100%" filterable>
          <el-option
            v-for="c in courseList"
            :key="c.id"
            :label="c.courseName"
            :value="c.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="组卷方式" prop="paperType">
        <el-select v-model="form.paperType" placeholder="请选择组卷方式" style="width: 100%">
          <el-option v-for="o in paperTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>

      <el-form-item label="考试时长" prop="duration">
        <el-input-number v-model="form.duration" :min="10" :max="300" :step="10" />
        <span class="unit">分钟</span>
      </el-form-item>

      <el-alert
        v-if="!isEdit"
        title="总分与及格分都不用填：创建后进入组卷页选题，系统按题目分值自动累加总分，及格分 = 满分 × 60%"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 8px"
      />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        {{ isEdit ? '保存' : '新增' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createPaper, updatePaper } from '@/api/examPaper'
import { getCourseList } from '@/api/course'
import { usePaperTypeOptions } from '@/composables/usePaperTypeOptions'

const props = defineProps({
  // 保存成功后回调（新增时传新试卷 id，编辑时无参）
  saved: { type: Function, required: true },
  // 嵌入「课程信息 → 课程试卷」抽屉时传入：新增试卷默认归属该学科
  defaultCourseId: { type: [Number, String], default: null }
})

/** 组卷方式选项（字典 paper_type，字典缺失时回退到 1 手动 / 2 随机） */
const { paperTypeOptions, loadPaperTypeOptions } = usePaperTypeOptions()

const visible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

/** 学科下拉数据 */
const courseList = ref([])

onMounted(() => {
  loadPaperTypeOptions()
  getCourseList()
    .then((res) => {
      courseList.value = res.data || []
    })
    .catch((err) => console.error('获取学科列表失败:', err))
})

const form = reactive({
  id: null,
  paperName: '',
  paperType: 1,
  duration: 60,
  courseId: null
})

const rules = {
  paperName: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择学科', trigger: 'change' }],
  duration: [{ required: true, message: '请填写考试时长', trigger: 'blur' }]
}

/** 打开对话框：不传 row = 新增，传 row = 编辑 */
function open(row) {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, {
      id: row.id,
      paperName: row.paperName,
      paperType: row.paperType,
      duration: row.duration,
      courseId: row.courseId ?? null
    })
  } else {
    // 嵌入「课程信息 → 课程试卷」抽屉时，新增试卷默认归属本课程
    const cid = props.defaultCourseId != null && props.defaultCourseId !== '' ? Number(props.defaultCourseId) : null
    Object.assign(form, { id: null, paperName: '', paperType: 1, duration: 60, courseId: cid })
  }
  visible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function resetForm() {
  Object.assign(form, { id: null, paperName: '', paperType: 1, duration: 60, courseId: null })
  formRef.value?.resetFields()
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updatePaper({ ...form })
      ElMessage.success('保存成功')
      visible.value = false
      props.saved()
    } else {
      const res = await createPaper({ ...form })
      ElMessage.success('新增成功，接下来去组卷吧')
      visible.value = false
      // 把新试卷 id 和组卷方式传出去，列表页直接跳组卷（3 = AI 智能组卷则自动打开 AI 弹窗）
      props.saved(res.data, form.paperType)
    }
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.unit {
  margin-left: 8px;
  color: #909399;
}
</style>
