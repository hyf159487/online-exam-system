import request from '@/utils/request'

// 获取当前登录学生的个人信息（学号/姓名/班级/年级/院系只读，手机号/邮箱可改）
export function getStudentProfile() {
  return request.get('/api/student/profile/get')
}

// 更新联系方式（仅手机号、邮箱，其余字段后端直接丢弃）
export function updateStudentProfile(data) {
  return request.put('/api/student/profile/update', data)
}

// 上传/更换当前学生头像：与通用版共用同一个接口（返回 /uploads/avatar/... 可访问路径）
export function uploadStudentAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/profile/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 修改当前学生登录密码（原密码 + 新密码，后端 BCrypt 加密存储）
export function updateStudentPassword(data) {
  return request.put('/api/student/profile/updatePwd', data)
}
