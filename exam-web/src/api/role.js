import request from '@/utils/request'

/**
 * 角色 API（管理员 RBAC 配置页面）
 * 后端对应 SysRoleController，所有接口仅管理员可访问
 */

/** 角色列表（支持名称模糊查询） */
export function getRoleList(roleName) {
  return request.get('/api/role/list', {
    params: roleName ? { roleName } : {}
  })
}

/** 查单条角色 */
export function getRoleById(id) {
  return request.get(`/api/role/${id}`)
}

/** 新增角色 */
export function createRole(data) {
  return request.post('/api/role', data)
}

/** 编辑角色（仅允许改 roleName/remark） */
export function updateRole(data) {
  return request.put('/api/role', data)
}

/** 删除角色（admin 角色受保护禁删） */
export function deleteRole(id) {
  return request.delete(`/api/role/${id}`)
}

/** 取角色已勾选的菜单 ID 列表（分配弹窗回显） */
export function getRoleMenus(id) {
  return request.get(`/api/role/${id}/menus`)
}

/** 给角色分配菜单权限（覆盖式 + 事务） */
export function assignRoleMenus(id, menuIds) {
  return request.post(`/api/role/${id}/menus`, menuIds)
}
