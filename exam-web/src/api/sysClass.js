import request from '@/utils/request'

// 班级列表（含学生数）
export function getClassList() {
  return request.get('/api/class')
}

// 新增班级（仅管理员）
export function createClass(data) {
  return request.post('/api/class', data)
}

// 更新班级（仅管理员）
export function updateClass(id, data) {
  return request.put(`/api/class/${id}`, data)
}

// 删除班级（仅管理员，班级下仍有学生时后端拒绝）
export function deleteClass(id) {
  return request.delete(`/api/class/${id}`)
}

// 指定班级下的学生列表（班级管理 - 点击【查看】跳转学生详情页调用）
export function getClassStudentList(classId) {
  return request.get('/api/class/studentList', { params: { classId } })
}

// 批量新增班级（仅管理员）：部分失败策略，返回 {success, failed, failedReasons}
export function batchCreateClasses(classes) {
  return request.post('/api/class/batch', classes)
}

// Excel 批量导入班级（仅管理员）：上传 .xlsx，列：班级名称|年级|院系|专业
export function importClassesExcel(file) {
  return request.post('/api/class/importExcel', file, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
