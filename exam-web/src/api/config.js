import request from '@/utils/request'

/**
 * 系统参数配置 API
 * 后端对应 SysConfigController
 */

/** 获取全部参数列表（管理员） */
export function getConfigList() {
  return request.get('/api/config/list')
}

/** 批量更新参数值（管理员） */
export function batchUpdateConfig(list) {
  return request.post('/api/config/batch-update', list)
}

/** 按 key 获取单个参数值（所有登录用户） */
export function getConfigValue(key) {
  return request.get(`/api/config/value/${key}`)
}
