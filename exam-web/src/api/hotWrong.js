import request from '@/utils/request'

export function getHotWrongList(params) {
  return request.get('/api/hot-wrong/list', { params })
}

export function getHotWrongStat(questionId) {
  return request.get(`/api/hot-wrong/stat/${questionId}`)
}

export function addToPaper(paperId, questionId, score) {
  return request.post('/api/hot-wrong/add-paper', { paperId, questionId, score })
}
