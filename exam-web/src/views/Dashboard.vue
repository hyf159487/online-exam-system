<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #409EFF">
              <el-icon :size="28"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ stats.questionCount }}</div>
              <el-tooltip content="仅统计审核通过的题目，待审核与已驳回不计入" placement="top">
                <div class="stat-label">题目总数</div>
              </el-tooltip>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #67C23A">
              <el-icon :size="28"><Tickets /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ stats.paperCount }}</div>
              <div class="stat-label">试卷总数</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-icon" style="background: #E6A23C">
              <el-icon :size="28"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-num">{{ stats.studentCount }}</div>
              <div class="stat-label">学生人数</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 管理员端不再操作考试场次：「考试次数」改为课程规模；教师端仍看本人考试次数 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <template v-if="role === 2">
              <div class="stat-icon" style="background: #00A8A8">
                <el-icon :size="28"><Reading /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-num">{{ stats.courseCount }}</div>
                <div class="stat-label">课程总数</div>
              </div>
            </template>
            <template v-else>
              <div class="stat-icon" style="background: #F56C6C">
                <el-icon :size="28"><TrendCharts /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-num">{{ stats.examCount }}</div>
                <div class="stat-label">考试次数</div>
              </div>
            </template>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 功能总览：介绍本角色能做什么，点击卡片直达对应功能；右上角可收起 / 展开（状态按账号记忆） -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div class="welcome-header">
          <span>{{ roleTag.label }}端功能总览</span>
          <div class="card-header__ops">
            <span class="section-tip">
              {{ overviewVisible ? '点击卡片可直接进入对应功能' : `已收起 ${overviewList.length} 个功能入口` }}
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
            v-for="item in overviewList"
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

    <el-card style="margin-top: 20px">
      <template #header>
        <div class="welcome-header">
          <span>欢迎使用在线考试与题库管理系统</span>
          <el-tag :type="roleTag.type" effect="dark" size="small">{{ roleTag.label }}端</el-tag>
        </div>
      </template>
      <div class="welcome">
        <p class="greet">{{ greeting }}，<strong>{{ authStore.userInfo?.realName || authStore.userInfo?.username }}</strong>！祝您使用愉快。</p>
        <p class="intro">本系统覆盖「课程与知识点建设 → 学习资料共享 → 录题入库与试题审核 → 手动 / 随机 / 模板 / AI 智能组卷 → 作业发布与批改 → 安排考试与实时监考 → 在线阅卷 → 成绩统计与学情分析」的完整业务闭环，并集成 AI 智能组卷、AI 难度评估、AI 知识点匹配、AI 主观题预评分与 AI 错题讲解能力（未配置密钥时自动降级本地规则引擎）；各角色功能分工明确，可参考下方导航快速上手。</p>
      </div>

      <!-- 快捷入口：教师 / 管理员都可按需自定义（挑选菜单权限内的入口 / 自己填写路由），配置跟账号走 -->
      <el-divider content-position="left">
        <span class="divider-row">
          <span>快捷入口</span>
          <el-button
            v-if="canCustomize"
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
        <el-descriptions-item
          v-for="f in featureList"
          :key="f.name"
          :label="f.name"
          label-width="150px"
        >
          {{ f.desc }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 自定义快捷入口：左侧挑菜单权限内的入口，右侧调整已选项与顺序 -->
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
        <el-input v-model="customLink.label" size="small" placeholder="入口名称，如：操作日志" style="width: 190px" />
        <el-input v-model="customLink.path" size="small" placeholder="路由地址，如：/system/logs" style="width: 230px" />
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
import { getQuestionList } from '@/api/question'
import { getCourseList } from '@/api/course'
import { getMyQuickLinks, saveMyQuickLinks, resetMyQuickLinks } from '@/api/userQuickLink'
import request from '@/utils/request'
import {
  Document, Tickets, User, TrendCharts, Bell, EditPen, DataAnalysis,
  Reading, Files, Checked, Setting, Collection, Timer, Notebook,
  HomeFilled, School, Warning, View, List, UserFilled, Tools, DocumentCopy,
  DataLine, Histogram, Download, Promotion, Postcard, Key, InfoFilled,
  Clock, SwitchButton, Upload, Search, Link, Expand, FolderOpened,
  ArrowUp, ArrowDown
} from '@element-plus/icons-vue'

const authStore = useAuthStore()

/** 当前角色（1教师 2管理员 0学生） */
const role = computed(() => authStore.userInfo?.role)

/** 是否教师 / 管理员：这两类角色都能自定义首页快捷入口（学生端首页无此区域） */
const canCustomize = computed(() => role.value >= 1)

const roleTag = computed(() => ({
  0: { label: '学生', type: 'info' },
  1: { label: '教师', type: 'primary' },
  2: { label: '管理员', type: 'warning' }
}[role.value] || { label: '未知', type: 'info' }))

/** 按时段问候 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

/* ==================== 快捷入口（教师 / 管理员可自定义） ==================== */

/** 图标名 → 组件 映射：快捷入口只存图标名，便于 JSON 序列化后落本地存储 */
const iconMap = {
  HomeFilled, Document, Notebook, Timer, DataAnalysis, User, School, Reading,
  Setting, Checked, TrendCharts, Bell, EditPen, Warning, Files, View, List,
  UserFilled, Collection, Tools, DocumentCopy, DataLine, Histogram, Download,
  Promotion, Postcard, Key, InfoFilled, Clock, SwitchButton, Upload, Tickets, Link, Expand,
  FolderOpened, ArrowUp, ArrowDown
}

function getIcon(name) {
  return iconMap[name] || Link
}

/** 默认快捷入口（仅图标名 + 名称 + 路由，存起来只有字符串） */
const DEFAULT_ADMIN_LINKS = [
  { label: '待审核试题', path: '/audit/pending', icon: 'Checked' },
  { label: '审核历史', path: '/audit/history', icon: 'Files' },
  { label: '账号管理', path: '/students', icon: 'User' },
  { label: '班级管理', path: '/classes', icon: 'Collection' },
  { label: '课程管理', path: '/courses', icon: 'Reading' },
  { label: '字典管理', path: '/system/dictionaries', icon: 'Setting' }
]

const DEFAULT_TEACHER_LINKS = [
  { label: '题库管理', path: '/questions', icon: 'Document' },
  { label: '试卷管理', path: '/papers', icon: 'Tickets' },
  { label: '考试管理', path: '/exams', icon: 'Timer' },
  { label: '成绩管理', path: '/grades', icon: 'EditPen' },
  { label: '教学统计', path: '/stats/class', icon: 'DataAnalysis' },
  { label: '发送通知', path: '/notifications/send', icon: 'Bell' }
]

/**
 * 只对管理员生效：已下线模块（与 Layout.vue 侧边栏保持一致，不进可选入口池）
 * 考试安排 / 发布考试 / 监控考生 / 成绩批阅都是教师业务，管理员端不再提供这两个模块
 */
const ADMIN_HIDDEN_MENUS = ['作业管理', '发布作业', '自主练习管理', '试卷管理', '考试管理', '成绩管理']

/** 管理员端已下线模块的路由前缀：历史自定义入口里可能还存着，渲染前统一过滤 */
const ADMIN_HIDDEN_PATHS = ['/exams', '/grades']
function isHiddenForAdmin(path) {
  return role.value === 2 && ADMIN_HIDDEN_PATHS.some(p => path === p || path.startsWith(p + '/'))
}

/** 当前角色的系统默认入口（未自定义过时展示） */
function defaultLinks() {
  return role.value === 2 ? DEFAULT_ADMIN_LINKS : DEFAULT_TEACHER_LINKS
}

/** 自定义入口的本地缓存 key：按账号隔离，换账号登录各用各的（数据库才是权威来源） */
const QUICK_LINK_KEY_PREFIX = 'exam_user_quick_links_'
function quickLinkStorageKey() {
  return QUICK_LINK_KEY_PREFIX + (authStore.userInfo?.userId ?? 'default')
}

/** 后端记录 → 首页渲染用的 { label, path, icon } */
function toLinkItem(row) {
  return { label: row?.label, path: row?.routePath, icon: row?.icon || 'Link' }
}

/** 本地缓存兜底：后端临时不可用时首页仍能显示上次配置的入口 */
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

const customLinks = ref([])      // 账号保存过的入口列表（教师 / 管理员）
const isCustomized = ref(false)  // 是否自定义过：false 时展示本角色的默认入口

/** 实际渲染的快捷入口：自定义过用自己的，其余走本角色默认；学生端首页没有这块区域 */
const quickLinks = computed(() => {
  if (!canCustomize.value) return []
  const list = isCustomized.value ? customLinks.value : defaultLinks()
  // 已下线模块（管理员端的试卷/考试/成绩）不再展示，哪怕历史配置里存过
  return list.filter(l => !isHiddenForAdmin(l.path))
})

/* -------------------- 编辑弹窗 -------------------- */

const editorVisible = ref(false)
const draftLinks = ref([])                  // 弹窗里编辑中的副本，点保存才生效
const poolKeyword = ref('')
const customLink = ref({ label: '', path: '', icon: '' })
const saving = ref(false)                   // 保存中：按钮转圈，防重复提交

/** 可选入口池：当前账号菜单权限内的所有入口（父 + 子菜单），管理员端过滤已下线模块 */
const menuPool = computed(() => {
  const pool = []
  const push = (label, path, icon) => {
    if (!label || !path || !String(path).startsWith('/')) return
    if (pool.some(p => p.path === path)) return
    pool.push({ label, path, icon: icon || 'Document' })
  }
  ;(authStore.menus || []).forEach(menu => {
    if (role.value === 2 && ADMIN_HIDDEN_MENUS.includes(menu.menuName)) return
    push(menu.menuName, menu.routePath, menu.icon)
    ;(menu.children || []).forEach(child => {
      if (role.value === 2 && ADMIN_HIDDEN_MENUS.includes(child.menuName)) return
      push(child.menuName, child.routePath, child.icon)
    })
  })
  // 个人中心是前端固定菜单（不落库），单独补进池子
  push('个人信息', '/profile/info', 'Postcard')
  push('关于系统', '/profile/about', 'InfoFilled')
  return pool
})

/** 按关键词过滤可选入口（名称或路由命中） */
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
  if (!canCustomize.value) return
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

function openQuickLinkEditor() {
  draftLinks.value = quickLinks.value.map(l => ({ ...l }))
  poolKeyword.value = ''
  customLink.value = { label: '', path: '', icon: '' }
  editorVisible.value = true
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
  draftLinks.value = defaultLinks().map(l => ({ ...l }))
  ElMessage.success('已恢复默认快捷入口')
}

/* ==================== 功能总览（管理员 / 教师） ==================== */

/** 管理员端功能总览：与左侧菜单对应，点击卡片直达对应功能 */
const ADMIN_OVERVIEW = [
  { title: '试题审核', icon: Checked, color: '#F56C6C', path: '/audit/pending',
    desc: '审核教师提交的试题，通过后入公共题库供全体组卷使用；驳回后教师可修改重新提交，全过程留痕可查。' },
  { title: '题库管理', icon: Document, color: '#409EFF', path: '/questions',
    desc: '全校题目视角，可按课程 / 知识点 / 题型 / 审核状态检索与维护，并在高频错题库汇总复用错题。' },
  { title: '教学统计分析', icon: DataAnalysis, color: '#67C23A', path: '/stats/class',
    desc: '全校维度的班级学情、试题质量分析与学情报表导出，支持导出 Excel 用于教学复盘。' },
  { title: '账号管理', icon: User, color: '#9B59B6', path: '/account/students',
    desc: '维护管理员 / 教师 / 学生账号，支持新建、启用禁用、批量导入、调班与移出班级。' },
  { title: '班级管理', icon: School, color: '#FF9F43', path: '/classes',
    desc: '维护班级基础信息与班级学生名单；删除班级前会校验班内学生已清空，防止无效关联。' },
  { title: '课程管理', icon: Reading, color: '#409EFF', path: '/courses',
    desc: '维护课程基础信息，课程下的知识点体系与学习资料也在这里统一管理。' },
  { title: '知识点管理', icon: Collection, color: '#00B4D8', path: '/knowledge-points',
    desc: '维护各课程知识点树，教师录题时按课程关联，学生学情与试题质量分析也依赖这套知识点。' },
  { title: '学习资料管理', icon: FolderOpened, color: '#9B59B6', path: '/study-resources',
    desc: '上传 PDF / Word / PPT 等课程资料，供学生在课程学习 ▸ 资料下载中查看与下载。' },
  { title: '消息通知', icon: Promotion, color: '#F7BA2A', path: '/notifications/send',
    desc: '向指定班级或全体学生发布考试通知与系统公告，并查看历史发送记录与已读确认情况。' },
  { title: '系统管理', icon: Setting, color: '#909399', path: '/system/dictionaries',
    desc: '字典、角色权限、操作日志与系统参数等全局配置，保障系统规范运行。' }
]

/** 教师端功能总览：与左侧菜单对应，点击卡片直达对应功能 */
const TEACHER_OVERVIEW = [
  { title: '题库管理', icon: Document, color: '#409EFF', path: '/questions',
    desc: '维护个人题目：单题新增（AI 自动评估难度、可批量匹配知识点）或 Word/Excel/PDF/JSON 批量导入，支持题干关键词搜索。' },
  { title: '高频错题库', icon: Warning, color: '#F56C6C', path: '/questions/hot-wrong',
    desc: '汇总全体学生的错题并统计错误率，便于针对性讲评与组卷。' },
  { title: '试卷管理', icon: Notebook, color: '#67C23A', path: '/papers',
    desc: '手动 / 随机 / 模板 / AI 智能组卷四种方式创建试卷，可选择共享给其他教师，支持发布、撤回与归档。' },
  { title: '组卷模板', icon: DocumentCopy, color: '#9B59B6', path: '/paper-templates',
    desc: '保存常用组卷参数为模板，下次一键复用，免去重复配置。' },
  { title: '考试管理', icon: Timer, color: '#00A8A8', path: '/exams',
    desc: '基于已发布试卷安排考试（限定时间窗口），查看考试列表与状态，支持撤回与重新发布。' },
  { title: '考试监控', icon: View, color: '#E6A23C', path: '/exams/monitor',
    desc: '实时查看在考学生的答题进度、切屏次数与剩余时间，异常情况下可强制交卷。' },
  { title: '成绩管理', icon: EditPen, color: '#FF9F43', path: '/grades',
    desc: '客观题交卷自动判分，主观题在线阅卷（AI 预评分辅助）；支持成绩复核与按试卷查看成绩明细。' },
  { title: '主观题阅卷', icon: Files, color: '#F7BA2A', path: '/grades/review',
    desc: '待批阅主观题列表，按题给分并写评语，AI 预评分给出建议得分与评分依据作为参考。' },
  { title: '作业管理', icon: Tickets, color: '#409EFF', path: '/practice/papers',
    desc: '发布课后作业供学生完成、批改其中的主观题，并跟踪学生作答记录与答题情况。' },
  { title: '教学统计分析', icon: DataAnalysis, color: '#67C23A', path: '/stats/class',
    desc: '班级学情分析、试题质量分析与学情报表导出，支持导出 Excel 用于教学复盘。' },
  { title: '消息通知', icon: Promotion, color: '#F56C6C', path: '/notifications/send',
    desc: '面向班级或学生发送考试通知与系统公告，可查看历史发送记录与已读确认情况。' },
  { title: '我的课程', icon: Reading, color: '#00A8A8', path: '/my-courses',
    desc: '查看本人任教课程与学生名单，审核学生加入课程的申请，并管理课程学习资料。' }
]

/** 当前角色的功能总览卡片（管理员端 / 教师端各有侧重） */
const overviewList = computed(() => (role.value === 2 ? ADMIN_OVERVIEW : TEACHER_OVERVIEW))

/**
 * 功能总览的收起 / 展开：默认展开，收起后只留卡片标题栏。
 * 选择记在本地（按账号隔离），下次进首页保持上次状态。
 */
const OVERVIEW_KEY_PREFIX = 'exam_dashboard_overview_visible_'

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

/** 按角色区分的功能说明 */
const featureList = computed(() => {
  if (role.value === 2) {
    return [
      { name: '试题审核', desc: '入口在「题库管理 ▸ 待审核试题 / 审核历史记录」：审核教师提交的题目，通过后入公共题库供全体组卷使用；驳回后教师可修改重新提交，全过程留痕可查。' },
      { name: '题库管理', desc: '管理员拥有全校题目视角：检索与维护全部教师创建的题目，审核通过后入公共题库，并在高频错题库汇总复用错题。试卷组卷、考试安排与发布、成绩批阅均属教师业务，管理员端不再提供这些入口。' },
      { name: '知识点 / 学习资料', desc: '在「课程管理」下维护各课程知识点体系（知识点管理）与学习资料（学习资料管理，PDF / Word / PPT 等），供教师录题关联、学生在课程学习 ▸ 资料下载中查看下载。' },
      { name: '账号管理', desc: '维护管理员 / 教师 / 学生账号，支持新建、启用/禁用、批量导入、调班与移出班级。' },
      { name: '班级 / 课程管理', desc: '维护班级与课程基础信息；删除班级前会校验班级内学生已清空，防止产生无效关联。' },
      { name: '消息通知', desc: '向指定班级或全体学生发布考试通知与系统公告，并查看历史发送记录与已读确认情况。' },
      { name: '系统管理', desc: '字典、角色权限、操作日志、系统参数等全局配置，保障系统规范运行。' },
      { name: '教学统计分析', desc: '全校维度的成绩分布、班级学情、题目质量分析与报表导出。' },
      { name: 'AI 辅助能力', desc: 'AI 智能组卷可按目标总分与难度倾向自动生成方案；录题时 AI 评估难度、批量匹配知识点；阅卷时 AI 预评分给出建议得分与评分依据；学生端 AI 错题讲解支持多轮追问。未配置密钥时统一降级为本地规则引擎。' }
    ]
  }
  return [
    { name: '题库管理', desc: '维护个人题目：单题新增（AI 自动评估难度、可批量匹配知识点）或 Word/Excel/PDF/JSON 批量导入；支持题干关键词搜索，并在「高频错题库」汇总全体学生错题以便复用于组卷。' },
    { name: '知识点 / 学习资料', desc: '知识点由管理员统一维护，录题时按课程只读关联；在「课程管理 ▸ 学习资料管理」上传 PDF / Word / PPT 等资料供学生下载。' },
    { name: '试卷管理', desc: '批量新增试卷（可选择共享给其他教师，被共享教师只读）；手动 / 随机 / 模板 / AI 智能组卷四种组卷方式，并可保存「组卷模板」复用；发布、撤回、归档全生命周期管理。' },
    { name: '考试管理', desc: '基于已发布试卷安排考试（限定时间窗口）；在「考试监控」中实时查看学生答题进度、切屏次数与剩余时间，必要时强制交卷。' },
    { name: '成绩管理', desc: '客观题交卷自动判分，主观题在「主观题阅卷」中在线评分（AI 预评分辅助）；支持成绩复核与按试卷查看成绩明细。' },
    { name: '教学统计分析', desc: '班级学情分析、试题质量分析与学情报表导出，支持导出 Excel 用于教学复盘。' },
    { name: '消息通知', desc: '面向班级或学生发送考试通知与系统公告，可查看历史发送记录与已读确认情况。' },
    { name: '作业管理', desc: '发布作业供学生课后完成、批改其中的主观题（批改作业），并跟踪学生作答记录与答题情况（作业记录）。' },
    { name: '我的课程 / 课程申请', desc: '查看本人任教课程与学生名单，审核学生加入课程的申请，并管理课程学习资料。' }
  ]
})

const stats = ref({
  questionCount: 0,
  paperCount: 0,
  studentCount: 0,
  examCount: 0,
  courseCount: 0
})

onMounted(async () => {
  // 管理员：恢复上次自定义的快捷入口
  loadCustomLinks()
  try {
    // 查题目总数：auditStatus=1 只统计审核通过的（待审核 0 / 已驳回 2 不计入题库用量）
    const qRes = await getQuestionList({ pageNum: 1, pageSize: 1, auditStatus: 1 })
    stats.value.questionCount = qRes.data?.total || 0

    // 查试卷总数
    const pRes = await request.get('/api/exam-paper/recent', { params: { limit: 100 } })
    stats.value.paperCount = pRes.data?.length || 0

    // 查学生总数（role=0 是学生）
    const uRes = await request.get('/api/sys-user/count', { params: { role: 0 } })
    stats.value.studentCount = uRes.data || 0

    // 管理员：统计全校课程数（管理员端不再展示考试场次数据）
    // 教师：统计本人考试次数
    if (role.value === 2) {
      const cRes = await getCourseList()
      stats.value.courseCount = (cRes.data || []).length
    } else {
      const eRes = await request.get('/api/exam/count')
      stats.value.examCount = eRes.data || 0
    }
  } catch (err) {
    console.error('加载统计数据失败:', err)
  }
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

.welcome p {
  line-height: 2;
  color: #606266;
}

.welcome-header {
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

/* 分隔线标题内左文案 + 右「自定义」按钮 */
.divider-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.divider-action {
  font-weight: 400;
}

.quick-links-empty {
  font-size: 13px;
  color: #909399;
}

/* 自定义快捷入口弹窗：顶部提示条 */
.editor-tip {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f4f8ff;
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

/* 描述列表 label 居中、内容行高舒适 */
.feature-desc :deep(.el-descriptions__label) {
  width: 150px;
  text-align: center;
  font-weight: 600;
}

.feature-desc :deep(.el-descriptions__content) {
  line-height: 1.8;
}

/* ================== 窄屏（≤1100px）：功能总览改两列；≤820px 单列 ================== */
@media (max-width: 1100px) {
  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  /* 卡片标题栏：右侧「提示 + 收起」自动换行；窄屏隐藏提示文案，只留按钮 */
  .welcome-header {
    flex-wrap: wrap;
    gap: 6px;
  }
  .card-header__ops .section-tip {
    display: none;
  }
  .feature-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .feature-item {
    padding: 12px;
  }
}
</style>
