import request from '@/utils/request'

/**
 * 字典管理 API
 * 后端对应 DictController：
 *   GET/POST/PUT/DELETE /api/dict/types     字典类型 CRUD
 *   GET/POST/PUT/DELETE /api/dict/items     字典子项 CRUD
 *   GET /api/dict/items/code/{typeCode}      按编码取启用子项（业务下拉框用）
 */

// ===== 字典类型 =====

export function getDictTypeList(typeName) {
  return request.get('/api/dict/types', {
    params: typeName ? { typeName } : {}
  })
}

export function getDictTypeById(id) {
  return request.get(`/api/dict/types/${id}`)
}

export function createDictType(data) {
  return request.post('/api/dict/types', data)
}

export function updateDictType(data) {
  return request.put('/api/dict/types', data)
}

export function deleteDictType(id) {
  return request.delete(`/api/dict/types/${id}`)
}

// ===== 字典子项 =====

export function getDictItemList(typeId) {
  return request.get('/api/dict/items', { params: { typeId } })
}

export function getDictItemById(id) {
  return request.get(`/api/dict/items/${id}`)
}

export function createDictItem(data) {
  return request.post('/api/dict/items', data)
}

export function updateDictItem(data) {
  return request.put('/api/dict/items', data)
}

export function deleteDictItem(id) {
  return request.delete(`/api/dict/items/${id}`)
}

/**
 * 按字典编码取启用的子项列表（业务下拉框最常用接口）
 * @param {string} typeCode 字典编码，如 'question_type' / 'difficulty'
 * @returns {Promise} 子项数组 [{ id, typeId, itemLabel, itemValue, sortOrder, enabled, ... }]
 */
export function getDictItemsByCode(typeCode) {
  return request.get(`/api/dict/items/code/${typeCode}`)
}

/**
 * 辅助函数：把字典子项数组转为 el-select 需要的 options 格式
 * @param {Array} items getDictItemsByCode 返回的 data 数组
 * @returns [{ value: '1', label: '单选题' }, ...]
 */
export function toSelectOptions(items) {
  if (!items || !Array.isArray(items)) return []
  return items.map(item => ({ value: item.itemValue, label: item.itemLabel }))
}

/**
 * 辅助函数：按 value 找 label（用于表格列显示文本）
 * @param {Array} items 字典子项数组
 * @param {string|number} value 存储值
 * @returns {string} 显示标签
 */
export function findLabel(items, value) {
  if (!items || !Array.isArray(items)) return String(value)
  const found = items.find(item => String(item.itemValue) === String(value))
  return found ? found.itemLabel : String(value)
}
