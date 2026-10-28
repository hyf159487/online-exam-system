import request from '@/utils/request'

// ==================== 管理端（管理员/教师）====================

// 分页查学习资料（courseId 必选、keyword 名称搜索）
export function getStudyResourceList(params) {
  return request.get('/api/study-resource/list', { params })
}

// 资料详情
export function getStudyResourceDetail(id) {
  return request.get(`/api/study-resource/${id}`)
}

// 上传资料（FormData：courseId + file + resourceName? + description?）
// 大文件（可达 300MB）上传耗时往往超过默认 60s，这里单独拉长到 5 分钟，
// 避免传一半被前端掐断误报"网络异常，请检查后端是否启动"
export function uploadStudyResource(formData) {
  return request.post('/api/study-resource/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

// 新增外部链接资料（第三方教学视频等站外资源：{ courseId, resourceName, linkUrl, description? }）
export function addStudyResourceLink(data) {
  return request.post('/api/study-resource/link', data)
}

// 更新资料（名称/备注/所属课程；外部链接资料可同时传 linkUrl 修改链接地址）
export function updateStudyResource(id, data) {
  return request.put(`/api/study-resource/${id}`, data)
}

// 删除单个资料
export function deleteStudyResource(id) {
  return request.delete(`/api/study-resource/${id}`)
}

// 批量删除资料（ids: number[]）
export function batchDeleteStudyResources(ids) {
  return request.delete('/api/study-resource/batch', { params: { ids: ids.join(',') } })
}

// 下载资料（返回 blob）
export function downloadStudyResource(id) {
  return request.get(`/api/study-resource/download/${id}`, { responseType: 'blob' })
}

// 在线预览资料（PDF，inline，返回 blob）
export function previewStudyResource(id) {
  return request.get(`/api/study-resource/preview/${id}`, { responseType: 'blob' })
}

// 记录一次浏览（在线预览前调用，累计浏览次数）
export function viewStudyResource(id) {
  return request.post(`/api/study-resource/view/${id}`)
}

// 导出资料清单（Excel，返回 blob）
export function exportStudyResources(query) {
  return request.get('/api/study-resource/export', { params: query, responseType: 'blob' })
}

// ==================== 学生端（自主练习）====================

// 按课程拉取该课程全部学习资料
export function getStudentStudyResources(courseId) {
  // courseId 为空表示查询当前学生已加入课程的全部资料（后端 courseId 可选）
  return request.get('/api/student/study-resource/list', { params: { courseId: courseId || undefined } })
}

// 学生下载资料（返回 blob）
export function downloadStudentStudyResource(id) {
  return request.get(`/api/student/study-resource/download/${id}`, { responseType: 'blob' })
}

// 学生在线预览资料（PDF，inline，返回 blob）
export function previewStudentStudyResource(id) {
  return request.get(`/api/student/study-resource/preview/${id}`, { responseType: 'blob' })
}

// 学生记录一次浏览（外部链接资料点击「去学习」时调用，累计浏览次数）
export function viewStudentStudyResource(id) {
  return request.post(`/api/student/study-resource/view/${id}`)
}
