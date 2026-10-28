import request from '@/utils/request'

export function getRunningExams() {
  return request.get('/api/monitor/running-exams')
}

export function getStudentList(examId, params) {
  return request.get(`/api/monitor/students/${examId}`, { params })
}

export function getAnswerDetail(examId, studentId) {
  return request.get('/api/monitor/answer-detail', { params: { examId, studentId } })
}

export function forceSubmit(examId, studentId) {
  return request.post('/api/monitor/force-submit', { examId, studentId })
}
