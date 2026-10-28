import axios from 'axios'
import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

/**
 * 试题审核管理 API
 */

/** 待审核 / 已驳回 试题列表（auditStatus：0待审核 2已驳回；管理员看全部，教师仅自己提交的题目） */
export function getAuditWaitList(params) {
  return request.get('/api/audit/waitList', { params })
}

/** 审核历史记录（管理员，支持审核结果/教师/审核人/时间范围/关键词筛选） */
export function getAuditHistoryList(params) {
  return request.get('/api/audit/historyList', { params })
}

/** 审核统计（统计卡片）：总审核条数、通过数、驳回数、重新提交次数 */
export function getAuditStats() {
  return request.get('/api/audit/stats')
}

/** 某道题的完整审核流水（时间正序，详情弹窗时间线用） */
export function getAuditLogs(questionId) {
  return request.get(`/api/audit/logs/${questionId}`)
}

/** 审核通过 */
export function auditPass(id) {
  return request.post(`/api/audit/pass/${id}`)
}

/** 审核驳回 */
export function auditReject(questionId, opinion) {
  return request.post('/api/audit/reject', { questionId, opinion })
}

/** 批量审核通过 */
export function auditBatchPass(ids) {
  return request.post('/api/audit/batchPass', { ids })
}

/** 批量审核驳回 */
export function auditBatchReject(ids, opinion) {
  return request.post('/api/audit/batchReject', { ids, opinion })
}

/** 导出审核历史 Excel（返回 axios response，response.data 为 Blob） */
export function exportAuditHistory(params) {
  const authStore = useAuthStore()
  return axios.get('/api/audit/export', {
    params,
    responseType: 'blob',
    headers: { Authorization: `Bearer ${authStore.token || ''}` }
  })
}

/** 试题详情（审核页用） */
export function getAuditDetail(id) {
  return request.get(`/api/audit/detail/${id}`)
}

/** 按角色查用户列表（筛选下拉用：role=1 教师，role=2 管理员） */
export function getUserListByRole(role) {
  return request.get('/api/sys-user/list', { params: { role } })
}

/** 教师重新提交审核 */
export function resubmitQuestion(id) {
  return request.post(`/api/question/resubmit/${id}`)
}
