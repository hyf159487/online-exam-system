import request from '@/utils/request'

// ==================== 学生端：加入课程 ====================

// 开放课程列表（分页，含当前学生对每门课的状态：null未申请 0待审 1已加入 2驳回；quitPending 退出待审）
// params: { statusFilter, courseName, teacherName, courseType, pageNum, pageSize } 返回 PageResult { records, total, pageNum, pageSize }
// statusFilter 可选：joined=已加入，notJoined=未加入，pending=审核中；不传=全部
// courseType 可选：课程类型精确筛选（取 getStudentCourseTypes 的下拉值）；不传=全部
export function getStudentOpenCourses(params = {}) {
  return request.get('/api/student/course/list', {
    params: {
      statusFilter: params.statusFilter || undefined,
      courseName: params.courseName || undefined,
      teacherName: params.teacherName || undefined,
      courseType: params.courseType || undefined,
      pageNum: params.pageNum || 1,
      pageSize: params.pageSize || 10
    }
  })
}

// 课程类型下拉选项（课程表中已有类型去重，用于「课程类型」筛选）
export function getStudentCourseTypes() {
  return request.get('/api/student/course/types')
}

// 课程名称 / 授课教师筛选下拉候选（去重列表，供筛选框下拉选择 + 输入关键字模糊查询）
// 返回 { courseNames: [...], teacherNames: [...] }
export function getStudentCourseFilterOptions() {
  return request.get('/api/student/course/filter-options')
}

// 申请加入课程
export function applyCourse(courseId) {
  return request.post(`/api/student/course/apply/${courseId}`)
}

// 申请退出课程
export function quitCourse(courseId) {
  return request.post(`/api/student/course/quit/${courseId}`)
}

// 我的申请记录
export function getMyCourseApplies() {
  return request.get('/api/student/course/my-applies')
}

// 个人中心「我的课程」：我已加入的课程信息
// 返回字段：id / courseName / courseCode / courseType / description / cover / startTime /
//          enrollDeadline / joinTime（本人加入时间）/ studentCount（参加人数）/ resourceCount（资料数）/
//          quitPending（1=退出申请审核中）/ teacherNames（任课教师，多名用「、」拼接）
// keyword 非空时按 课程名称 / 课程编码 / 任课教师姓名 模糊过滤
export function getMyJoinedCourses(keyword = '') {
  return request.get('/api/student/course/my-courses', {
    params: { keyword: keyword || undefined }
  })
}

// ==================== 学生端：课程详情页（从「加入课程」点击课程卡片进入）====================

// 课程详情：课程完整信息（详细介绍 intro / 学习目标 / 适用人群 / 课程大纲 / 任课教师 / 已加入人数）
// 返回 { course: {...}, teachers: [{ id, realName, phone, email }], state: {...} }
// state = { applyStatus, canApply, inPending, joined, quitPending, canQuit, applyClosed }
//   前端据此锁定「资料 / 公告 / 作业 / 考试」Tab，并切换底部按钮文案与可点状态
export function getStudentCourseDetail(courseId) {
  return request.get(`/api/student/course/detail/${courseId}`)
}

// 课程公告列表（按发布时间倒序；仅已加入该课程的学生可读，未加入返回 403）
export function getStudentCourseNotices(courseId) {
  return request.get('/api/student/course/notices', { params: { courseId } })
}
