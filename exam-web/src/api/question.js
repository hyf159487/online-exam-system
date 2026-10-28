import request from '@/utils/request'

// 分页查询题目列表
export function getQuestionList(params) {
  return request.get('/api/question/list', { params })
}

// 按 ID 查单个题目（编辑回显用）
export function getQuestionById(id) {
  return request.get(`/api/question/${id}`)
}

// 新增题目
export function createQuestion(data) {
  return request.post('/api/question', data)
}

// 更新题目
export function updateQuestion(data) {
  return request.put('/api/question', data)
}

// 删除题目
export function deleteQuestion(id) {
  return request.delete(`/api/question/${id}`)
}

// 批量删除题目（传 id 数组）
export function batchDeleteQuestions(ids) {
  return request.post('/api/question/batchDelete', ids)
}

// 批量新增题目
export function batchCreateQuestions(data) {
  return request.post('/api/question/batch', data)
}

// ==================== AI 难度评估 ====================

/**
 * AI 批量评估题目难度
 * @param {Array} questions - 题目数组（含 questionType/content/options/answer/analysis 等字段）
 * @returns {Promise<Array>} 评估结果数组，每项：{ difficulty, difficultyText, source, reason, model }
 */
export function aiEvaluateDifficulty(questions) {
  return request.post('/api/question/aiEvaluateDifficulty', questions)
}

/**
 * AI 评估单道题难度
 * @param {Object} question - 单道题目
 */
export function aiEvaluateDifficultyOne(question) {
  return request.post('/api/question/aiEvaluateDifficultyOne', question)
}

// ==================== 批量设置 / AI 智能匹配知识点 ====================

/**
 * 批量设置题目知识点（把选中的题目统一绑定到同一组知识点，1~3 个，第一个为主知识点）
 * @param {Object} data - { ids: [题目ID数组], knowledgeIds: [知识点ID数组] }
 */
export function batchSetKnowledge(data) {
  return request.post('/api/question/batchSetKnowledge', data)
}

/**
 * 逐题保存知识点组合（AI 智能匹配"应用"：每道题绑定各自匹配到的 1~3 个知识点）
 * @param {Array} list - [{ questionId, knowledgeIds: [知识点ID数组] }, ...]
 */
export function batchSetKnowledgeItems(list) {
  return request.post('/api/question/batchSetKnowledgeItems', list)
}

/**
 * AI 智能匹配知识点：根据题干/选项/答案/解析，从所属学科知识点中为每道题推荐 1~3 个
 * @param {Array} questions - 题目数组（含 id/courseId/content/options/answer/analysis）
 * @returns {Promise<Array>} 匹配建议数组：{ questionId, knowledgeIds, knowledgeNames, reason, source, knowledgeId(主) }
 */
export function aiMatchKnowledge(questions) {
  return request.post('/api/question/aiMatchKnowledge', questions, { timeout: 120000 })
}

// ==================== 统一文件解析接口 ====================

// 统一文件导入（支持 Word/Excel/PDF/JSON/图片）
// timeout 90s：大文件/网络慢时避免请求被前端拦截器中断，后端 applyAiDifficulty 内部已做 5s 总超时降级
export function importFileQuestions(file, courseId) {
  const formData = new FormData()
  formData.append('file', file)
  if (courseId) formData.append('courseId', courseId)
  return request.post('/api/question/importFile', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 90000
  })
}

// 统一文本解析（type: 'word' 或 'json'）
export function parseTextQuestions(type, content, courseId) {
  return request.post('/api/question/parseText', { type, content, courseId }, {
    timeout: 60000
  })
}

// ==================== 模板下载接口 ====================

/**
 * 下载导入模板（Excel / JSON / TXT）
 * @param {'excel'|'json'|'txt'} type - 模板类型
 */
export function downloadQuestionTemplate(type) {
  // 后端已处理 Content-Disposition 文件名，直接用 a 标签下载即可
  // request 拦截器已自动带上 Token
  return request.get(`/api/question/template/${type}`, {
    responseType: 'blob'
  })
}

// ==================== 旧接口（向后兼容） ====================

// 从 Word 文档解析题目（上传文件，返回解析结果）
export function importWordQuestions(file, courseId) {
  return importFileQuestions(file, courseId)
}

// 从纯文本解析题目（粘贴 Word 文本）
export function importTextQuestions(text, courseId) {
  return parseTextQuestions('word', text, courseId)
}
