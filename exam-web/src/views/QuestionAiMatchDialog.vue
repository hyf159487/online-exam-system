<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="🤖 AI 智能匹配知识点"
    width="960px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-loading="matching">
      <el-alert
        v-if="!props.questions.length"
        type="warning"
        title="尚未勾选任何题目，请先在列表中勾选待匹配的题目（支持跨页勾选）"
        :closable="false"
      />

      <template v-else>
        <div class="ai-toolbar">
          <span>
            共 <b>{{ aiRows.length }}</b> 道题，AI 推荐知识点
            <b style="color: var(--el-color-primary)">{{ matchedCount }}</b> 道
            <span class="ai-source-tag">{{ sourceText }}</span>
          </span>
          <span class="ai-hint">每道题推荐 1~3 个知识点，第一个为主知识点，可在下拉中调整</span>
          <el-button size="small" :loading="matching" plain @click="runMatch">
            重新匹配
          </el-button>
        </div>

        <el-table :data="aiRows" border max-height="420" style="width: 100%">
          <el-table-column label="题干" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ summarize(row.question.content) }}</template>
          </el-table-column>

          <el-table-column label="当前知识点" width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <div v-if="curNames(row).length" class="kp-list">
                <el-tag
                  v-for="n in curNames(row)"
                  :key="n"
                  size="small"
                  type="info"
                  effect="plain"
                  class="kp-tag"
                >{{ n }}</el-tag>
              </div>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>

          <el-table-column label="AI 建议知识点（1~3）" min-width="240">
            <template #default="{ row }">
              <el-select
                v-model="row.matchKnowledgeIds"
                placeholder="未匹配"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                :multiple-limit="3"
                size="small"
                style="width: 100%"
                @change="(v) => onPick(row, v)"
              >
                <el-option
                  v-for="k in kpsOf(row.question.courseId)"
                  :key="k.id"
                  :label="k.pointName"
                  :value="k.id"
                />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="匹配理由" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="{ 'text-muted': !row.matchKnowledgeIds.length }">
                {{ row.reason || (row.matchKnowledgeIds.length ? '—' : '未匹配到合适知识点') }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="采用" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.adopt" />
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!aiRows.length"
        @click="handleApply"
      >
        应用（{{ adoptCount }} 道）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getKnowledgePointList } from '@/api/knowledgePoint'
import { aiMatchKnowledge, batchSetKnowledgeItems } from '@/api/question'

const props = defineProps({
  modelValue: Boolean,
  /** 已勾选的题目（含 id/courseId/content/knowledgeName/knowledgeNames 等字段） */
  questions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'success'])

const aiRows = ref([])
const matching = ref(false)
const submitting = ref(false)

/** 按学科缓存知识点列表，供"AI 建议"下拉手工调整 */
const kpCache = ref(new Map())

/** 匹配到知识点的题目数（匹配到 1~3 个均算命中） */
const matchedCount = computed(() => aiRows.value.filter((r) => r.matchKnowledgeIds && r.matchKnowledgeIds.length).length)

/** 采用（勾选且已匹配）的题目数 */
const adoptCount = computed(() => aiRows.value.filter((r) => r.adopt && r.matchKnowledgeIds && r.matchKnowledgeIds.length).length)

/** 匹配来源提示 */
const sourceText = computed(() => {
  const llm = aiRows.value.filter((r) => r.source === 'llm').length
  if (!llm) return '· 本地规则'
  return '· 大模型 ' + llm + ' 题'
})

function summarize(content) {
  if (!content) return ''
  return content.length > 40 ? content.slice(0, 40) + '…' : content
}

/** 题目当前绑定的知识点名称数组（优先 knowledgeNames，兜底主知识点名） */
function curNames(row) {
  const q = row.question || {}
  if (q.knowledgeNames) {
    return String(q.knowledgeNames).split('、').filter(Boolean)
  }
  if (q.knowledgeName) {
    return [q.knowledgeName]
  }
  return []
}

function kpsOf(courseId) {
  return kpCache.value.get(courseId) || []
}

async function ensureKps(courseId) {
  if (kpCache.value.has(courseId)) return kpCache.value.get(courseId)
  let list = []
  try {
    const res = await getKnowledgePointList(courseId)
    list = res.data || []
  } catch (e) {
    list = []
  }
  kpCache.value.set(courseId, list)
  return list
}

/** 用户手工调整知识点下拉时，按知识库过滤一次无效 id */
function onPick(row, v) {
  const valid = kpsOf(row.question.courseId).map((k) => k.id)
  row.matchKnowledgeIds = (v || []).filter((id) => valid.includes(id)).slice(0, 3)
}

/** 触发一次 AI 匹配 */
async function runMatch() {
  if (!props.questions.length) {
    aiRows.value = []
    return
  }
  matching.value = true
  try {
    const res = await aiMatchKnowledge(props.questions)
    const list = res.data || []
    aiRows.value = props.questions.map((q) => {
      const m = list.find((x) => x.questionId === q.id) || {}
      const ids = Array.isArray(m.knowledgeIds)
        ? (m.knowledgeIds || [])
        : m.knowledgeId
          ? [m.knowledgeId]
          : []
      return {
        question: q,
        matchKnowledgeIds: ids.slice(0, 3),
        reason: m.reason || (m.source === 'llm' ? '大模型匹配' : '本地规则匹配'),
        source: m.source || '',
        adopt: true
      }
    })
    // 预加载涉及学科的知识点，供下拉手工调整
    const courseIds = [...new Set(props.questions.map((q) => q.courseId).filter(Boolean))]
    await Promise.all(courseIds.map((c) => ensureKps(c)))
  } catch (e) {
    aiRows.value = []
    ElMessage.error('AI 匹配失败：' + (e?.message || '未知错误'))
  } finally {
    matching.value = false
  }
}

/** 应用：对"采用且已匹配"的题目，逐题保存各自的知识点组合 */
async function handleApply() {
  const pending = aiRows.value.filter((r) => r.adopt && r.matchKnowledgeIds && r.matchKnowledgeIds.length)
  if (!pending.length) {
    ElMessage.warning('没有要应用的知识点（请勾选"采用"并确认 AI 建议或手工选择）')
    return
  }
  const items = pending.map((r) => ({
    questionId: r.question.id,
    knowledgeIds: [...r.matchKnowledgeIds]
  }))

  submitting.value = true
  try {
    await batchSetKnowledgeItems(items)
    ElMessage.success(`已为 ${items.length} 道题设置知识点（每题 1~${items[0].knowledgeIds.length} 个）`)
    // 先关闭弹窗（destroy-on-close 会释放内部表格），再通知父组件刷新并清空勾选，
    // 否则父组件先清空勾选会让 props.questions 变空、弹窗内容闪一下"尚未勾选"提示
    emit('update:modelValue', false)
    emit('success')
  } catch (e) {
    ElMessage.error('应用失败：' + (e?.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

/** 打开弹窗时自动触发匹配 */
watch(
  () => props.modelValue,
  (val) => {
    if (val) runMatch()
  },
  { immediate: true }
)
</script>

<style scoped>
.ai-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.ai-source-tag {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-color-info);
}
.ai-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.kp-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.kp-tag {
  max-width: 130px;
}
.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>
