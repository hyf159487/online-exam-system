import { ref } from 'vue'
import { getDictItemsByCode } from '@/api/dict'

/**
 * 组卷方式选项（字典编码 paper_type，对应 exam_paper.paper_type / 后端 Integer paperType）
 *
 * - 编码约定：1 = 手动组卷，2 = 随机组卷
 * - 字典未配置（或被全部禁用）、接口异常时，回退到内置的 1 / 2，保证下拉框始终可用
 * - 首次调用从后端拉取，成功后在模块内缓存，多个页面共用同一份选项
 */

/** 字典缺失时的兜底选项 */
const FALLBACK_OPTIONS = [
  { label: '手动组卷', value: 1 },
  { label: '随机组卷', value: 2 }
]

/** 下拉选项：[{ label, value }]，value 为数字，与后端 Integer 字段保持一致 */
const paperTypeOptions = ref([...FALLBACK_OPTIONS])
/** 是否已成功读取过字典（失败不置位，下次调用可重试） */
let loaded = false
let loadingPromise = null

/**
 * 加载组卷方式字典（幂等，可重复调用）
 * @returns {Promise<Array<{label: string, value: number}>>}
 */
export function loadPaperTypeOptions() {
  if (loaded) return Promise.resolve(paperTypeOptions.value)
  if (loadingPromise) return loadingPromise
  loadingPromise = getDictItemsByCode('paper_type')
    .then((res) => {
      const items = (res.data || [])
        .map((it) => ({ label: it.itemLabel, value: Number(it.itemValue) }))
        .filter((it) => it.label && Number.isFinite(it.value))
      // 字典没配或全被禁用时保留兜底选项
      if (items.length) paperTypeOptions.value = items
      loaded = true
      return paperTypeOptions.value
    })
    .catch(() => paperTypeOptions.value)
    .finally(() => { loadingPromise = null })
  return loadingPromise
}

/**
 * 组卷方式文案
 * @param {number|string|null} value exam_paper.paper_type
 * @returns {string} 如「手动组卷」；字典里没有的值原样返回，空值返回 '-'
 */
export function paperTypeLabel(value) {
  const hit = paperTypeOptions.value.find((o) => o.value === Number(value))
  if (hit) return hit.label
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

/** 列表标签配色：1 手动=主色，2 随机=橙色，3 AI=绿色；字典新增的其他值统一橙色 */
const TAG_TYPE_MAP = { 1: 'primary', 2: 'warning', 3: 'success' }

/**
 * 列表标签配色
 * @param {number|string|null} value exam_paper.paper_type
 */
export function paperTypeTagType(value) {
  return TAG_TYPE_MAP[Number(value)] || 'warning'
}

/** 组合式函数：统一入口，供试卷相关页面使用 */
export function usePaperTypeOptions() {
  return { paperTypeOptions, loadPaperTypeOptions, paperTypeLabel, paperTypeTagType }
}
