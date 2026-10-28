import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMyMenus } from '@/api/menu'

/**
 * 认证状态管理
 *
 * 存储策略：仅使用 sessionStorage（关闭浏览器窗口 / 桌面客户端即清空）。
 *   - 每次打开系统都必须重新登录（考试系统的安全要求）
 *   - 同一浏览器开多个 Tab 各自独立登录，互不覆盖
 * 注意：不再使用 localStorage 持久化登录态；
 * 页面加载时会清掉历史上遗留在 localStorage 里的旧登录态（兼容老版本升级）。
 */

const AUTH_KEYS = ['token', 'userInfo', 'menus']

// 升级兼容：清掉旧版本写在 localStorage 里的登录态，避免"打开不用登录"的问题
AUTH_KEYS.forEach(k => localStorage.removeItem(k))

/**
 * 从 sessionStorage 读取登录态
 * @param {'token'|'userInfo'|'menus'} key
 * @param {string|null} fallback
 * @returns {string|null}
 */
function readSessionWithFallback(key, fallback) {
  const val = sessionStorage.getItem(key)
  return val === null || val === undefined || val === '' ? fallback : val
}

/**
 * 写入身份信息（仅本 Tab / 本次窗口有效）
 * @param {'token'|'userInfo'|'menus'} key
 * @param {string} value
 */
function writeAuth(key, value) {
  sessionStorage.setItem(key, value)
}

/**
 * 清除身份信息（连同历史遗留的 localStorage 一起清）
 * @param {'token'|'userInfo'|'menus'} key
 */
function removeAuth(key) {
  sessionStorage.removeItem(key)
  localStorage.removeItem(key)
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(String(readSessionWithFallback('token', '') || ''))

  let userInfoInit = null
  try {
    const raw = readSessionWithFallback('userInfo', 'null')
    userInfoInit = raw ? JSON.parse(raw) : null
  } catch (e) {
    userInfoInit = null
  }
  const userInfo = ref(userInfoInit)

  let menusInit = []
  try {
    const raw = readSessionWithFallback('menus', '[]')
    menusInit = raw ? JSON.parse(raw) : []
    if (!Array.isArray(menusInit)) menusInit = []
  } catch (e) {
    menusInit = []
  }
  // RBAC：当前用户拥有的菜单权限树，登录时返回或刷新页面时拉取
  const menus = ref(menusInit)

  // 登录成功后调用（data 是登录接口返回的 data 对象）
  function setAuth(tokenVal, user, userMenus) {
    token.value = tokenVal || ''
    userInfo.value = user || null
    menus.value = Array.isArray(userMenus) ? userMenus : []
    writeAuth('token', String(tokenVal || ''))
    writeAuth('userInfo', JSON.stringify(userInfo.value))
    writeAuth('menus', JSON.stringify(menus.value))
  }

  // 仅更新菜单（不重置 token/userInfo），用于菜单变更后重新拉取
  function setMenus(userMenus) {
    menus.value = Array.isArray(userMenus) ? userMenus : []
    writeAuth('menus', JSON.stringify(menus.value))
  }

  // 刷新页面或菜单变更后调用：从后端重新拉当前用户菜单
  async function loadMenus() {
    if (!token.value) {
      menus.value = []
      removeAuth('menus')
      return []
    }
    try {
      const res = await getMyMenus()
      const list = Array.isArray(res?.data) ? res.data : []
      setMenus(list)
      return list
    } catch (e) {
      // 拉 menu 失败不阻塞：菜单为空时 Layout 会兜底显示首页
      return menus.value
    }
  }

  // 退出登录：清当前 Tab 的 sessionStorage，同时清 localStorage（因为当前账号已经退出了）
  // 注意：其他 Tab 如果还登录着旧账号，它们的 sessionStorage 有自己的副本，不受本次 clearAuth 影响。
  // 但其他 Tab 下次向后端发请求时若 token 相同会被后端判定为同一账号；若用户在另一个 Tab 登录的是不同账号，则那里的 sessionStorage 保留的是另一套 token，不受影响。
  function clearAuth() {
    token.value = ''
    userInfo.value = null
    menus.value = []
    AUTH_KEYS.forEach(removeAuth)
  }

  return { token, userInfo, menus, setAuth, setMenus, loadMenus, clearAuth }
})
