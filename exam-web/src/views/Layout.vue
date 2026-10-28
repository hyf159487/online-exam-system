<template>
  <el-container class="layout">
    <!-- 侧边栏（移动端抽屉化：.open 时滑出） -->
    <el-aside width="220px" class="sidebar" :class="{ open: sidebarOpen }">
      <div class="logo">
        <span class="logo__brand">📚</span>
        <span class="logo__title">{{ isAdmin ? '管理员端' : '教师端' }}</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        :router="true"
        :unique-opened="true"
        background-color="transparent"
        text-color="rgba(226, 232, 240, .85)"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <!-- 动态菜单：从 authStore.menus 渲染 -->
        <template v-for="menu in menus" :key="menu.id">
          <!-- 父菜单：有子菜单 → el-sub-menu -->
          <el-sub-menu
            v-if="menu.hasChildren && menu.children && menu.children.length > 0"
            :index="String(menu.id)"
          >
            <template #title>
              <el-icon><component :is="getIcon(menu.icon)" /></el-icon>
              <span>{{ menu.menuName }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.id"
              :index="child.routePath"
            >
              <el-icon><component :is="getIcon(child.icon)" /></el-icon>
              <span>{{ child.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 叶子菜单：无子菜单 → el-menu-item -->
          <el-menu-item
            v-else
            :index="menu.routePath"
          >
            <el-icon><component :is="getIcon(menu.icon)" /></el-icon>
            <span>{{ menu.menuName }}</span>
          </el-menu-item>
        </template>

        <!-- 兜底：菜单为空时显示首页（避免侧边栏全白） -->
        <el-menu-item v-if="menus.length === 0" index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>

        <!-- =========================================================================
             「个人中心」模块（固定菜单，不依赖 DB；与学生端结构对齐，管理员/教师均可见）
             - 父菜单：个人中心（图标 UserFilled）
             - 子菜单：个人信息 /profile/info、关于系统 /profile/about
             - 「修改密码」不再是独立菜单：已合并进「个人信息」页面底部（密码修改分区）
             ========================================================================= -->
        <el-sub-menu index="profile-parent">
          <template #title>
            <el-icon><UserFilled /></el-icon>
            <span>个人中心</span>
          </template>
          <el-menu-item index="/profile/info">
            <el-icon><Postcard /></el-icon>
            <span>个人信息</span>
          </el-menu-item>
          <el-menu-item index="/profile/about">
            <el-icon><InfoFilled /></el-icon>
            <span>关于系统</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container class="main-wrapper">
      <!-- 顶部导航 -->
      <el-header class="header app-layout__header">
        <div class="header-left">
          <!-- 移动端：汉堡按钮呼出抽屉侧边栏（桌面隐藏） -->
          <el-icon class="menu-toggle" @click="sidebarOpen = true"><Expand /></el-icon>
          <span>在线考试与题库管理系统 - {{ isAdmin ? '管理员端' : '教师端' }}</span>
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
              <span class="bell-wrapper" title="消息通知">
                <el-icon class="bell-icon"><Bell /></el-icon>
                <span v-if="unreadCount > 0" class="bell-badge">
                  {{ unreadCount > 99 ? '99+' : unreadCount }}
                </span>
              </span>
            </template>

            <!-- 消息面板内容 -->
            <div class="notice-panel">
              <div class="panel-header">
                <span class="panel-title">消息通知</span>
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
              {{ authStore.userInfo?.realName || authStore.userInfo?.username || '用户' }}
              <el-tag size="small" style="margin-left: 8px" :type="isAdmin ? 'danger' : 'primary'">
                {{ isAdmin ? '管理员' : '教师' }}
              </el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon style="margin-right: 6px"><UserFilled /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon style="margin-right: 6px"><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区：路由切换淡入过渡；:key="$route.fullPath" 强制每次路由切换都重建组件（避免同组件跨路由复用导致数据不刷新） -->
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
import { getUnreadCount, getUserNoticeList, markNoticeRead, markAllRead } from '@/api/notice'
// 全部用到的 Element Plus 图标
import {
  HomeFilled, Document, Notebook, Timer, DataAnalysis,
  User, School, Reading, ArrowDown, Setting, Checked,
  TrendCharts, Bell, EditPen, Warning, Files, View,
  List, UserFilled, Collection, Tools, DocumentCopy,
  DataLine, Histogram, Download, Promotion, Postcard,
  Key, InfoFilled, Clock, SwitchButton, Expand, Upload
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isAdmin = computed(() => authStore.userInfo?.role === 2)

// ===== 移动端抽屉侧边栏状态：路由跳转后自动收起 =====
const sidebarOpen = ref(false)
watch(() => route.path, () => { sidebarOpen.value = false })

// 当前用户的菜单树（从 auth store 拿，登录时已写入）
// 按业务流顺序重排：首页 → 系统配置 → 基础数据 → 题库试卷 → 考试相关 → 统计 → 消息
// 注：原顶级「试题审核管理」已并入「题库管理」（DB 侧由
// migrate_merge_audit_menu_into_question_bank.sql 把两个子菜单挂到题库管理下），
// 这里不再需要它的排序项。
const MENU_ORDER = [
  '首页', '系统管理', '账号管理', '班级管理', '课程管理',
  '题库管理', '试卷管理', '考试管理',
  // 「作业管理」历史名称：自主练习管理 → 发布作业 → 作业管理，旧名都保留，兼容未执行更名脚本的库
  '作业管理', '发布作业', '自主练习管理', '成绩管理', '教学统计分析', '消息通知'
]
// 管理员端已下线的模块（统一归教师端使用）：侧边栏不渲染，数据库侧由迁移脚本回收授权
// 注：安排考试 / 发布考试 / 监控考生 / 成绩批阅都属于教师业务，管理员端不再提供
//     「考试管理」「成绩管理」入口；接口权限保持不变（管理员仍可只读查看课程内考试数据）
// 注：「试卷管理」的 list/detail 接口仍对管理员开放，管理员维护题目需要读取试卷数据
const ADMIN_HIDDEN_MENUS = ['作业管理', '发布作业', '自主练习管理', '试卷管理', '考试管理', '成绩管理']
// 系统管理子菜单目标顺序：字典 → 系统参数 → 角色权限 → 操作日志
const CHILD_ORDER = {
  '系统管理': ['字典管理', '系统参数配置', '角色与权限配置', '操作日志']
}
const menus = computed(() => {
  const role = authStore.userInfo?.role
  const list = authStore.menus || []
  return [...list]
    // 管理员端已下线的模块（归教师使用）：此处兜底过滤，
    // 避免数据库授权尚未回收时管理员侧边栏仍出现这些入口
    .filter(menu => !(role === 2 && ADMIN_HIDDEN_MENUS.includes(menu.menuName)))
    .map(menu => {
      // 消息通知：教师端与管理员端同样保留「发布通知」子菜单——
      // 教师可向指定班级 / 自己创建考试的考生发通知（后端 NoticeController 已放开教师 role=1，
      // Service 侧限制教师不能群发全体学生/全体教师），因此这里不再按角色裁剪该子菜单。
      let children = menu.children
      // 子菜单排序（系统管理）
      const childOrder = CHILD_ORDER[menu.menuName]
      if (childOrder && children) {
        children = [...children].sort((a, b) => {
          const ia = childOrder.indexOf(a.menuName)
          const ib = childOrder.indexOf(b.menuName)
          return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib)
        })
      }
      // 账号管理：从叶子菜单改为父菜单（管理员/教师/学生/批量导入）
      if (menu.menuName === '账号管理') {
        return {
          ...menu,
          hasChildren: true,
          routePath: null,
          children: [
            { id: 'account-admins', menuName: '管理员账号', routePath: '/account/admins', icon: 'User' },
            { id: 'account-teachers', menuName: '教师账号', routePath: '/account/teachers', icon: 'User' },
            { id: 'account-students', menuName: '学生账号', routePath: '/account/students', icon: 'User' },
            { id: 'account-import', menuName: '账号批量导入', routePath: '/account/import', icon: 'Upload' }
          ]
        }
      }
      return { ...menu, children }
    })
    .sort((a, b) => {
      const ia = MENU_ORDER.indexOf(a.menuName)
      const ib = MENU_ORDER.indexOf(b.menuName)
      return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib)
    })
})

// 图标名 → 组件 映射，供 <component :is> 动态渲染
const iconMap = {
  HomeFilled, Document, Notebook, Timer, DataAnalysis,
  User, School, Reading, Setting, Checked,
  TrendCharts, Bell, EditPen, Warning, Files, View,
  List, UserFilled, Collection, Tools, DocumentCopy,
  DataLine, Histogram, Download, Promotion, Postcard,
  Key, InfoFilled, Clock, Upload
}

function getIcon(name) {
  if (!name) return Document        // 兜底图标
  return iconMap[name] || Document
}

// 侧边栏当前高亮菜单：详情类页面（如 /courses/5、/my-courses/5）按所属列表菜单高亮。
// 规则：在所有菜单 routePath 中取与当前路径「相等或为其父级」的最长匹配，避免进入详情页后菜单失去高亮。
const activeMenu = computed(() => {
  const path = route.path
  const routePaths = []
  for (const menu of menus.value) {
    if (menu.routePath) routePaths.push(menu.routePath)
    if (menu.children) {
      for (const child of menu.children) {
        if (child.routePath) routePaths.push(child.routePath)
      }
    }
  }
  let matched = ''
  for (const rp of routePaths) {
    if (rp && (path === rp || path.startsWith(rp + '/')) && rp.length > matched.length) {
      matched = rp
    }
  }
  return matched || path
})

// 根据当前路径自动展开对应的子菜单
// 路径匹配某个子菜单 routePath 时，把父菜单 id 加入 defaultOpeneds
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
  // 固定「个人中心」模块的路径自动展开
  if (path.startsWith('/profile/')) {
    opened.push('profile-parent')
  }
  return opened
})

// 每次进入布局都静默拉一次最新菜单：
// 1) 刷新页面后 menus 为空时（极端情况）必须有；
// 2) 后端菜单/角色授权有变更（如新增「我的课程」）时，刷新页面即可生效，无需退出重登。
onMounted(async () => {
  if (authStore.token) {
    await authStore.loadMenus()
  }
  // 启动未读消息轮询（30 秒刷新一次）
  loadUnreadCount()
  pollTimer = setInterval(loadUnreadCount, 30000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})

function handleCommand(command) {
  if (command === 'profile') {
    // 管理员 / 教师：跳转到通用个人中心 - 个人信息页
    router.push('/profile/info')
    return
  }
  if (command === 'logout') {
    logout().catch(() => {})
    authStore.clearAuth()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

/* ========== 铃铛消息通知（教师/管理员端） ========== */
const unreadCount = ref(0)
const noticeList = ref([])
const noticeLoading = ref(false)
const detailVisible = ref(false)
const currentNotice = ref(null)
let pollTimer = null

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

function summary(text) {
  if (!text) return ''
  return text.length > 50 ? text.slice(0, 50) + '...' : text
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

</script>

<style scoped>
.layout {
  height: 100vh;
}

/* ======================================================================
 * 侧边栏：外层渐变（slate-800 → slate-900）；el-menu 使用 transparent 背景
 * 避免 el-menu 的 background-color 属性只能吃纯色、不支持渐变的问题（经验 447101）
 * ====================================================================== */
.sidebar {
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
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

/* 顶部 LOGO：两栏（图标 + 标题），有一条分隔线 */
.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 18px;
  gap: 10px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: .02em;
  border-bottom: 1px solid rgba(148, 163, 184, .15);
  margin-bottom: 8px;
}
.logo__brand {
  width: 32px; height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 16px;
  box-shadow: 0 6px 14px rgba(99, 102, 241, .35);
}
.logo__title {
  color: #f1f5f9;
  font-size: 15px;
}

/* 顶栏：基础布局，毛玻璃效果由 .app-layout__header（theme.css）控制 */
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
.main {
  background: var(--app-color-bg, #f8fafc);
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden; /* 宽内容（如表格）改为内部横向滚动，不撑破整个布局 */
}
/* 关键：flex 子项默认 min-width:auto 会被宽表格撑开，导致整页横向溢出、
   表格右侧列被推出窗口且看不到滚动条。置 0 后宽度锁定，表格内部自己出滚动条。 */
.main-wrapper {
  min-width: 0;
}

/* ======================================================================
 * 菜单内部结构精准覆盖（::v-deep，scoped 下仍可命中 Element Plus 内部 class）
 * 关键：激活项左侧渐变彩色竖条（视觉锚点）+ hover 柔和深蓝半透明背景
 * ====================================================================== */
.sidebar-menu {
  border-right: none !important;
  background: transparent !important;
  padding: 6px 8px 20px;
}
/* menu item / sub-menu title 通用块 */
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
/* hover 状态：浅深蓝背景 + 字变白 + 微抬升阴影（暗色底的柔光感） */
:deep(.sidebar-menu .el-menu-item:hover),
:deep(.sidebar-menu .el-sub-menu__title:hover) {
  background: rgba(51, 65, 85, .7) !important;
  color: #ffffff !important;
}
:deep(.sidebar-menu .el-menu-item:hover .el-icon),
:deep(.sidebar-menu .el-sub-menu__title:hover .el-icon) { opacity: 1; }

/* 激活项（el-menu-item.is-active）：彩色背景 + 左 3px 彩色竖条 + 柔光阴影 */
:deep(.sidebar-menu .el-menu-item.is-active) {
  background: linear-gradient(90deg,
    rgba(59, 130, 246, .25) 0%,
    rgba(99, 102, 241, .18) 100%) !important;
  color: #ffffff !important;
  box-shadow: inset 0 0 0 1px rgba(147, 197, 253, .15);
}
:deep(.sidebar-menu .el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0; top: 10px; bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, #60a5fa 0%, #8b5cf6 100%);
  box-shadow: 0 0 10px rgba(96, 165, 250, .5);
}
:deep(.sidebar-menu .el-menu-item.is-active .el-icon) { opacity: 1; }

/* sub-menu 展开态的标题和子菜单：字色加深 */
:deep(.sidebar-menu .el-sub-menu.is-opened > .el-sub-menu__title) {
  color: #ffffff !important;
  background: rgba(51, 65, 85, .45) !important;
}
:deep(.sidebar-menu .el-sub-menu .el-menu) {
  background: transparent !important;
}
:deep(.sidebar-menu .el-sub-menu .el-menu-item) {
  padding-left: 44px !important;
  height: 40px;
  line-height: 40px;
  font-size: 13px;
  border-radius: 8px;
  margin: 2px 0;
}
/* 子菜单激活竖条稍微缩短一点 */
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

/* 用户信息（右上角下拉按钮） */
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
  background: var(--app-primary-lighter, #eff6ff);
  border-color: #93c5fd;
  color: var(--app-primary, #2563eb);
  box-shadow: 0 2px 10px rgba(37, 99, 235, .08);
}
.user-info .avatar {
  width: 26px; height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700;
}

/* 铃铛（通知图标） */
.bell-wrapper {
  position: relative;
  cursor: pointer;
  color: var(--app-color-text-regular, #334155);
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid var(--app-color-border, #e2e8f0);
  background: rgba(255,255,255,.7);
  transition: all .2s;
}
.bell-wrapper:hover {
  background: #eff6ff;
  color: var(--app-primary, #2563eb);
  border-color: #93c5fd;
  box-shadow: 0 2px 10px rgba(37, 99, 235, .08);
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
</style>

<style>
/* popover 全局样式（消除 popover 默认 padding） */
.notice-popover {
  padding: 12px !important;
}
</style>
