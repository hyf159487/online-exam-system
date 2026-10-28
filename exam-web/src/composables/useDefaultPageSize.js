import { ref } from 'vue'
import { getConfigValue } from '@/api/config'

const defaultPageSize = ref(null)
let loadingPromise = null

/**
 * 获取默认分页条数（系统参数 default_page_size）
 * 首次调用时从后端加载，后续直接返回缓存值
 * @returns {Promise<number>}
 */
export function useDefaultPageSize() {
  if (defaultPageSize.value !== null) {
    return Promise.resolve(defaultPageSize.value)
  }
  if (loadingPromise) return loadingPromise
  loadingPromise = getConfigValue('default_page_size')
    .then(res => {
      const val = parseInt(res.data, 10)
      defaultPageSize.value = val > 0 ? val : 10
      return defaultPageSize.value
    })
    .catch(() => {
      defaultPageSize.value = 10
      return 10
    })
    .finally(() => { loadingPromise = null })
  return loadingPromise
}
