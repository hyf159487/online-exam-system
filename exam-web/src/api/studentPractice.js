import request from '@/utils/request'

/** 学生端自主练习相关接口 */

// 一、按学科自由刷题

// 按筛选条件随机抽取练习题目：courseId + questionTypes/difficulties/knowledgeId/count + mode
// mode=answer 答题模式(不含答案)；mode=review 背题模式(含答案与解析)
export function getPracticeQuestions(params) {
  return request.get('/api/student/practice/questions', { params })
}

// 按课程查知识点（刷题条件「知识点下拉」用，只读）
export function getPracticeKnowledgePoints(courseId) {
  return request.get('/api/student/practice/knowledge-points', { params: { courseId } })
}

// 某学科练习统计：题库总题数 + 该学生已作答过的去重题数
export function getCoursePracticeStats(courseId) {
  return request.get('/api/student/practice/stat', { params: { courseId } })
}

// 某道题的练习统计：该学生总作答次数 + 答对次数（答题后展示「已做过几次/正确率」）
export function getQuestionPracticeStat(questionId) {
  return request.get('/api/student/practice/question-stat', { params: { questionId } })
}

// 背题模式：AI 智能讲解（生成首轮讲解）
export function explainPracticeQuestion(questionId) {
  return request.post(`/api/student/practice/questions/${questionId}/explain`)
}

// 背题模式：AI 多轮对话（携带历史消息继续追问；本地规则引擎仅支持首轮）
export function chatPracticeQuestion(questionId, data) {
  return request.post(`/api/student/practice/questions/${questionId}/chat`, data)
}

// 提交单题答案（判分 + 答错归集错题本）
export function submitPractice(data) {
  return request.post('/api/student/practice/submit', data)
}

// 错题自动移除策略：读取
export function getPracticeConfig() {
  return request.get('/api/student/practice/config')
}

// 错题自动移除策略：保存
export function savePracticeConfig(data) {
  return request.put('/api/student/practice/config', data)
}

// 二、教师发布的练习卷

/** 列出所有已发布练习卷（可选按学科筛选） */
export function getPublishedPracticePapers(params) {
  return request.get('/api/student/practice/papers', { params })
}

/** 取某张已发布练习卷的题目（不含正确答案和解析，防作弊） */
export function getPracticePaperDetail(id) {
  return request.get(`/api/student/practice/papers/${id}`)
}

/** 整套提交某张练习卷 → 判分 + 写练习记录 + 错题归集 */
export function submitPracticePaper(id, answers) {
  return request.post(`/api/student/practice/papers/${id}/submit`, { answers })
}

/**
 * 提交文件型作业：上传学生做好的作业文件（下载 → 线下作答 → 回传）
 * 同一份作业重复提交会覆盖上一版附件，并重新进入教师待批改列表
 * @param {number} id     作业ID
 * @param {File}   file   学生提交的作业文件
 * @param {string} remark 提交备注（选填）
 */
export function submitPaperAttachment(id, file, remark) {
  const form = new FormData()
  form.append('file', file)
  if (remark) form.append('remark', remark)
  return request.post(`/api/student/practice/papers/${id}/submit-attachment`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 作业文件可能较大，放宽到 5 分钟，避免上传被前端拦截器掐断
  })
}
