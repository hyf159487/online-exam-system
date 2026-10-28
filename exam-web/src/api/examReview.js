import request from '@/utils/request'

export function getReviewList(params) {
  return request.get('/api/review/wait-list', { params })
}

export function getAnswerDetail(answerId) {
  return request.get(`/api/review/answer/${answerId}`)
}

export function saveScore(data) {
  return request.post('/api/review/save-score', data)
}

/**
 * AI 预评分（单题）
 * 返回 { answerId, score, maxScore, comment, reason, hits, missing, confidence, source, model, warning }
 * 注意：仅返回评分建议，不落库，教师确认后再调 saveScore
 */
export function aiScoreAnswer(answerId) {
  return request.post('/api/review/ai-score', { answerId }, { timeout: 90000 })
}

/**
 * AI 预评分（整卷多题一键预评，单次最多 10 题）
 * @param {number[]} answerIds 待预评的答题明细ID
 */
export function aiScoreBatch(answerIds) {
  return request.post('/api/review/ai-score-batch', { answerIds }, { timeout: 180000 })
}
