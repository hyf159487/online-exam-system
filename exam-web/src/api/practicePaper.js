import request from '@/utils/request'

// ========== 练习卷 ==========

/** 练习卷分页列表 */
export function listPracticePaper(params) {
  return request.get('/api/practice/paper/list', { params })
}

/** 练习卷详情（含题目） */
export function getPracticePaperDetail(id) {
  return request.get(`/api/practice/paper/detail/${id}`)
}

/** 随机选题（按题型/难度/数量从题库随机抽题，返回题目列表，不落库） */
export function randomPickPracticePaper(data) {
  return request.post('/api/practice/paper/random-pick', data)
}

/** 新增/编辑练习卷 */
export function savePracticePaper(data) {
  return request.post('/api/practice/paper/save', data)
}

/**
 * 上传作业附件（文件型作业：作业就是一份文件，学生下载或在线查看后线下作答）
 * 只落盘并返回元数据，保存作业时把返回值一并提交给 savePracticePaper
 * timeout 拉长到 5 分钟，避免大文件上传被前端拦截器掐断
 */
export function uploadPracticeAttachment(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/practice/paper/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

/** 发布/停用 */
export function publishPracticePaper(id, status) {
  return request.put(`/api/practice/paper/publish/${id}`, null, { params: { status } })
}

/** 删除练习卷 */
export function deletePracticePaper(id) {
  return request.delete(`/api/practice/paper/delete/${id}`)
}

// ========== 练习记录 ==========

/** 练习记录列表 */
export function listPracticeRecord(params) {
  return request.get('/api/practice/record/list', { params })
}

/** 练习答题详情 */
export function getPracticeRecordDetail(recordId) {
  return request.get(`/api/practice/record/detail/${recordId}`)
}

// ========== 批改作业 ==========

/** 批改作业列表（reviewStatus：1待批改 2已批改，不传=全部） */
export function listPracticeReview(params) {
  return request.get('/api/practice/review/list', { params })
}

/** 提交批改：items = [{ answerId, reviewScore, reviewOpinion }] */
export function submitPracticeReview(recordId, items) {
  return request.post(`/api/practice/review/${recordId}`, items)
}

/**
 * 文件型作业批改：学生上传的作业文件没有逐题明细，直接给总分 + 评语
 * @param {number} recordId 练习记录ID
 * @param {{score:number, opinion:string}} data
 */
export function submitAttachmentReview(recordId, data) {
  return request.post(`/api/practice/review/attachment/${recordId}`, data)
}

/**
 * 下载学生提交的作业文件（教师端批改用）
 * 后端以学生上传时的原始文件名返回（Content-Disposition: attachment），
 * responseType: 'blob' → 调用方拿到 Blob 后触发浏览器下载
 */
export function downloadRecordAttachment(recordId) {
  return request.get(`/api/practice/review/attachment/${recordId}/download`, {
    responseType: 'blob'
  })
}
