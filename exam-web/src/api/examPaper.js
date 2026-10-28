import request from '@/utils/request'

// 分页查询试卷列表（可选：名称模糊、状态筛选）
export function getPaperList(params) {
  return request.get('/api/exam-paper/list', { params })
}

// 试卷详情（基本信息 + 已选题目列表，组卷页回显用）
export function getPaperDetail(id) {
  return request.get(`/api/exam-paper/${id}`)
}

// 新增试卷（返回新试卷 id）
export function createPaper(data) {
  return request.post('/api/exam-paper', data)
}

// 更新试卷基本信息
export function updatePaper(data) {
  return request.put('/api/exam-paper', data)
}

// 删除试卷（连同题目关联）
export function deletePaper(id) {
  return request.delete(`/api/exam-paper/${id}`)
}

// 发布试卷（草稿 → 已发布）
export function publishPaper(id) {
  return request.put(`/api/exam-paper/${id}/publish`)
}

// 撤回试卷（已发布 → 草稿）
export function revokePaper(id) {
  return request.put(`/api/exam-paper/${id}/revoke`)
}

// 归档试卷（已发布 → 已归档）
export function archivePaper(id) {
  return request.put(`/api/exam-paper/${id}/archive`)
}

// 批量归档试卷
export function archivePaperBatch(ids) {
  return request.put('/api/exam-paper/batch-archive', null, { params: { ids: ids.join(',') } })
}

// 保存组卷结果（全量覆盖式）
export function savePaperQuestions(data) {
  return request.post('/api/exam-paper/questions', data)
}

// 随机组卷（按题型/难度/数量规则从题库随机抽题，覆盖式）
export function randomCompose(id, data) {
  return request.post(`/api/exam-paper/${id}/random-compose`, data)
}

// AI 智能组卷：组卷资源概览（题库可用题量 / 知识点题量 / 大模型启用状态）
export function getAiComposeMeta(id) {
  return request.get(`/api/exam-paper/${id}/ai-compose/meta`)
}

// AI 智能组卷（按目标总分/难度倾向/重点知识点自动生成方案并抽题，覆盖式）
export function aiCompose(id, data) {
  return request.post(`/api/exam-paper/${id}/ai-compose`, data)
}

// 批量删除试卷（连同题目关联）
export function deletePaperBatch(ids) {
  return request.delete('/api/exam-paper/batch', { params: { ids: ids.join(',') } })
}
