<template>
  <el-container class="layout">
    <!-- 侧边栏（移动端抽屉化：.open 时滑出） -->
    <el-aside width="220px" class="sidebar" :class="{ open: sidebarOpen }">
      <div class="logo">
        <span class="logo__brand">🎓</span>
        <span class="logo__title">考试系统 · 学生端</span>
      </div>
      <el-menu
        :default-active="route.path"
        :default-openeds="defaultOpeneds"
        :router="true"
        :unique-opened="true"
        background-color="transparent"
        text-color="rgba(226, 232, 240, .85)"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <!-- 首页：固定一级菜单，介绍学生端功能、展示考试提醒与快捷入口 -->
        <el-menu-item index="/student/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>

        <!-- 通知公告：固定一级菜单，学生只读查看（无发布权限） -->
        <el-menu-item index="/student/messages">
          <el-icon><Bell /></el-icon>
          <span>通知公告</span>
        </el-menu-item>

        <!-- 动态菜单：从 authStore.menus 渲染 -->
        <template v-for="menu in menus" :key="menu.id">
          <el-sub-menu
            v-if="menu.hasChildren && menu.children && menu.children.length > 0"
            :index="String(menu.id)"
          >
            <template #title>
              <el-icon><component :is="getIcon(menu.icon)" /></el-icon>
              <span>{{ menuLabel(menu) }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.id"
              :index="child.routePath"
            >
              <el-icon><component :is="getIcon(child.icon)" /></el-icon>
              <span>{{ menuLabel(child) }}</span>
            </el-menu-item>
          </el-sub-menu>

          <el-menu-item
            v-else
            :index="menu.routePath"
          >
            <el-icon><component :is="getIcon(menu.icon)" /></el-icon>
            <span>{{ menuLabel(menu) }}</span>
          </el-menu-item>
        </template>

        <!-- 兜底：菜单为空时显示「我的考试」 -->
        <el-menu-item v-if="menus.length === 0" index="/student/exams">
          <el-icon><Notebook /></el-icon>
          <span>我的考试</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header app-layout__header">
        <div class="header-left">
          <!-- 移动端：汉堡按钮呼出抽屉侧边栏（桌面隐藏） -->
          <el-icon class="menu-toggle" @click="sidebarOpen = true"><Expand /></el-icon>
          <span>在线考试与题库管理系统 - 学生端</span>
        </div>
        <div class="header-right">
          <!-- 铃铛图标 + 未读红点 -->
          <el-popover
            placement="bottom-end"
            :width="360"
            trigger="click"
            popper-class="notice-popover"
            @show="handlePopoverShow"
          >
            <template #reference>
              <span class="bell-wrapper" title="通知公告">
                <el-icon class="bell-icon"><Bell /></el-icon>
                <span v-if="unreadCount > 0" class="bell-badge">
                  {{ unreadCount > 99 ? '99+' : unreadCount }}
                </span>
              </span>
            </template>

            <!-- 消息面板内容 -->
            <div class="notice-panel">
              <div class="panel-header">
                <span class="panel-title">通知公告</span>
                <div>
                  <span v-if="unreadCount > 0" class="unread-tip">{{ unreadCount }} 条未读</span>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :disabled="unreadCount === 0"
                    @click="handleMarkAllRead"
                  >全部已读</el-button>
                </div>
              </div>

              <div class="notice-list" v-loading="noticeLoading">
                <template v-if="noticeList.length > 0">
                  <div
                    v-for="item in noticeList"
                    :key="item.id"
                    class="notice-item"
                    :class="{ unread: item.isRead !== 1 }"
                    @click="handleNoticeClick(item)"
                  >
                    <div class="notice-item-header">
                      <span class="notice-title">
                        <span v-if="item.isRead !== 1" class="unread-dot"></span>
                        {{ item.noticeTitle }}
                      </span>
                      <span class="notice-time">{{ formatTime(item.sendTime) }}</span>
                    </div>
                    <div class="notice-sender">{{ item.sendUserName || '系统' }}</div>
                    <div class="notice-summary">{{ summary(item.noticeContent) }}</div>
                  </div>
                </template>
                <el-empty v-else description="暂无消息" :image-size="60" />
              </div>

              <div class="panel-footer">
                <span>最多显示最近 50 条消息</span>
              </div>
            </div>
          </el-popover>

          <el-dropdown @command="handleCommand">
            <span class="user-info">
              {{ authStore.userInfo?.realName || authStore.userInfo?.username || '同学' }}
              <el-tag size="small" type="success" style="margin-left: 8px">学生</el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区：:key="route.fullPath" 强制每次路由切换都重建组件（避免同组件跨路由复用导致数据不刷新） -->
      <el-main class="main">
        <router-view v-slot="{ Component, route }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 消息详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="消息详情"
      width="560px"
      destroy-on-close
    >
      <div v-if="currentNotice" class="notice-detail">
        <div class="detail-row">
          <span class="detail-label">标题：</span>
          <span class="detail-title">{{ currentNotice.noticeTitle }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">发送人：</span>
          <span>{{ currentNotice.sendUserName || '系统' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">发送时间：</span>
          <span>{{ currentNotice.sendTime }}</span>
        </div>
        <div class="detail-divider"></div>
        <div class="notice-content">{{ currentNotice.noticeContent }}</div>

        <!-- 确认收到区域 -->
        <div class="confirm-area">
          <template v-if="currentNotice.confirmStatus === 1">
            <el-tag type="success" effect="light">
              <el-icon style="vertical-align: -2px"><CircleCheck /></el-icon>
              已确认收到{{ currentNotice.confirmTime ? '（' + formatTime(currentNotice.confirmTime) + '）' : '' }}
            </el-tag>
            <el-button
              v-if="currentNotice.examId != null"
              type="primary"
              plain
              @click="goExam(currentNotice)"
            >立即前往考试</el-button>
          </template>
          <template v-else-if="currentNotice.examId != null">
            <!-- 考试通知：可选立即前往考试 / 稍后再说 -->
            <el-button type="primary" :loading="confirming" @click="handleConfirm(true)">
              确认收到并立即前往考试
            </el-button>
            <el-button :disabled="confirming" @click="handleConfirm(false)">稍后再说</el-button>
          </template>
          <template v-else>
            <!-- 普通消息：确认收到 -->
            <el-button type="primary" :loading="confirming" @click="handleConfirm(false)">
              确认收到
            </el-button>
          </template>
        </div>
      </div>
    </el-dialog>

    <!-- 移动端抽屉遮罩：点遮罩收起侧边栏 -->
    <div class="sidebar-mask" :class="{ show: sidebarOpen }" @click="sidebarOpen = false"></div>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { logout } from '@/api/auth'
import {
  Notebook, DataAnalysis, EditPen, Warning, UserFilled, ArrowDown,
  Document, Postcard, Bell, Key, InfoFilled, CircleCheck, HomeFilled,
  Expand, Files, MagicStick, Star, Plus, Reading, FolderOpened
} from '@element-plus/icons-vue'
import {
  getUnreadCount,
  getUserNoticeList,
  markNoticeRead,
  markAllRead,
  confirmNotice
} from '@/api/notice'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 学生端菜单顺序：首页(硬编码最前) → 通知公告(硬编码) → 加入课程 → 课程学习 → 我的错题 → 我的收藏 → 我的考试 → 我的成绩 → 个人中心
// 说明：
//   1) 「首页」「通知公告」为固定一级入口，不参与 authStore.menus 动态渲染（见模板顶部），故下面不再重复列出
//   2) 「加入课程」原为「课程学习」子菜单，现已提升为一级入口（先加入课程，再进入课程学习查看资料 / 练题）
//   3) 「练习卷」「自由刷题」已并入「课程学习」子菜单，不再作为独立的一级「自主练习」入口
//   4) 「我的错题」（系统自动归集的错题）与「我的收藏」（自己标记的重点题）并列，构成两套复习体系
const STUDENT_MENU_ORDER = ['加入课程', '课程学习', '我的错题', '我的收藏', '我的考试', '我的成绩', '个人中心']
// 「课程学习」子菜单顺序：课程作业 → 自由刷题 → 资料下载（「加入课程」已提升为一级入口）
const COURSE_CHILD_ORDER = ['课程作业', '自由刷题', '资料下载']
// 子菜单显示名映射：数据库里仍是旧名称（练习卷 / 课程资料）时统一显示为新名称，
// 老库无需执行 database/migrate_rename_student_course_children.sql 也能看到新名字；
// 执行脚本后菜单名本身就是新名，映射自然不再命中。
const MENU_NAME_ALIAS = { 练习卷: '课程作业', 课程资料: '资料下载' }
/** 菜单显示名：接口数据保持原样，仅在渲染与排序时按别名归一 */
const menuLabel = menu => (menu && MENU_NAME_ALIAS[menu.menuName]) || (menu && menu.menuName) || ''
// 「加入课程」一级入口对应的路由与菜单名：数据库里它可能仍是「课程学习」的子菜单，
// 这里统一提取出来渲染为一级菜单；老库可执行
// database/migrate_promote_join_course_menu.sql 同步调整库结构
const JOIN_COURSE_ROUTE = '/student/course/join'
const JOIN_COURSE_NAME = '加入课程'
// 「个人中心 → 我的课程」路由：数据库菜单（sys_menu，layout=student）尚未执行
// database/migrate_add_student_my_courses_menu.sql 时，前端在「个人中心」下兜底补上该入口，
// 保证学生不执行脚本也能看到；执行脚本后由数据库菜单接管，按路由去重不会出现两个入口
const MY_COURSE_ROUTE = '/student/profile/my-courses'
const ABOUT_ROUTE = '/student/profile/about'
// 已固定在模板中渲染的一级菜单，动态菜单里出现同名项时过滤，避免重复入口
const FIXED_ROOT_MENUS = ['首页', '通知公告']
// 固定入口占用的路由：历史数据库菜单名为「消息通知」，旧个人中心子菜单用
// /student/profile/notifications，同一个页面只允许一个入口，这里按路由兜底去重
const FIXED_ROOT_PATHS = ['/student/messages', '/student/profile/notifications']

const menus = computed(() => {
  const list = authStore.menus || []
  // 兼容后端菜单数据尚未执行合并脚本的情况：
  // 把一级「自主练习」的子菜单并入「课程学习」，并从菜单树中移除该一级入口
  const practiceChildren = []
  const roots = []
  for (const menu of list) {
    if (menu.menuName === '自主练习') {
      practiceChildren.push(...(menu.children || []))
      continue
    }
    // 「首页」「通知公告」已在模板中固定渲染，这里按菜单名 + 路由双重过滤，避免重复入口
    if (FIXED_ROOT_MENUS.includes(menu.menuName)) continue
    if (FIXED_ROOT_PATHS.includes(menu.routePath)) continue
    roots.push(menu)
  }

  // 「加入课程」提升为一级菜单：无论库里挂在「课程学习」下还是已提升为一级，统一收集到这里
  let joinCourse = null
  const isJoinCourse = m => !!m && (m.routePath === JOIN_COURSE_ROUTE || m.menuName === JOIN_COURSE_NAME)

  const mapped = roots.map(menu => {
    if (menu.menuName === '课程学习') {
      const seen = new Set()
      const children = [...(menu.children || []), ...practiceChildren]
        .filter(c => {
          const key = c.routePath || c.menuName
          if (seen.has(key)) return false
          seen.add(key)
          // 「加入课程」已提升为一级入口，这里不再作为子菜单渲染，避免出现两个入口
          if (isJoinCourse(c)) {
            joinCourse = joinCourse || c
            return false
          }
          return true
        })
        .sort((a, b) => {
          const ia = COURSE_CHILD_ORDER.indexOf(menuLabel(a))
          const ib = COURSE_CHILD_ORDER.indexOf(menuLabel(b))
          return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib)
        })
      return { ...menu, children, hasChildren: children.length > 0 }
    }
    // 个人中心：移除「消息通知」（已独立为通知公告一级入口，消除入口重复），并兜底补上「我的课程」
    if (menu.menuName === '个人中心') {
      const children = (menu.children || []).filter(c => c.menuName !== '消息通知')
      if (!children.some(c => c.routePath === MY_COURSE_ROUTE)) {
        const myCourse = {
          id: 'local-my-courses',
          menuName: '我的课程',
          routePath: MY_COURSE_ROUTE,
          icon: 'Files'
        }
        // 插在「关于系统」之前，与数据库菜单排序一致（个人信息 → 我的课程 → 关于系统）
        const aboutIndex = children.findIndex(c => c.routePath === ABOUT_ROUTE)
        if (aboutIndex === -1) children.push(myCourse)
        else children.splice(aboutIndex, 0, myCourse)
      }
      return { ...menu, children }
    }
    // 数据库已把「加入课程」提升为一级菜单时，同样收集进来按一级入口渲染
    if (isJoinCourse(menu)) joinCourse = joinCourse || menu
    return menu
  })

  // 过滤空的父级分组（子菜单被全部提升后不再渲染空分组）
  const visible = mapped.filter(m => !(m.hasChildren && (!m.children || m.children.length === 0)))

  return (joinCourse ? [{ ...joinCourse, children: undefined, hasChildren: false }, ...visible] : visible)
    .sort((a, b) => {
      const ia = STUDENT_MENU_ORDER.indexOf(a.menuName)
      const ib = STUDENT_MENU_ORDER.indexOf(b.menuName)
      return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib)
    })
})

// ===== 移动端抽屉侧边栏状态：路由跳转后自动收起 =====
const sidebarOpen = ref(false)
watch(() => route.path, () => { sidebarOpen.value = false })

const unreadCount = ref(0)
const noticeList = ref([])
const noticeLoading = ref(false)

const detailVisible = ref(false)
const currentNotice = ref(null)

let pollTimer = null

const iconMap = {
  Notebook, DataAnalysis, EditPen, Warning, UserFilled,
  Document, Postcard, Bell, Key, InfoFilled,
  Files, MagicStick, Star, Plus, Reading, FolderOpened
}

function getIcon(name) {
  if (!name) return Document
  return iconMap[name] || Document
}

const defaultOpeneds = computed(() => {
  const path = route.path
  const opened = []
  for (const menu of menus.value) {
    if (menu.hasChildren && menu.children) {
      for (const child of menu.children) {
        if (child.routePath && path.startsWith(child.routePath)) {
          opened.push(String(menu.id))
          break
        }
      }
    }
  }
  return opened
})

async function loadUnreadCount() {
  if (!authStore.token) return
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data || 0
  } catch {}
}

async function loadNoticeList() {
  noticeLoading.value = true
  try {
    const res = await getUserNoticeList(50)
    noticeList.value = res.data || []
  } catch (err) {
    ElMessage.error(err?.message || '加载消息失败')
  } finally {
    noticeLoading.value = false
  }
}

function handlePopoverShow() {
  loadNoticeList()
}

async function handleNoticeClick(item) {
  // 先标记已读
  if (item.isRead !== 1) {
    try {
      await markNoticeRead(item.noticeId)
      item.isRead = 1
      if (unreadCount.value > 0) unreadCount.value--
    } catch {}
  }
  // 打开详情弹窗
  currentNotice.value = item
  detailVisible.value = true
}

async function handleMarkAllRead() {
  try {
    await markAllRead()
    unreadCount.value = 0
    for (const n of noticeList.value) {
      n.isRead = 1
    }
    ElMessage.success('已全部标记为已读')
  } catch (err) {
    ElMessage.error(err?.message || '操作失败')
  }
}

/* ========== 确认收到消息 ========== */
const confirming = ref(false)

async function handleConfirm(goExamFlag) {
  const item = currentNotice.value
  if (!item || !item.noticeId) return
  confirming.value = true
  try {
    await confirmNotice(item.noticeId)
    item.confirmStatus = 1
    ElMessage.success('已确认收到')
    // 考试通知选择「立即前往」：跳转到答题页
    if (goExamFlag) goExam(item)
  } catch (err) {
    ElMessage.error(err?.message || '确认失败')
  } finally {
    confirming.value = false
  }
}

// 跳转到考试列表页
function goExam() {
  detailVisible.value = false
  router.push('/student/exams')
}

function summary(text) {
  if (!text) return ''
  return text.length > 50 ? text.slice(0, 50) + '...' : text
}

function formatTime(t) {
  if (!t) return ''
  // 简化：直接返回，后端已格式化
  return String(t).replace('T', ' ').slice(0, 16)
}

function startPolling() {
  loadUnreadCount()
  pollTimer = setInterval(() => {
    loadUnreadCount()
  }, 30000) // 每 30 秒刷新一次未读数
}

function handleCommand(command) {
  if (command === 'logout') {
    logout().catch(() => {})
    authStore.clearAuth()
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/student/profile/info')
  }
}

onMounted(async () => {
  if (authStore.token && menus.value.length === 0) {
    await authStore.loadMenus()
  }
  startPolling()
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

/* 学生端侧边渐变：绿-墨色渐变，和管理端蓝-靛区分（呼应角色主题） */
.sidebar {
  background: linear-gradient(180deg, #0f766e 0%, #0f172a 100%);
  overflow-y: auto;
  box-shadow: 2px 0 18px rgba(15, 23, 42, .12);
}
.sidebar::-webkit-scrollbar { width: 6px; }
.sidebar::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, .25);
  border-radius: 999px;
}
.sidebar::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, .45);
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 18px;
  gap: 10px;
  color: #fff;
  font-weight: 700;
  letter-spacing: .02em;
  border-bottom: 1px solid rgba(148, 163, 184, .15);
  margin-bottom: 8px;
}
.logo__brand {
  width: 32px; height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #10b981 0%, #0ea5e9 100%);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 16px;
  box-shadow: 0 6px 14px rgba(16, 185, 129, .35);
}
.logo__title {
  color: #f1f5f9;
  font-size: 15px;
}

/* 顶栏：毛玻璃效果由 app-layout__header 提供 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  z-index: 2;
}
.header-left {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-color-text-primary, #0f172a);
  letter-spacing: .02em;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 用户信息胶囊按钮 */
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 999px;
  color: var(--app-color-text-regular, #334155);
  font-size: 14px;
  font-weight: 500;
  transition: all .2s ease;
  border: 1px solid var(--app-color-border, #e2e8f0);
  background: rgba(255,255,255, .7);
}
.user-info:hover {
  background: #ecfdf5;
  border-color: #6ee7b7;
  color: #047857;
  box-shadow: 0 2px 10px rgba(16, 185, 129, .08);
}
.user-info .avatar {
  width: 26px; height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #10b981, #0ea5e9);
  color: #fff;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700;
}

/* 铃铛 */
.bell-wrapper {
  position: relative;
  cursor: pointer;
  color: var(--app-color-text-regular, #334155);
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid var(--app-color-border, #e2e8f0);
  background: rgba(255,255,255, .7);
  transition: all 0.2s;
}
.bell-wrapper:hover {
  background: #ecfdf5;
  color: #047857;
  border-color: #6ee7b7;
  box-shadow: 0 2px 10px rgba(16, 185, 129, .08);
}
.bell-icon { font-size: 17px; }
.bell-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  background: linear-gradient(135deg, #f87171, #ef4444);
  color: #fff;
  font-size: 10px;
  border-radius: 999px;
  text-align: center;
  box-sizing: border-box;
  box-shadow: 0 2px 6px rgba(239, 68, 68, .35);
}

.main {
  background: var(--app-color-bg, #f8fafc);
  padding: 20px;
  overflow-y: auto;
}

/* 菜单精准覆盖（学生端激活竖条用绿-青渐变） */
.sidebar-menu {
  border-right: none !important;
  background: transparent !important;
  padding: 6px 8px 20px;
}
:deep(.sidebar-menu .el-menu-item),
:deep(.sidebar-menu .el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin: 4px 0;
  border-radius: 10px;
  color: rgba(226, 232, 240, .85) !important;
  transition: all .2s ease;
  position: relative;
  padding: 0 14px !important;
  font-weight: 500;
  font-size: 14px;
}
:deep(.sidebar-menu .el-menu-item .el-icon),
:deep(.sidebar-menu .el-sub-menu__title .el-icon) {
  font-size: 16px;
  margin-right: 10px;
  opacity: .9;
}
:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  background: rgba(51, 65, 85, .7) !important;
  color: #ffffff !important;
}
/* 学生端激活：绿-青渐变竖条 */
:deep(.sidebar-menu .el-menu-item.is-active) {
  background: linear-gradient(90deg,
    rgba(16, 185, 129, .22) 0%,
    rgba(14, 165, 233, .16) 100%) !important;
  color: #ffffff !important;
  box-shadow: inset 0 0 0 1px rgba(110, 231, 183, .15);
}
:deep(.sidebar-menu .el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0; top: 10px; bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, #34d399 0%, #22d3ee 100%);
  box-shadow: 0 0 10px rgba(52, 211, 153, .5);
}
:deep(.sidebar-menu .el-sub-menu.is-opened > .el-sub-menu__title) {
  color: #ffffff !important;
  background: rgba(51, 65, 85, .45) !important;
}
:deep(.sidebar-menu .el-sub-menu .el-menu) { background: transparent !important; }
:deep(.sidebar-menu .el-sub-menu .el-menu-item) {
  padding-left: 44px !important;
  height: 40px;
  line-height: 40px;
  font-size: 13px;
  border-radius: 8px;
  margin: 2px 0;
}
:deep(.sidebar-menu .el-sub-menu .el-menu-item.is-active::before) {
  top: 10px; bottom: 10px;
  left: 20px;
}

/* ===== 菜单微动效增量：hover 菜单项右移 + 图标放大提亮，active 图标发光 ===== */
:deep(.sidebar-menu .el-menu-item .el-icon),
:deep(.sidebar-menu .el-sub-menu__title .el-icon) {
  transition: all .2s ease;
}
:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  transform: translateX(3px);
}
:deep(.sidebar-menu .el-menu-item:hover .el-icon),
:deep(.sidebar-menu .el-sub-menu__title:hover .el-icon) {
  transform: scale(1.15);
  color: #93c5fd;
}
:deep(.sidebar-menu .el-menu-item.is-active .el-icon) {
  filter: drop-shadow(0 0 6px rgba(96, 165, 250, .8));
}

/* ================== 移动端适配（≤820px）：抽屉侧边栏 + 内容收紧 ================== */
.menu-toggle { display: none; }
.sidebar-mask { display: none; }

@media (max-width: 820px) {
  /* 顶栏汉堡按钮显示 */
  .menu-toggle {
    display: inline-flex;
    font-size: 22px;
    color: #475569;
    cursor: pointer;
    margin-right: 10px;
  }
  /* 顶栏长标题隐藏，避免挤压 */
  .header-left span { display: none; }

  /* 侧边栏抽屉化：固定定位 + 滑入滑出 */
  .sidebar {
    position: fixed !important;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 1001;
    transform: translateX(-100%);
    transition: transform .28s ease;
    box-shadow: none;
  }
  .sidebar.open {
    transform: translateX(0);
    box-shadow: 6px 0 24px rgba(15, 23, 42, .28);
  }

  /* 遮罩：随 .show 淡入，点击收起侧边栏 */
  .sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, .45);
    z-index: 1000;
    opacity: 0;
    pointer-events: none;
    transition: opacity .25s ease;
  }
  .sidebar-mask.show {
    opacity: 1;
    pointer-events: auto;
  }

  /* 内容区收紧留白，给表格更多宽度 */
  .main {
    padding: 10px !important;
  }
}

/* 消息面板卡片 */
.notice-panel {
  min-height: 120px;
  max-height: 420px;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 8px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.unread-tip {
  font-size: 12px;
  color: #f56c6c;
  margin-right: 8px;
}

.notice-list {
  flex: 1;
  overflow-y: auto;
  max-height: 300px;
}

.notice-item {
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  border-bottom: 1px solid #f2f6fc;
  transition: background 0.2s;
}

.notice-item:hover {
  background: #f5f7fa;
}

.notice-item.unread {
  background: #fff;
}

.notice-item.unread:hover {
  background: #ecf5ff;
}

.notice-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.notice-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: #f56c6c;
  border-radius: 50%;
  flex-shrink: 0;
}

.notice-time {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}

.notice-sender {
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
}

.notice-summary {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-footer {
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
  text-align: center;
  font-size: 11px;
  color: #c0c4cc;
}

/* 消息详情弹窗 */
.notice-detail {
  line-height: 1.8;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.detail-label {
  color: #606266;
  min-width: 70px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.detail-divider {
  border-top: 1px dashed #e4e7ed;
  margin: 12px 0;
}

.notice-content {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
  font-size: 14px;
  line-height: 1.8;
  min-height: 80px;
  max-height: 400px;
  overflow-y: auto;
}

/* 确认收到区域 */
.confirm-area {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #e4e7ed;
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>

<style>
/* popover 全局样式（消除 popover 默认 padding） */
.notice-popover {
  padding: 12px !important;
}
</style>
