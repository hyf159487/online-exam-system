import request from '@/utils/request'

/**
 * 班级学情分析接口
 */

/** 获取可统计考试列表 */
export function getExamList() {
  return request.get('/api/stats/class/exam-list')
}

/** 获取某考试下的可统计班级列表 */
export function getClassList(examId) {
  return request.get('/api/stats/class/class-list', { params: { examId } })
}

/** 查询班级学情统计数据 */
export function getClassData(data) {
  return request.post('/api/stats/class/data', data)
}

/** 查看某学生逐题作答详情 */
export function getStudentAnswers(data) {
  return request.post('/api/stats/class/answers', data)
}

/** 获取作答维度统计（按题型 type / 按难度 difficulty） */
export function getDimensionStats(data, mode) {
  return request.post('/api/stats/class/dimension', data, { params: { mode } })
}

/** 导出班级学情 Excel（完整报表） */
export function exportClassStatsExcel(data) {
  return request.post('/api/stats/class/export', data, {
    responseType: 'blob',
    timeout: 60000
  })
}
