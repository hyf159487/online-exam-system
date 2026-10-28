import request from '@/utils/request'

// 登录
export function login(data) {
  return request.post('/api/auth/login', data)
}

// 获取当前登录用户信息
export function getMe() {
  return request.get('/api/auth/me')
}

// 退出登录（通知后端将 token 加入黑名单，立即失效）
export function logout() {
  return request.post('/api/auth/logout')
}
