<template>
  <div class="student-dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #409EFF">
              <el-icon :size="28"><Notebook /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ stats.pendingCount }}</div>
              <div class="stat-label">待参加考试</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #67C23A">
              <el-icon :size="28"><EditPen /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ stats.finishedCount }}</div>
              <div class="stat-label">已完成考试</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #E6A23C">
              <el-icon :size="28"><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ stats.wrongCount }}</div>
              <div class="stat-label">我的错题</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #F56C6C">
              <el-icon :size="28"><Bell /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ stats.unreadCount }}</div>
              <div class="stat-label">未读消息</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 学生端功能总览：介绍系统能做什么，点击卡片直达对应功能；右上角可收起 / 展开 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>学生端功能总览</span>
          <div class="card-header__ops">
            <span class="section-tip">
              {{ overviewVisible ? '点击卡片可直接进入对应功能' : `已收起 ${featureList.length} 个功能入口` }}
            </span>
            <el-button link type="primary" size="small" @click="toggleOverview">
              <el-icon style="margin-right: 4px">
                <ArrowUp v-if="overviewVisible" />
                <ArrowDown v-else />
              </el-icon>{{ overviewVisible ? '收起' : '展开' }}
            </el-button>
          </div>
        </div>
      </template>
      <el-collapse-transition>
        <div v-show="overviewVisible" class="feature-grid">
          <div
            v-for="item in featureList"
            :key="item.path"
            class="feature-item"
            @click="$router.push(item.path)"
          >
            <div class="feature-item__icon" :style="{ background: item.color }">
              <el-icon :size="20"><component :is="item.icon" /></el-icon>
            </div>
            <div class="feature-item__body">
              <div class="feature-item__title">{{ item.title }}</div>
              <div class="feature-item__desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 进行中/即将开始考试提醒 -->
    <el-card v-if="ongoingExams.length > 0" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>进行中 / 即将开始的考试</span>
          <el-button link type="primary" @click="$router.push('/student/exams')">查看全部</el-button>
        </div>
      </template>
      <div class="ongoing-list">
        <div v-for="exam in ongoingExams" :key="exam.id" class="ongoing-item">
          <div class="ongoing-info">
            <el-tag :type="exam.runtimeStatus === 1 ? 'danger' : 'info'" size="small" effect="dark">
              {{ exam.runtimeStatus === 1 ? '进行中' : '未开始' }}
            </el-tag>
            <span class="ongoing-name">{{ exam.examName }}</span>
            <span class="ongoing-meta">{{ exam.paperName }} · {{ exam.duration }} 分钟</span>
          </div>
          <div class="ongoing-right">
            <span class="ongoing-time">{{ exam.startTime }} ~ {{ exam.endTime }}</span>
            <el-button
              v-if="exam.runtimeStatus === 1"
              type="primary"
              size="small"
              @click="$router.push(`/student/exams/${exam.id}/do`)"
            >进入考试</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 欢迎 + 快捷入口 + 功能说明 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>欢迎使用在线考试与题库管理系统</span>
          <el-tag type="success" effect="dark" size="small">学生端</el-tag>
        </div>
      </template>
      <div class="welcome">
        <p class="greet">{{ greeting }}，<strong>{{ authStore.userInfo?.realName || authStore.userInfo?.username || '同学' }}</strong>！祝你学习进步。</p>
        <p class="intro">在这里你可以参加考试、查询成绩，在「课程学习」下完成课程作业、自由刷题与资料下载，用错题本与收藏管理薄弱题目并随时向 AI 发起错题讲解；加入课程后还能下载课程资料，各类考试通知也会第一时间推送到你的消息中心。</p>
      </div>

      <!-- 快捷入口：点右侧「自定义」可增删与排序，配置存在账号下（换浏览器 / 换电脑依然保留） -->
      <el-divider content-position="left">
        <span class="divider-row">
          <span>快捷入口</span>
          <el-button
            link
            type="primary"
            size="small"
            class="divider-action"
            @click="openQuickLinkEditor"
          >
            <el-icon style="margin-right: 4px"><Setting /></el-icon>自定义
          </el-button>
        </span>
      </el-divider>
      <div class="quick-links">
        <el-button
          v-for="link in quickLinks"
          :key="link.path"
          type="primary"
          plain
          @click="$router.push(link.path)"
        >
          <el-icon style="margin-right: 5px"><component :is="getIcon(link.icon)" /></el-icon>{{ link.label }}
        </el-button>
        <span v-if="quickLinks.length === 0" class="quick-links-empty">
          还没有快捷入口，点上方「自定义」按需添加
        </span>
      </div>

      <el-divider content-position="left">功能说明</el-divider>
      <el-descriptions :column="1" border size="small" class="feature-desc">
        <el-descriptions-item label="通知公告" label-width="150px">
          接收教师发布的考试通知与系统公告，重要通知需点击「确认收到」；考试通知可一键前往考试列表。
        </el-descriptions-item>
        <el-descriptions-item label="加入课程" label-width="150px">
          浏览开放课程并申请加入，经任课教师审核通过后即可加入该课程；已加入的课程可申请退出。
        </el-descriptions-item>
        <el-descriptions-item label="我的课程（个人中心）" label-width="150px">
          集中查看我已加入的课程信息：课程编码、授课教师、参加人数、学习资料数与加入时间。
        </el-descriptions-item>
        <el-descriptions-item label="课程学习" label-width="150px">
          已加入课程的学习入口，包含「课程作业」「自由刷题」「资料下载」三个子菜单。
        </el-descriptions-item>
        <el-descriptions-item label="课程作业" label-width="150px">
          完成教师发布的成套作业：按试卷作答，提交后即时判分并查看解析，答错的题目自动归集到错题本。
        </el-descriptions-item>
        <el-descriptions-item label="自由刷题" label-width="150px">
          按学科 / 知识点自主选题随机练习，支持背题模式，提交后即时判分，答错的题目自动归集到错题本。
        </el-descriptions-item>
        <el-descriptions-item label="我的错题" label-width="150px">
          错题自动归集，支持重做与 AI 智能讲解（可围绕题目多轮追问）；重做答对后可按策略自动移出错题本。
        </el-descriptions-item>
        <el-descriptions-item label="我的收藏" label-width="150px">
          自主练习或背题时标记的重点题目会集中在此，方便随时回看与再次练习。
        </el-descriptions-item>
        <el-descriptions-item label="我的考试" label-width="150px">
          查看全部已发布考试；进行中的考试可进入作答，支持答题过程自动保存，断线重连后可恢复进度继续作答。
        </el-descriptions-item>
        <el-descriptions-item label="我的成绩" label-width="150px">
          查看历史考试成绩、得分与答题明细，客观题交卷后即时出分。
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 自定义快捷入口：左侧挑入口，右侧调整已选项与顺序 -->
    <el-dialog v-model="editorVisible" title="自定义快捷入口" width="780px" top="6vh" style="max-width: 92vw">
      <div class="editor-tip">
        配置保存在你的账号下，换浏览器 / 换电脑登录后依然保留；「恢复默认」会清空配置并回到系统内置入口。
      </div>
      <div class="quick-editor">
        <div class="editor-panel">
          <div class="panel-title">可选入口（按你的菜单权限列出）</div>
          <el-input
            v-model="poolKeyword"
            size="small"
            clearable
            placeholder="搜索入口名称 / 路由"
            style="margin-bottom: 8px"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <div class="panel-body">
            <div v-for="item in filteredPool" :key="item.path" class="pool-item">
              <span class="pool-name" :title="item.path">{{ item.label }}</span>
              <el-button
                v-if="isPicked(item.path)"
                size="small"
                type="info"
                plain
                @click="removeLink(item.path)"
              >已添加</el-button>
              <el-button v-else size="small" type="primary" plain @click="addLink(item)">添加</el-button>
            </div>
            <div v-if="filteredPool.length === 0" class="panel-empty">没有匹配的入口</div>
          </div>
        </div>

        <div class="editor-panel">
          <div class="panel-title">已选入口（{{ draftLinks.length }} 个，按顺序展示）</div>
          <div class="panel-body">
            <div v-for="(item, index) in draftLinks" :key="item.path" class="picked-item">
              <span class="picked-name" :title="item.path">{{ item.label }}</span>
              <span class="picked-ops">
                <el-button size="small" link :disabled="index === 0" @click="moveLink(index, -1)">上移</el-button>
                <el-button size="small" link :disabled="index === draftLinks.length - 1" @click="moveLink(index, 1)">下移</el-button>
                <el-button size="small" link type="danger" @click="removeLink(item.path)">移除</el-button>
              </span>
            </div>
            <div v-if="draftLinks.length === 0" class="panel-empty">还没有入口，从左侧挑选</div>
          </div>
        </div>
      </div>

      <!-- 菜单里没有的页面：自己填名称 + 路由 -->
      <el-divider content-position="left">自定义入口（菜单之外的页面）</el-divider>
      <div class="custom-form">
        <el-input v-model="customLink.label" size="small" placeholder="入口名称，如：错题重练" style="width: 190px" />
        <el-input v-model="customLink.path" size="small" placeholder="路由地址，如：/student/wrong-questions" style="width: 230px" />
        <el-input v-model="customLink.icon" size="small" placeholder="图标名（可选，如 Files）" style="width: 190px" />
        <el-button type="primary" plain size="small" @click="addCustomLink">加入已选</el-button>
      </div>

      <template #footer>
        <el-button link type="info" :disabled="saving" @click="resetQuickLinks">恢复默认</el-button>
        <el-button :disabled="saving" @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveQuickLinks">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  Notebook, EditPen, Warning, Bell, DataAnalysis, Setting, Search, Link, ArrowUp, ArrowDown,
  Reading, Files, MagicStick, Star, UserFilled, Plus, Document, Tickets,
  Timer, Checked, TrendCharts, Collection, DocumentCopy, Postcard, Key,
  InfoFilled, Clock, Upload, View, List, HomeFilled, Histogram, DataLine,
  Download, Promotion
} from '@element-plus/icons-vue'
import { getMyExams } from '@/api/studentExam'
import { getMyWrongStats } from '@/api/studentWrong'
import { getUnreadCount } from '@/api/notice'
import { getMyQuickLinks, saveMyQuickLinks, resetMyQuickLinks } from '@/api/userQuickLink'

const authStore = useAuthStore()

/** 功能总览：与左侧菜单一一对应，用于向学生介绍学生端能力（iconName 供快捷入口配置使用） */
const featureList = [
  { title: '通知公告', icon: Bell, iconName: 'Bell', color: '#FF9F43', path: '/student/messages',
    desc: '接收考试通知与系统公告，重要通知需确认收到，可一键前往考试列表。' },
  { title: '加入课程', icon: Plus, iconName: 'Plus', color: '#909399', path: '/student/course/join',
    desc: '浏览开放课程并申请加入，经任课教师审核通过后即可学习该课程资料。' },
  { title: '我的课程', icon: Collection, iconName: 'Collection', color: '#0EA5E9', path: '/student/profile/my-courses',
    desc: '个人中心下集中查看我已加入的课程信息，可直接进入课程资料与课程作业。' },
  { title: '课程学习', icon: Reading, iconName: 'Reading', color: '#409EFF', path: '/student/course/resources',
    desc: '已加入课程的学习入口，包含「课程作业」「自由刷题」「资料下载」三个子菜单。' },
  { title: '课程作业', icon: Files, iconName: 'Files', color: '#E6A23C', path: '/student/practice/papers',
    desc: '完成教师发布的成套作业，提交后即时判分并查看解析，答错自动归集错题。' },
  { title: '自由刷题', icon: MagicStick, iconName: 'MagicStick', color: '#9B59B6', path: '/student/practice/free',
    desc: '按学科 / 知识点自主选题随机练习，支持背题模式，答错自动归集错题。' },
  { title: '我的错题', icon: Warning, iconName: 'Warning', color: '#F56C6C', path: '/student/wrong-questions',
    desc: '考试与练习答错的题目自动归集，支持重做与 AI 智能讲解，达标后自动移出。' },
  { title: '我的收藏', icon: Star, iconName: 'Star', color: '#F7BA2A', path: '/student/favorites',
    desc: '练习或背题时标记的重点题目集中在此，方便随时回看与再次练习。' },
  { title: '我的考试', icon: Notebook, iconName: 'Notebook', color: '#409EFF', path: '/student/exams',
    desc: '查看已发布考试，进行中可作答；答题自动保存，断线重连后可继续。' },
  { title: '我的成绩', icon: DataAnalysis, iconName: 'DataAnalysis', color: '#67C23A', path: '/student/grades',
    desc: '查看历史考试成绩、得分与逐题作答明细，客观题交卷即出分。' },
  { title: '个人信息', icon: UserFilled, iconName: 'UserFilled', color: '#00A8A8', path: '/student/profile/info',
    desc: '维护个人基本信息与登录密码，查看系统版本说明（关于系统）。' }
]

/**
 * 功能总览卡片的收起 / 展开：默认展开，收起后只留卡片标题栏。
 * 选择记在本地（按账号隔离），下次进首页保持上次状态。
 */
const OVERVIEW_KEY_PREFIX = 'exam_student_overview_visible_'

function overviewStorageKey() {
  return OVERVIEW_KEY_PREFIX + (authStore.userInfo?.userId ?? 'default')
}

function readOverviewVisible() {
  try {
    return localStorage.getItem(overviewStorageKey()) !== '0'
  } catch (e) {
    return true
  }
}

const overviewVisible = ref(readOverviewVisible())

function toggleOverview() {
  overviewVisible.value = !overviewVisible.value
  try {
    localStorage.setItem(overviewStorageKey(), overviewVisible.value ? '1' : '0')
  } catch (e) {
    console.error('记录功能总览展开状态失败:', e)
  }
}

const stats = ref({
  pendingCount: 0,
  finishedCount: 0,
  wrongCount: 0,
  unreadCount: 0
})

/** 进行中或未开始且尚未参加的考试（提醒区） */
const ongoingExams = ref([])

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

/* ==================== 快捷入口（学生可自定义，配置跟账号走） ==================== */

/** 默认入口：从未自定义过时展示（与学生端常用入口一致） */
const DEFAULT_STUDENT_LINKS = [
  { label: '通知公告', path: '/student/messages', icon: 'Bell' },
  { label: '课程作业', path: '/student/practice/papers', icon: 'Files' },
  { label: '自由刷题', path: '/student/practice/free', icon: 'MagicStick' },
  { label: '我的错题', path: '/student/wrong-questions', icon: 'Warning' },
  { label: '我的成绩', path: '/student/grades', icon: 'DataAnalysis' },
  { label: '我的考试', path: '/student/exams', icon: 'Notebook' }
]

/** 图标名 → 组件：配置里只存图标名（字符串），方便序列化落库 / 落本地缓存 */
const iconMap = {
  Notebook, DataAnalysis, Plus, Reading, Files, MagicStick, Warning, Star, Bell, UserFilled,
  Document, Tickets, Timer, Checked, TrendCharts, Collection, DocumentCopy, EditPen,
  Postcard, Key, InfoFilled, Clock, Upload, View, List, HomeFilled, Histogram, DataLine,
  Download, Promotion, Search, Link
}

function getIcon(name) {
  return iconMap[name] || Link
}

/** 不进可选入口池的路径：首页自身 + 已被「通知公告」取代的旧入口 */
const HIDDEN_POOL_PATHS = ['/student/dashboard', '/student/profile/notifications']

/** 本地缓存 key：按账号隔离，与教师 / 管理员端同一套规则（数据库才是权威来源） */
const QUICK_LINK_KEY_PREFIX = 'exam_user_quick_links_'
function quickLinkStorageKey() {
  return QUICK_LINK_KEY_PREFIX + (authStore.userInfo?.userId ?? 'default')
}

function writeLocalCache(list) {
  try {
    localStorage.setItem(quickLinkStorageKey(), JSON.stringify(list))
  } catch (e) {
    console.error('写入本地快捷入口缓存失败:', e)
  }
}

function readLocalCache() {
  try {
    const arr = JSON.parse(localStorage.getItem(quickLinkStorageKey()) || 'null')
    return Array.isArray(arr) ? arr.filter(l => l && l.label && l.path) : []
  } catch (e) {
    return []
  }
}

function clearLocalCache() {
  localStorage.removeItem(quickLinkStorageKey())
}

/** 后端记录 → 首页渲染用的 { label, path, icon } */
function toLinkItem(row) {
  return { label: row?.label, path: row?.routePath, icon: row?.icon || 'Link' }
}

const customLinks = ref([])      // 账号保存过的入口列表
const isCustomized = ref(false)  // 是否自定义过：false 时展示内置默认入口

/** 首页实际渲染的快捷入口 */
const quickLinks = computed(() => (isCustomized.value ? customLinks.value : DEFAULT_STUDENT_LINKS))

/* -------------------- 编辑弹窗 -------------------- */

const editorVisible = ref(false)
const draftLinks = ref([])                  // 弹窗内编辑中的副本，点保存才生效
const poolKeyword = ref('')
const customLink = ref({ label: '', path: '', icon: '' })
const saving = ref(false)                   // 保存中：按钮转圈，防重复提交

/** 可选入口池：自己菜单权限内的入口（父 + 子），并用功能总览兜底去重 */
const menuPool = computed(() => {
  const pool = []
  const push = (label, path, icon) => {
    if (!label || !path || !String(path).startsWith('/')) return
    if (HIDDEN_POOL_PATHS.includes(path)) return
    if (pool.some(p => p.path === path)) return
    pool.push({ label, path, icon: icon || 'Document' })
  }
  ;(authStore.menus || []).forEach(menu => {
    push(menu.menuName, menu.routePath, menu.icon)
    ;(menu.children || []).forEach(child => push(child.menuName, child.routePath, child.icon))
  })
  featureList.forEach(f => push(f.title, f.path, f.iconName))
  return pool
})

const filteredPool = computed(() => {
  const kw = poolKeyword.value.trim().toLowerCase()
  if (!kw) return menuPool.value
  return menuPool.value.filter(p =>
    p.label.toLowerCase().includes(kw) || p.path.toLowerCase().includes(kw)
  )
})

/**
 * 读取我的快捷入口：以数据库（user_quick_link）为准，跟账号走，换电脑也保留。
 * 先用本地缓存垫一下避免闪动；接口失败时继续用缓存兜底，不影响首页展示。
 */
async function loadCustomLinks() {
  const cached = readLocalCache()
  if (cached.length > 0) {
    customLinks.value = cached
    isCustomized.value = true
  }
  try {
    const res = await getMyQuickLinks()
    const list = (res.data || []).map(toLinkItem).filter(l => l.label && l.path)
    if (list.length > 0) {
      customLinks.value = list
      isCustomized.value = true
      writeLocalCache(list)
    } else {
      // 数据库里没有记录 = 从未自定义（或已恢复默认）
      customLinks.value = []
      isCustomized.value = false
      clearLocalCache()
    }
  } catch (e) {
    console.error('读取自定义快捷入口失败，使用本地缓存兜底:', e)
  }
}

async function openQuickLinkEditor() {
  draftLinks.value = quickLinks.value.map(l => ({ ...l }))
  poolKeyword.value = ''
  customLink.value = { label: '', path: '', icon: '' }
  editorVisible.value = true
  // 学生端菜单由布局组件异步加载，弹窗打开时若还没有就补一次，保证可选池完整
  if (!(authStore.menus || []).length) {
    try {
      await authStore.loadMenus()
    } catch (e) {
      console.error('加载菜单权限失败:', e)
    }
  }
}

function isPicked(path) {
  return draftLinks.value.some(l => l.path === path)
}

function addLink(item) {
  if (isPicked(item.path)) return
  draftLinks.value.push({ label: item.label, path: item.path, icon: item.icon || 'Document' })
}

function removeLink(path) {
  draftLinks.value = draftLinks.value.filter(l => l.path !== path)
}

/** 上移 / 下移：调整入口在首页的展示顺序 */
function moveLink(index, delta) {
  const target = index + delta
  const list = draftLinks.value
  if (target < 0 || target >= list.length) return
  ;[list[index], list[target]] = [list[target], list[index]]
}

/** 自定义入口：菜单里没有的页面，自己填名称与路由 */
function addCustomLink() {
  const label = customLink.value.label.trim()
  let path = customLink.value.path.trim()
  if (!label || !path) {
    ElMessage.warning('请填写入口名称与路由地址')
    return
  }
  if (!path.startsWith('/')) path = '/' + path
  if (isPicked(path)) {
    ElMessage.warning('该路由已在已选列表中')
    return
  }
  draftLinks.value.push({ label, path, icon: customLink.value.icon.trim() || 'Link' })
  customLink.value = { label: '', path: '', icon: '' }
}

/** 保存到后端（全量覆盖），成功后同步一份本地缓存用于离线兜底 */
async function saveQuickLinks() {
  if (draftLinks.value.length === 0) {
    ElMessage.warning('至少保留一个快捷入口，或点「恢复默认」')
    return
  }
  const links = draftLinks.value.map(l => ({ label: l.label, path: l.path, icon: l.icon || '' }))
  saving.value = true
  try {
    await saveMyQuickLinks(links)
  } catch (e) {
    console.error('保存快捷入口失败:', e)
    ElMessage.error('保存失败，请确认后端服务正常后重试')
    return
  } finally {
    saving.value = false
  }
  customLinks.value = draftLinks.value.map(l => ({ ...l }))
  isCustomized.value = true
  writeLocalCache(customLinks.value)
  editorVisible.value = false
  ElMessage.success('快捷入口已保存')
}

/** 恢复默认：删除库里的配置（并清掉本地缓存），首页回到系统内置入口 */
async function resetQuickLinks() {
  saving.value = true
  try {
    await resetMyQuickLinks()
  } catch (e) {
    console.error('恢复默认快捷入口失败:', e)
    ElMessage.error('操作失败，请确认后端服务正常后重试')
    return
  } finally {
    saving.value = false
  }
  clearLocalCache()
  customLinks.value = []
  isCustomized.value = false
  draftLinks.value = DEFAULT_STUDENT_LINKS.map(l => ({ ...l }))
  ElMessage.success('已恢复默认快捷入口')
}

onMounted(async () => {
  try {
    // 我的考试：runtimeStatus 0未开始 1进行中 2已结束 3已撤回；myRecordId 有值 = 已参加
    const res = await getMyExams()
    const exams = res.data || []
    stats.value.pendingCount = exams.filter(
      (e) => e.myRecordId == null && (e.runtimeStatus === 0 || e.runtimeStatus === 1)
    ).length
    stats.value.finishedCount = exams.filter((e) => e.myRecordId != null).length
    ongoingExams.value = exams
      .filter((e) => e.myRecordId == null && (e.runtimeStatus === 0 || e.runtimeStatus === 1))
      .sort((a, b) => (b.runtimeStatus === 1 ? 1 : 0) - (a.runtimeStatus === 1 ? 1 : 0))
      .slice(0, 5)
  } catch (err) {
    console.error('加载考试数据失败:', err)
  }

  try {
    // 错题总数（按学科统计结构兼容：数组求和 / 数字 / 对象 total）
    const wRes = await getMyWrongStats()
    const d = wRes.data
    if (Array.isArray(d)) {
      stats.value.wrongCount = d.reduce((sum, it) => sum + (it.count || it.questionCount || it.num || 0), 0)
    } else if (typeof d === 'number') {
      stats.value.wrongCount = d
    } else if (d && typeof d === 'object') {
      stats.value.wrongCount = d.total || 0
    }
  } catch (err) {
    console.error('加载错题统计失败:', err)
  }

  try {
    const uRes = await getUnreadCount()
    stats.value.unreadCount = uRes.data || 0
  } catch (err) {
    console.error('加载未读消息失败:', err)
  }

  // 快捷入口：以数据库为准（跟账号走），失败时用本地缓存兜底
  loadCustomLinks()
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-right: 16px;
  flex-shrink: 0;
}

.stat-num {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 卡片标题栏右侧：提示文案 + 收起 / 展开按钮 */
.card-header__ops {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.section-tip {
  font-size: 12px;
  font-weight: normal;
  color: #909399;
}

/* ================== 功能总览卡片网格 ================== */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafbfc;
  cursor: pointer;
  transition: all .2s;
}

.feature-item:hover,
.feature-item:focus-visible {
  border-color: var(--el-color-primary);
  background: #fff;
  box-shadow: 0 4px 14px rgba(64, 158, 255, .12);
  transform: translateY(-2px);
}

.feature-item__icon {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.feature-item__body {
  min-width: 0;
}

.feature-item__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.feature-item__desc {
  font-size: 12.5px;
  line-height: 1.65;
  color: #909399;
}

/* 进行中考试提醒 */
.ongoing-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ongoing-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.ongoing-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.ongoing-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ongoing-meta {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.ongoing-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.ongoing-time {
  font-size: 12px;
  color: #909399;
}

.welcome p {
  line-height: 2;
  color: #606266;
}

.greet {
  font-size: 15px;
  margin: 0 0 4px;
}

.intro {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.quick-links-empty {
  font-size: 13px;
  color: #909399;
}

/* 分隔线标题内左文案 + 右「自定义」按钮 */
.divider-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.divider-action {
  font-weight: 400;
}

/* 自定义快捷入口弹窗：顶部提示条 */
.editor-tip {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f0fdf9;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

/* 自定义快捷入口弹窗：左「可选入口」右「已选入口」两栏 */
.quick-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.editor-panel {
  flex: 1 1 260px;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.panel-body {
  max-height: 280px;
  overflow-y: auto;
}

.pool-item,
.picked-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 4px;
  border-bottom: 1px dashed #f0f2f5;
}

.pool-item:last-child,
.picked-item:last-child {
  border-bottom: none;
}

.pool-name,
.picked-name {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picked-ops {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
}

.panel-empty {
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: #c0c4cc;
}

.custom-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.feature-desc {
  margin-top: 4px;
}

.feature-desc :deep(.el-descriptions__label) {
  width: 150px;
  text-align: center;
  font-weight: 600;
}

.feature-desc :deep(.el-descriptions__content) {
  line-height: 1.8;
}

/* ================== 手机端（≤820px）：考试条目改两行 + 描述列表 label 自适应 ================== */
@media (max-width: 820px) {
  /* 卡片标题栏：右侧「提示 + 收起」自动换行；窄屏隐藏提示文案，只留按钮 */
  .card-header {
    flex-wrap: wrap;
    gap: 6px;
  }
  .card-header__ops .section-tip {
    display: none;
  }

  /* 功能总览：手机端单列，说明文字不再被挤压 */
  .feature-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .feature-item {
    padding: 12px;
  }

  /* 考试提醒条目：改纵向两行，第一行标题+状态，第二行时间 + 按钮 */
  .ongoing-item {
    flex-wrap: wrap;
    align-items: stretch;
    gap: 6px;
    padding: 10px 12px;
  }
  /* 左侧信息区：标签 + 标题 + 元信息自动堆叠 */
  .ongoing-info {
    flex: 1 1 auto;
    min-width: 0;
    flex-wrap: wrap;
    gap: 6px 8px;
  }
  .ongoing-name {
    flex: 1 1 100%;   /* 独占整行 */
    white-space: normal; /* 标题允许换行，更可读 */
    line-height: 1.4;
    font-size: 13.5px;
  }
  .ongoing-meta {
    display: none;    /* 手机隐藏试卷名·时长（次要信息） */
  }
  /* 右侧操作区：时间戳 + 按钮，第二行靠右 */
  .ongoing-right {
    flex: 0 0 100%;
    justify-content: space-between;
    gap: 8px;
    margin-top: 4px;
  }
  .ongoing-time {
    font-size: 11.5px;
    color: #94a3b8;
    /* 时间太长时只显示日期部分 */
    max-width: 62%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* 快捷入口：按钮改成每行两个（手机下一行 3 个太挤） */
  .quick-links .el-button {
    flex: 1 1 calc(50% - 5px);
    min-width: 0;
    font-size: 13px;
  }

  /* 功能说明：label 不再占 150px 固定宽 */
  .feature-desc :deep(.el-descriptions__table) {
    table-layout: auto;
    width: 100%;
  }
  .feature-desc :deep(.el-descriptions__label) {
    width: auto;
    min-width: 72px;
    padding: 8px 10px;
    font-size: 13px;
    text-align: left;      /* 手机上居中反而挤 */
  }
  .feature-desc :deep(.el-descriptions__content) {
    padding: 8px 10px;
    font-size: 13px;
  }
}
</style>
