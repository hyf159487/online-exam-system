import request from '@/utils/request'

// ==================== 知识点管理 ====================
// 说明：管理操作（增删改/导入导出）仅管理员，读接口教师/管理员可用。
// 后端 /api/knowledge-point 权限由 AuthInterceptor 按路径 + 方法控制。

// 分页查询知识点（管理页：courseId 建议必选 + keyword 名称/编码搜索）
export function getKnowledgePointPage(params) {
  return request.get('/api/knowledge-point/page', { params })
}

// 按课程查知识点（无分页，题目录入下拉 / 只读查看用）
export function getKnowledgePointList(courseId, keyword) {
  return request.get('/api/knowledge-point/list', {
    params: { courseId, keyword }
  })
}

// 新增知识点
export function addKnowledgePoint(data) {
  return request.post('/api/knowledge-point', data)
}

// 更新知识点
export function updateKnowledgePoint(data) {
  return request.put('/api/knowledge-point', data)
}

// 删除单个知识点（被试题引用时后端拒绝）
export function deleteKnowledgePoint(id) {
  return request.delete(`/api/knowledge-point/${id}`)
}

// 批量删除知识点
export function batchDeleteKnowledgePoints(ids) {
  return request.post('/api/knowledge-point/batchDelete', ids)
}

// Excel 批量导入知识点（按课程）
export function importKnowledgeExcel(file, courseId) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('courseId', courseId)
  return request.post('/api/knowledge-point/importExcel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 下载导入模板（blob）
export function downloadKnowledgeTemplate() {
  return request.get('/api/knowledge-point/template/excel', { responseType: 'blob' })
}

// 导出当前课程全部知识点（blob）
export function exportKnowledgePoints(params) {
  return request.get('/api/knowledge-point/export', { params, responseType: 'blob' })
}
