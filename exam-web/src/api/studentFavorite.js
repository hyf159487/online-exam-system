import request from '@/utils/request'

/** 学生端「我的收藏」相关接口 */

// 我的收藏：分页列表（可按学科 / 题型 / 题干关键词筛选）
export function getMyFavorites(params) {
  return request.get('/api/student/favorites', { params })
}

// 我的收藏：总览统计（收藏总题数 + 各学科分布）
export function getFavoriteOverview() {
  return request.get('/api/student/favorites/overview')
}

// 我的收藏：当前已收藏的试题 ID 列表（练习页标记星标用）
export function getFavoriteQuestionIds() {
  return request.get('/api/student/favorites/ids')
}

// 我的收藏：收藏一道题（幂等，重复收藏不会报错）
export function addFavorite(questionId) {
  return request.post('/api/student/favorites', { questionId })
}

// 我的收藏：取消收藏（按收藏记录 ID，列表页用）
export function removeFavorite(id) {
  return request.delete(`/api/student/favorites/${id}`)
}

// 我的收藏：取消收藏（按试题 ID，练习页用）
export function removeFavoriteByQuestion(questionId) {
  return request.delete(`/api/student/favorites/question/${questionId}`)
}

// 我的收藏：批量取消收藏（body 为收藏记录 id 数组）
export function batchRemoveFavorites(ids) {
  return request.post('/api/student/favorites/batch-remove', ids)
}

// 我的收藏：导出当前筛选条件下的收藏 Excel（返回 blob）
export function exportFavorites(params) {
  return request.get('/api/student/favorites/export', { params, responseType: 'blob' })
}
