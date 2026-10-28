import request from '@/utils/request'

/**
 * 首页「快捷入口」自定义配置接口
 *
 * 配置存在后端 user_quick_link 表、跟账号走：换浏览器 / 换电脑登录后依然保留。
 * 未自定义过时 list 返回空数组，前端回落到系统默认入口。
 */

/** 我的快捷入口列表（返回 [{ id, label, routePath, icon, sortNo }]） */
export function getMyQuickLinks() {
  return request.get('/api/user/quick-links')
}

/** 保存我的快捷入口（全量覆盖，links: [{ label, path, icon }]） */
export function saveMyQuickLinks(links) {
  return request.post('/api/user/quick-links', { links })
}

/** 恢复默认：清空我的自定义配置 */
export function resetMyQuickLinks() {
  return request.delete('/api/user/quick-links')
}
