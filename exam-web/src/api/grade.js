import request from '@/utils/request'

// 分页查询成绩（可选：试卷筛选、学号/姓名模糊）
export function getGradePage(params) {
  return request.get('/api/grade/page', { params })
}

// 成绩统计（人数/平均/最高/最低/及格数/分数段分布）
export function getGradeStats(params) {
  return request.get('/api/grade/stats', { params })
}

// 待阅卷列表（status=1 已交卷、简答题待教师判分）
export function getPendingGrades(params) {
  return request.get('/api/grade/pending', { params })
}

// 阅卷详情（记录信息 + 逐题题干/标准答案/学生作答）
export function getReviewDetail(recordId) {
  return request.get(`/api/grade/${recordId}/review`)
}

// 教师提交判分（items: [{ questionId, score, comment }]）
export function submitReview(recordId, data) {
  return request.post(`/api/grade/${recordId}/review`, data)
}
