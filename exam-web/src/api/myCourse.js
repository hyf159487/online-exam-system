import request from '@/utils/request'

// ==================== 教师端：我的课程 ====================

// 我任教的课程列表（含学生人数）
// params: { keyword 课程名称/编码模糊筛选, courseType 课程类型模糊筛选 }，均可不传
export function getMyCourses({ keyword, courseType } = {}) {
  return request.get('/api/teacher/my-course/list', {
    params: {
      keyword: keyword || undefined,
      courseType: courseType || undefined
    }
  })
}

// 课程信息（教师端「我的课程」点击课程卡片进入；仅任教教师可查看）
// 返回结构与管理员端课程详情一致：{ course（含介绍字段 + 任课教师）, teachers, questionCount, paperCount, examCount, knowledgeCount }
export function getMyCourseDetail(courseId) {
  return request.get(`/api/teacher/my-course/${courseId}`)
}

// 修改课程信息（仅任教教师）
// 可改：课程名称/课程类型/简介/详细介绍/学习目标/适用人群/课程大纲/封面/开课时间/报名截止
// 课程编码与任课教师绑定由管理员维护，后端会忽略教师端提交的这两个字段
export function updateMyCourse(courseId, data) {
  return request.put(`/api/teacher/my-course/${courseId}`, data)
}

// 某课程已加入学生名单（仅任教教师可查看），keyword 按学生姓名/学号模糊筛选
export function getMyCourseStudents(courseId, keyword) {
  return request.get(`/api/teacher/my-course/${courseId}/students`, {
    params: { keyword: keyword || undefined }
  })
}

// 将学生移出课程（仅任教教师可操作）
export function removeMyCourseStudent(courseId, studentId) {
  return request.delete(`/api/teacher/my-course/${courseId}/students/${studentId}`)
}
