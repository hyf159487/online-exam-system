import request from '@/utils/request'

// ==================== 教师/管理员端：课程申请审核 ====================

// 申请列表（status 可空=全部，0=待审核）
export function getCourseApplyList(status) {
  return request.get('/api/teacher/course-apply/list', { params: { status: status ?? undefined } })
}

// 待审核申请数量（角标提示用）
export function getCourseApplyPendingCount() {
  return request.get('/api/teacher/course-apply/pending-count')
}

// 审核页统计卡片数据：{ pendingCount 待审核数, handledThisMonth 本月已处理数 }
export function getCourseApplyStats() {
  return request.get('/api/teacher/course-apply/stats')
}

// 申请学生基础信息 + 该生在本人任教课程下的申请记录（点击学生姓名查看）
export function getCourseApplyStudentDetail(studentId) {
  return request.get(`/api/teacher/course-apply/student/${studentId}`)
}

// 同意申请
export function approveCourseApply(id) {
  return request.post(`/api/teacher/course-apply/${id}/approve`)
}

// 批量通过（ids: number[]；批量驳回需逐条填写理由，不提供批量接口）
export function batchApproveCourseApply(ids) {
  return request.post('/api/teacher/course-apply/batch-approve', { ids })
}

// 驳回申请（填写驳回理由）
export function rejectCourseApply(id, reason) {
  return request.post(`/api/teacher/course-apply/${id}/reject`, null, { params: { reason } })
}

// 导出课程申请审核记录（Excel，返回 blob）
export function exportCourseApplies(params) {
  return request.get('/api/teacher/course-apply/export', { params, responseType: 'blob' })
}
