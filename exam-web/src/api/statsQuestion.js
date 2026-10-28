import request from '@/utils/request'

export function getExamList() {
  return request.get('/api/stats/question/exam-list')
}

export function getQuestionData(examId) {
  return request.get('/api/stats/question/data', { params: { examId } })
}

export function getAnswerDetail(examId, questionId) {
  return request.get('/api/stats/question/answer-detail', { params: { examId, questionId } })
}
