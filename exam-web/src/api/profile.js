import request from '@/utils/request'

/** 通用「个人中心」接口（管理员 / 教师 / 学生三种角色通用，基于 token 获取本人信息） */

/** 获取当前登录用户的个人信息（username/realName/role/roleName/className/grade/department/phone/email/status） */
export function getProfile() {
  return request.get('/api/profile/get')
}

/** 更新当前账号联系方式（仅手机号、邮箱，身份字段后端会丢弃） */
export function updateProfile(data) {
  return request.put('/api/profile/update', data)
}

/**
 * 上传/更换当前账号头像（任意角色通用）：form-data: file=xxx.png
 * 返回可访问路径（形如 /uploads/avatar/202609/xxx.png），前端直接当头像 src 使用
 */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/profile/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 修改当前账号登录密码（{ oldPassword, newPassword }），成功后建议重新登录 */
export function updatePassword(data) {
  return request.put('/api/profile/updatePwd', data)
}
