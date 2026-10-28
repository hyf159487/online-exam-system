import request from '@/utils/request'

// 分页查询考试列表（可选：名称模糊、运行状态筛选）
// runtimeStatus：0未开始 1进行中 2已结束 3已撤回
export function getExamPage(params) {
  return request.get('/api/exam/page', { params })
}

// 考试详情
export function getExamById(id) {
  return request.get(`/api/exam/${id}`)
}

// 发布考试
export function createExam(data) {
  return request.post('/api/exam', data)
}

// 更新考试
export function updateExam(data) {
  return request.put('/api/exam', data)
}

// 发布 / 撤回（status: 1=发布 0=撤回）
export function updateExamStatus(id, status) {
  return request.put('/api/exam/status', { id, status })
}

// 提前结束进行中的考试（结束时间改为当前时刻，考试立即进入已结束）
export function finishExam(id) {
  return request.put(`/api/exam/${id}/finish`)
}

// 重新发布已结束的考试，两种模式二选一：
// republishExam(id, { days: 3 })                        → 结束时间 = 当前时间 + 3 天
// republishExam(id, { startTime, endTime })             → 自定义时间窗口（yyyy-MM-dd HH:mm:ss）
export function republishExam(id, payload) {
  return request.put(`/api/exam/${id}/republish`, payload)
}

// 删除考试
export function deleteExam(id) {
  return request.delete(`/api/exam/${id}`)
}

// 考试总数（仪表盘）
export function countExam() {
  return request.get('/api/exam/count')
}
