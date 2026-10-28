import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

// 创建 axios 实例
// baseURL 优先级：
//  1. Android WebView 注入的 window.__API_BASE__（打包时 MainActivity 自动注入）
//  2. Vercel 环境变量 VITE_API_BASE（在 Vercel 项目设置里配置，指向线上后端地址）
//  3. 空字符串 → 本地开发走 vite.config.js 的 /api 代理
const apiBase =
  (typeof window !== 'undefined' && window.__API_BASE__) ||
  import.meta.env.VITE_API_BASE ||
  ''

const request = axios.create({
  baseURL: apiBase,
  // 默认 60 秒：AI 讲解/难度评估等大模型请求较慢（后端配置 ai.timeout-seconds=60），
  // 10 秒太短会导致生成到一半被前端掐断，报 timeout of 10000ms exceeded
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：自动带上 JWT token
request.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理后端返回的 { code, msg, data }
request.interceptors.response.use(
  (response) => {
    // 下载文件（blob 响应）不做业务 code 判定，直接放行原始 response
    if (response.config?.responseType === 'blob') {
      // 但是还需要判断：如果 Content-Type 是 application/json，说明后端返回了错误信息的 JSON
      // （例如 401/403 的业务错误被后端写成 JSON），这种要把 blob 解析成 JSON 再处理
      const contentType = response.headers['content-type'] || response.headers['Content-Type'] || ''
      if (contentType.includes('application/json') || contentType.includes('text/plain')) {
        // 用 FileReader 读取 JSON 错误信息，兼容 blob+json 的情况
        return new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => {
            try {
              const res = JSON.parse(reader.result)
              if (res.code === 200) {
                resolve(res)
              } else {
                ElMessage.error(res.msg || '请求失败')
                const error = new Error(res.msg || '请求失败')
                error.res = res
                reject(error)
              }
            } catch {
              resolve(response)
            }
          }
          reader.onerror = () => resolve(response)
          reader.readAsText(response.data)
        })
      }
      return response
    }

    const res = response.data
    // code = 200 业务成功，直接返回 data
    if (res.code === 200) {
      return res
    }
    // 带结构化数据的业务错误（如"题库不足"）或调用方显式 errorTip:false 时，这里不重复弹提示
    const skipTip = res.data?.insufficient || res.data?.duplicate === true || response.config?.errorTip === false
    if (!skipTip) {
      ElMessage.error(res.msg || '请求失败')
    }
    // 挂上完整响应，方便调用方读取 res.data 做后续操作（如自动调整题数）
    const error = new Error(res.msg || '请求失败')
    error.res = res
    return Promise.reject(error)
  },
  (error) => {
    // HTTP 状态码错误（401/403/500 等）
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        const authStore = useAuthStore()
        authStore.clearAuth()
        router.push('/login')
      } else if (status === 403) {
        // 已登录但权限不足：展示后端返回的具体提示，并回到对应角色首页
        const msg = error.response.data?.msg || '没有权限访问该功能'
        ElMessage.error(msg)
        const authStore = useAuthStore()
        const role = authStore.userInfo?.role
        router.push(role === 0 ? '/student/dashboard' : '/dashboard')
      } else {
        const msg = error.response.data?.msg
        ElMessage.error(msg || `服务器错误 (${status})`)
      }
    } else {
      ElMessage.error('网络异常，请检查后端是否启动')
    }
    return Promise.reject(error)
  }
)

export default request
