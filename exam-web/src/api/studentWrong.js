import request from '@/utils/request'

// 学生端错题本：分页列表（可按学科筛选）
export function getMyWrongQuestions(params) {
  return request.get('/api/student/wrong-questions', { params })
}

// 学生端错题本：按学科统计数量
export function getMyWrongStats() {
  return request.get('/api/student/wrong-questions/stats')
}

// 学生端错题本：总览统计（错题总数 / 已做对过 / 累计答错次数 / 各学科分布）
export function getWrongOverview() {
  return request.get('/api/student/wrong-questions/overview')
}

// 学生端错题本：批量移除（body 为 id 数组）
export function batchRemoveWrongQuestions(ids) {
  return request.post('/api/student/wrong-questions/batch-remove', ids)
}

// 学生端错题本：导出当前筛选条件下的错题 Excel（返回 blob）
export function exportWrongQuestions(params) {
  return request.get('/api/student/wrong-questions/export', { params, responseType: 'blob' })
}

// 学生端错题本：重做正确后移除一条
export function removeWrongQuestion(id) {
  return request.delete(`/api/student/wrong-questions/${id}`)
}

// 学生端错题本：重做一道错题（判分 + 按学生策略决定是否自动移出）
export function redoWrongQuestion(id, data) {
  return request.post(`/api/student/wrong-questions/${id}/redo`, data)
}

// 学生端错题本：AI 错题讲解（大模型生成，未配置 key 时本地规则降级）
export function explainWrongQuestion(id) {
  return request.post(`/api/student/wrong-questions/${id}/explain`)
}

// 学生端错题本：AI 多轮对话讲解（携带历史消息继续追问；本地规则引擎仅支持首轮）
export function chatWrongQuestion(id, data) {
  return request.post(`/api/student/wrong-questions/${id}/chat`, data)
}
