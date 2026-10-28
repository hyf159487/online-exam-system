import request from '@/utils/request'

/**
 * 菜单 API
 * 后端对应 SysMenuController：
 *   GET  /api/menu/tree             全部菜单树 / 按 layout 区分
 *   GET  /api/menu/my               当前登录用户的菜单树
 *   POST /api/menu                  新增菜单（仅管理员）
 *   PUT  /api/menu                  编辑菜单（仅管理员）
 *   DELETE /api/menu/{id}           删除菜单（仅管理员）
 */

/**
 * 全部菜单树（分配权限弹窗用）
 * @param layout 可选：传 'admin' 只取教师端菜单树，'student' 只取学生端，不传取全部
 */
export function getMenuTree(layout) {
  return request.get('/api/menu/tree', {
    params: layout ? { layout } : {}
  })
}

/**
 * 当前登录用户的菜单树
 * 用于刷新页面后重新拉自己的菜单（authStore.menus 为空时调用）
 */
export function getMyMenus() {
  return request.get('/api/menu/my')
}

/** 新增菜单 */
export function createMenu(data) {
  return request.post('/api/menu', data)
}

/** 编辑菜单 */
export function updateMenu(data) {
  return request.put('/api/menu', data)
}

/** 删除菜单 */
export function deleteMenu(id) {
  return request.delete(`/api/menu/${id}`)
}
