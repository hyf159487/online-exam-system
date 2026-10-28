import request from '@/utils/request'

/** 学生端考试相关接口 */

// 我的考试列表（全部已发布考试）
export function getMyExams() {
  return request.get('/api/student/exams')
}

// 考试详情 + 题目（不含答案）
export function getExamQuestions(examId) {
  return request.get(`/api/student/exams/${examId}/questions`)
}

// 保存答题进度（刷新/断线后恢复，不判分）+ 心跳同步（剩余时间/切屏次数）
// extra 可选：{ remainingTime: 秒数, screenSwitchCount: 切屏次数 }
export function saveExamProgress(examId, answers, extra) {
  const payload = { answers }
  if (extra) {
    if (extra.remainingTime != null) payload.remainingTime = extra.remainingTime
    if (extra.screenSwitchCount != null) payload.screenSwitchCount = extra.screenSwitchCount
  }
  return request.post(`/api/student/exams/${examId}/progress`, payload)
}

// 心跳：只同步剩余时间/切屏次数，不传答案（answers 数组空）
export function heartbeatExamProgress(examId, remainingTime, screenSwitchCount) {
  return request.post(`/api/student/exams/${examId}/progress`, {
    answers: [],
    remainingTime,
    screenSwitchCount,
  })
}

// 交卷（客观题自动判分）
// answers: [{ questionId: 1, answer: 'A' }, { questionId: 2, answer: 'A,C' }]
export function submitExam(examId, answers) {
  return request.post(`/api/student/exams/${examId}/submit`, { answers })
}

// 我的成绩
export function getMyRecords() {
  return request.get('/api/student/records')
}

// 我的某次试卷详情（逐题作答 / 得分 / 教师评语 + 批阅进度）
// 参考答案与解析在考试结束后才下发，未批阅的主观题 getScore 为 null（前端显示「待批阅」）
export function getMyRecordDetail(recordId) {
  return request.get(`/api/student/records/${recordId}`)
}

// 试卷回看：AI 讲解某道题（考试结束后才放行，与「查看试卷」同一门槛）
// 未配置大模型 api-key 时后端自动降级为本地规则解析引擎
export function explainRecordQuestion(recordId, questionId) {
  return request.post(`/api/student/records/${recordId}/questions/${questionId}/explain`)
}

// 试卷回看：AI 追问（携带历史对话；本地规则引擎仅支持首轮）
export function chatRecordQuestion(recordId, questionId, data) {
  return request.post(`/api/student/records/${recordId}/questions/${questionId}/chat`, data)
}
