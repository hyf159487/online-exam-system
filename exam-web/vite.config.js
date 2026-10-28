import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
// Vite 配置
// 代理：前端请求 /api/xxx 时自动转发到后端 cpolar地址/api/xxx
// 本地开发走代理，自动解决跨域
export default defineConfig(({ mode }) => {
  // 读取环境变量（.env / .env.production）
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    // base：资源引用的根路径
    //  - Vercel / 本地 dev：用默认 '/'（绝对路径，SPA 路由刷新不 404）
    //  - Android WebView 打包：必须 ./（相对路径，file:// 协议才能加载）
    //  Android 重新打包时：在 .env 里写 VITE_BASE=./ 再执行 npm run build
    base: env.VITE_BASE || '/',
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: true, // 监听所有网卡，允许局域网内其他设备访问（别人用 http://你的IP:5173 打开）
      port: 5173,
      allowedHosts: true, // 放行cpolar域名，解决 Blocked request 报错
      proxy: {
        '/api': {
          target: 'https://334a6d19.r18.vip.cpolar.cn',
          changeOrigin: true
        },
        // 学习资料 PDF 直接预览：后端静态资源 /uploads/** 也代理到后端，
        // 避免在新窗口打开 localhost:5173/uploads/... 时被 Vite 当作 SPA 路由返回 index.html
        '/uploads': {
          target: 'https://334a6d19.r18.vip.cpolar.cn',
          changeOrigin: true
        }
      }
    }
  }
})
