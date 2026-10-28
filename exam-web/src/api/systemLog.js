import request from '@/utils/request'

/**
 * 操作日志 API（管理员专属审计页面）
 *
 * 接口对应后端 OperationLogController：
 *   GET /api/operation-log/list           分页查询
 *   GET /api/operation-log/{id}           查单条（详情弹窗）
 *   GET /api/operation-log/modules        模块下拉选项
 *   GET /api/operation-log/operationTypes  操作类型下拉选项
 *   GET /api/operation-log/export         导出 Excel
 *
 * ❗后端无任何写接口：审计数据只增不改
 */

/**
 * 分页查询操作日志
 * @param {Object} params - 查询参数：
 *   { pageNum, pageSize, operatorName, module, operationType, status, startTime, endTime }
 *   其中 startTime/endTime 格式为 'yyyy-MM-dd HH:mm:ss'
 */
export function getOperationLogList(params) {
  return request.get('/api/operation-log/list', { params })
}

/**
 * 按 ID 查单条操作日志（详情弹窗用）
 */
export function getOperationLogById(id) {
  return request.get(`/api/operation-log/${id}`)
}

/** 模块下拉选项 */
export function getOperationLogModules() {
  return request.get('/api/operation-log/modules')
}

/** 操作类型下拉选项 */
export function getOperationLogTypes() {
  return request.get('/api/operation-log/operationTypes')
}

/**
 * 导出当前筛选条件下的日志为 Excel
 * 后端直接返回 .xlsx 文件流，axios 拿到 blob 后由调用方触发浏览器下载
 * token 已由 request 拦截器自动加到 Authorization 头
 */
export function exportOperationLogs(params) {
  return request.get('/api/operation-log/export', {
    params,
    responseType: 'blob'
  })
}
