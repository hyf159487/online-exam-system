import request from '@/utils/request'

// 教师端错题管理：全班错题列表（可按 学生/班级/学科/题型 筛选）
export function getTeacherWrongQuestions(params) {
  return request.get('/api/teacher/wrong-questions', { params })
}

// 教师端错题统计：按学科分布 + 高频错题 Top10
export function getTeacherWrongStats(params) {
  return request.get('/api/teacher/wrong-questions/stats', { params })
}

// 班级列表（筛选下拉用）
export function getClassList() {
  return request.get('/api/teacher/classes')
}
