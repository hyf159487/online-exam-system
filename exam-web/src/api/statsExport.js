import axios from 'axios'
import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

/**
 * 学情报表导出 API
 * 导出使用原生 axios 处理文件流下载（blob）
 */

function getAuthConfig() {
  const authStore = useAuthStore()
  return {
    headers: {
      Authorization: `Bearer ${authStore.token || ''}`
    },
    responseType: 'blob'
  }
}

/**
 * 导出学情报表（Excel 文件流下载）
 * @param {Object} params - { examId, classId?, reportType: 1|2|3 }
 */
export function exportStats(params) {
  return axios.get('/api/stats/export', {
    params,
    ...getAuthConfig()
  })
}

/**
 * 报表预览：返回与导出的 Excel 完全一致的表头与数据
 * 响应 data 结构：{ title, total, columns: [{ prop, label, width }], rows: [[...]] }
 * @param {Object} params - { examId, classId?, reportType: 1|2|3 }
 */
export function previewStats(params) {
  return request.get('/api/stats/export/preview', { params })
}

/**
 * 触发浏览器下载
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
