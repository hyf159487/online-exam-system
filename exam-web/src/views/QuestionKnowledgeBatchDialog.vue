<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="批量设置知识点"
    width="480px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="kb-batch">
      <el-alert
        v-if="!props.questions.length"
        type="warning"
        title="尚未勾选任何题目，请先在列表中勾选待设置的题目（支持跨页勾选）"
        :closable="false"
      />
      <template v-else>
        <p class="kb-tip">将为 <b>{{ props.questions.length }}</b> 道题统一设置同一组知识点（可选 1~3 个，第一个为主知识点）。</p>

        <el-alert
          v-if="multiCourse"
          type="info"
          title="所选题目包含多个学科，请先选择要绑定知识点的学科"
          :closable="false"
          style="margin-bottom: 12px"
        />

        <el-form label-width="80px">
          <el-form-item label="学科">
            <el-select
              v-model="selCourseId"
              placeholder="请选择学科"
              filterable
              style="width: 100%"
              @change="loadKnowledgeList"
            >
              <el-option
                v-for="c in courseList"
                :key="c.id"
                :label="c.courseName"
                :value="c.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="知识点">
            <el-select
              v-model="selKnowledgeIds"
              placeholder="请选择知识点（可选 1~3 个）"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              :multiple-limit="3"
              :loading="kpLoading"
              :disabled="!selCourseId"
              style="width: 100%"
            >
              <el-option
                v-for="k in knowledgeList"
                :key="k.id"
                :label="k.pointName"
                :value="k.id"
              />
            </el-select>
          </el-form-item>
        </el-form>

        <el-alert
          type="info"
          title="仅更新知识点，不改动该题的题干、答案与审核状态"
          :closable="false"
        />
      </template>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!props.questions.length || !selKnowledgeIds.length"
        @click="handleSubmit"
      >
        确认设置
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getCourseList } from '@/api/course'
import { getKnowledgePointList } from '@/api/knowledgePoint'
import { batchSetKnowledge } from '@/api/question'

const props = defineProps({
  modelValue: Boolean,
  /** 已勾选的题目（含 id、courseId 等字段） */
  questions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'success'])

const courseList = ref([])
const knowledgeList = ref([])
const kpLoading = ref(false)
const selCourseId = ref(null)
const selKnowledgeIds = ref([])
const submitting = ref(false)

/** 所选题目是否包含多个学科 */
const multiCourse = computed(() => {
  const set = new Set(props.questions.map((q) => q.courseId).filter(Boolean))
  return set.size > 1
})

/** 加载学科下拉 */
function loadCourseList() {
  getCourseList()
    .then((res) => { courseList.value = res.data || [] })
    .catch((err) => console.error('获取学科列表失败:', err))
}

/** 按学科加载知识点 */
async function loadKnowledgeList(courseId) {
  selKnowledgeIds.value = []
  knowledgeList.value = []
  if (!courseId) return
  kpLoading.value = true
  try {
    const res = await getKnowledgePointList(courseId)
    knowledgeList.value = res.data || []
  } catch (e) {
    knowledgeList.value = []
    ElMessage.error('知识点加载失败')
  } finally {
    kpLoading.value = false
  }
}

/** 弹窗打开时初始化：若所选题目都是同一学科，自动带入该学科并加载知识点 */
watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    if (!courseList.value.length) loadCourseList()
    const set = new Set(props.questions.map((q) => q.courseId).filter(Boolean))
    if (set.size === 1) {
      selCourseId.value = [...set][0]
      loadKnowledgeList(selCourseId.value)
    } else {
      selCourseId.value = null
      knowledgeList.value = []
    }
  },
  { immediate: true }
)

/** 提交：批量设置统一知识点组 */
async function handleSubmit() {
  submitting.value = true
  try {
    const ids = props.questions.map((q) => q.id)
    await batchSetKnowledge({ ids, knowledgeIds: [...selKnowledgeIds.value] })
    ElMessage.success(`已为 ${ids.length} 道题设置知识点`)
    // 先关闭弹窗，再通知父组件刷新并清空勾选：避免先清空勾选时，弹窗内容闪成"未勾选题目"的提示
    emit('update:modelValue', false)
    emit('success')
  } catch (e) {
    ElMessage.error('设置失败：' + (e?.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.kb-batch {
  display: flex;
  flex-direction: column;
}
.kb-tip {
  margin: 0 0 12px;
  color: var(--el-text-color-regular);
}
</style>
