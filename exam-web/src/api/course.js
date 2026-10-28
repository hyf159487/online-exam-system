import request from '@/utils/request'

// 查全部学科（题库筛选、新增题目/试卷下拉框用）
export function getCourseList() {
  return request.get('/api/course/list')
}

// 关键字模糊查询学科（下拉远端搜索用）
// keyword 为空时返回前 limit 条（按 id 排序），非空按名称/编码 LIKE 匹配
export function searchCourses(keyword, limit = 50) {
  return request.get('/api/course/search', { params: { keyword: keyword || '', limit } })
}

// 我已加入的课程（学生端自主练习：练习卷/题库/资料只显示加入课程范围内的学科）
// keyword 为空返回全部已加入课程，非空按名称/编码模糊匹配
export function getJoinedCourses(keyword = '') {
  return request.get('/api/student/course/joined', { params: { keyword } })
}

// 新增课程（仅管理员）
export function createCourse(data) {
  return request.post('/api/course', data)
}

// 更新课程（仅管理员）
export function updateCourse(id, data) {
  return request.put(`/api/course/${id}`, data)
}

// 删除课程（仅管理员，有引用时后端拒绝）
// 传 config.params.force=true 时：仅剩知识点引用会自动连带清理知识点后删除（页面"一键清理并删除"用）
export function deleteCourse(id, config = {}) {
  return request.delete(`/api/course/${id}`, config)
}

// 课程详情：基础信息 + 任课教师列表 + 试题/试卷/考试/知识点数量统计
export function getCourseDetail(id) {
  return request.get(`/api/course/${id}`)
}

// 上传课程封面图片（教师 / 管理员）：form-data: file=xxx.png
// 返回可访问路径（形如 /uploads/course-cover/202609/xxx.png），保存课程时写入 cover 字段
export function uploadCourseCover(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/course/cover', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// Excel 批量导入课程（仅管理员）：上传 .xlsx，列：课程名称|课程编码|课程描述
export function importCoursesExcel(file) {
  return request.post('/api/course/importExcel', file, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 批量删除课程（仅管理员）：部分失败策略，返回 {success, failed, failedReasons}
export function batchDeleteCourses(ids) {
  return request.delete('/api/course/batch', { params: { ids: ids.join(',') } })
}

// 一键随机分配任课教师（仅管理员）
// data: { courseIds?: [] 为空=全部课程, minCount?: 2, maxCount?: 3, skipBound?: true }
// 从全部启用教师中随机为每门课程分配 minCount~maxCount 名教师
export function assignCourseTeachers(data) {
  return request.post('/api/course/assign-teachers', data)
}

// ==================== 课程公告（任课教师 / 管理员）====================
// 学生端「课程详情 → 课程公告」Tab 的数据来源，按课程维度组织

// 某课程的公告列表（按发布时间倒序；仅该课程任课教师或管理员可查）
export function getCourseNoticeList(courseId) {
  return request.get('/api/course-notice/list', { params: { courseId } })
}

// 发布课程公告：{ courseId, title, content }
// 发布成功后会向该课程已加入学生推送一条系统通知（学生端「通知公告」同步可见）
export function createCourseNotice(data) {
  return request.post('/api/course-notice', data)
}

// 修改课程公告：{ title, content }
export function updateCourseNotice(id, data) {
  return request.put(`/api/course-notice/${id}`, data)
}

// 删除课程公告（逻辑删除）
export function deleteCourseNotice(id) {
  return request.delete(`/api/course-notice/${id}`)
}
