import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
// 管理员端已下线的模块（路由 meta.group 标记），直接输入 URL 时拦截并跳回首页
// paper：试卷创建与组卷归教师端
// exam / grade：安排考试、发布考试、监控考生、成绩批阅均为教师业务，管理员端不再提供
const ADMIN_HIDDEN_GROUPS = ['paper', 'exam', 'grade']

// 命中下线模块时的提示语：写明归属，避免管理员误以为是系统故障
const ADMIN_HIDDEN_TIPS = {
  paper: '试卷管理已归教师端使用，管理员端不再提供',
  exam: '考试由任课教师安排与发布，管理员端不再提供考试管理',
  grade: '成绩由任课教师批阅与复核，管理员端不再提供成绩管理'
}

// 占位页：所有新增但尚未实现具体业务的菜单项统一指向它
const Placeholder = () => import('@/views/PlaceholderPage.vue')

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      // ===== 首页 =====
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },

      // ===== 题库管理 =====
      {
        path: 'questions',
        name: 'QuestionList',
        component: () => import('@/views/QuestionList.vue'),
        meta: { title: '我的试题', icon: 'Document', group: 'question' }
      },
      {
        path: 'questions/hot-wrong',
        name: 'QuestionHotWrong',
        component: () => import('@/views/HotWrongList.vue'),
        meta: { title: '高频错题库', group: 'question', description: '基于全体学生错题统计的高频错题汇总，支持教师复用错题进行组卷。' }
      },

      // ===== 试卷管理 =====
      {
        path: 'papers',
        name: 'PaperList',
        component: () => import('@/views/PaperList.vue'),
        meta: { title: '我的试卷', icon: 'Notebook', group: 'paper' }
      },
      {
        path: 'papers/:id/compose',
        name: 'PaperCompose',
        component: () => import('@/views/PaperCompose.vue'),
        // group 用于管理员端「试卷管理」模块下线后的路由拦截（见 beforeEach）
        meta: { title: '组卷', hidden: true, group: 'paper' }
      },
      {
        path: 'paper-templates',
        name: 'PaperTemplates',
        component: () => import('@/views/PaperTemplateList.vue'),
        meta: { title: '组卷模板', group: 'paper', description: '保存常用组卷参数模板，一键复用快速组卷。' }
      },

      // ===== 考试管理 =====
      {
        path: 'exams',
        name: 'ExamList',
        component: () => import('@/views/ExamList.vue'),
        meta: { title: '我的考试', icon: 'Timer', group: 'exam' }
      },
      {
        path: 'exams/monitor',
        name: 'ExamMonitor',
        component: () => import('@/views/ExamMonitorList.vue'),
        meta: { title: '考试监控', group: 'exam', description: '查看正在考试的学生状态、答题进度，异常情况下可强制交卷。' }
      },

      // ===== 成绩管理 =====
      {
        path: 'grades',
        name: 'GradeList',
        component: () => import('@/views/GradeList.vue'),
        meta: { title: '考试成绩', icon: 'DataAnalysis', group: 'grade' }
      },
      {
        path: 'grades/:recordId/review',
        name: 'GradeReview',
        component: () => import('@/views/ReviewDetail.vue'),
        meta: { title: '主观题阅卷', hidden: true }
      },
      {
        path: 'grades/review',
        name: 'GradeReviewList',
        component: () => import('@/views/ExamReviewList.vue'),
        meta: { title: '主观题阅卷', group: 'grade', description: '主观题待批阅列表，支持教师在线阅卷评分。' }
      },

      // ===== 教学统计分析（教师专属）=====
      {
        path: 'stats/class',
        name: 'StatsClass',
        component: () => import('@/views/StatsClass.vue'),
        meta: { title: '班级学情分析', group: 'stats', description: '按班级维度分析学生成绩分布、知识点掌握情况。' }
      },
      {
        path: 'stats/question',
        name: 'StatsQuestion',
        component: () => import('@/views/StatsQuestion.vue'),
        meta: { title: '试题质量分析', group: 'stats', description: '试题错误率、难度系数分析，辅助优化题库质量。' }
      },
      {
        path: 'stats/export',
        name: 'StatsExport',
        component: () => import('@/views/StatsExport.vue'),
        meta: { title: '学情报表导出', group: 'stats', description: '导出班级学情、学生成绩等报表为 Excel。' }
      },

      // ===== 消息通知（教师）=====
      {
        path: 'notifications/send',
        name: 'NotificationSend',
        component: () => import('@/views/NotificationSend.vue'),
        meta: { title: '发布通知', group: 'notification', description: '向学生推送考试提醒、通知消息。' }
      },
      {
        path: 'notifications/history',
        name: 'NotificationHistory',
        component: () => import('@/views/NotificationHistory.vue'),
        meta: { title: '通知记录', group: 'notification', description: '查看已发送通知历史及送达状态。' }
      },

      // ===== 作业管理（教师，原「自主练习管理」）=====
      {
        path: 'practice/papers',
        name: 'PracticePapers',
        component: () => import('@/views/PracticePaperList.vue'),
        meta: { title: '发布作业', group: 'practiceMgmt', description: '发布与管理供学生课后完成的作业。' }
      },
      {
        path: 'practice/review',
        name: 'PracticeReview',
        component: () => import('@/views/PracticeReviewList.vue'),
        meta: { title: '批改作业', group: 'practiceMgmt', description: '批改学生作业中的主观题，按题给分并写评语。' }
      },
      {
        path: 'practice/records',
        name: 'PracticeRecords',
        component: () => import('@/views/PracticeRecordList.vue'),
        meta: { title: '作业记录', group: 'practiceMgmt', description: '查看学生作业提交记录与答题情况。' }
      },

      // ===== 系统管理（管理员专属）=====
      {
        path: 'system/logs',
        name: 'SystemLogs',
        component: () => import('@/views/SystemLogs.vue'),
        meta: { title: '操作日志', group: 'system', description: '用户操作留痕：增删试题、发布考试、账号操作等审计记录。' }
      },
      {
        path: 'system/roles',
        name: 'SystemRoles',
        component: () => import('@/views/SystemRoles.vue'),
        meta: { title: '角色与权限配置', group: 'system', description: 'RBAC 角色权限配置，管理员/教师/学生权限分配。' }
      },
      {
        path: 'system/dictionaries',
        name: 'SystemDictionaries',
        component: () => import('@/views/DictionaryManage.vue'),
        meta: { title: '字典管理', group: 'system', description: '题型、难度、状态等基础字典维护。' }
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/SystemConfig.vue'),
        meta: { title: '系统参数配置', group: 'system', description: '考试超时时间、token 有效期、防作弊开关等系统参数。' }
      },

      // ===== 试题审核（管理员专属；侧边栏菜单已并入「题库管理」，路由地址保持不变）=====
      {
        path: 'audit/pending',
        name: 'AuditPending',
        component: () => import('@/views/QuestionAudit.vue'),
        meta: { title: '待审核试题', group: 'audit', description: '审核教师提交的试题，通过/驳回操作。' }
      },
      {
        path: 'audit/history',
        name: 'AuditHistory',
        component: () => import('@/views/QuestionAudit.vue'),
        meta: { title: '审核历史记录', group: 'audit', description: '试题审核历史记录查询。' }
      },

      // ===== 账号管理（管理员）=====
      {
        path: 'account/admins',
        name: 'AdminAccountList',
        component: () => import('@/views/StudentList.vue'),
        meta: { title: '管理员账号' },
        props: { defaultRole: 2 }
      },
      {
        path: 'account/teachers',
        name: 'TeacherAccountList',
        component: () => import('@/views/StudentList.vue'),
        meta: { title: '教师账号' },
        props: { defaultRole: 1 }
      },
      {
        path: 'account/students',
        name: 'StudentAccountList',
        component: () => import('@/views/StudentList.vue'),
        meta: { title: '学生账号' },
        props: { defaultRole: 0 }
      },
      {
        path: 'account/import',
        name: 'UserBatchImport',
        component: () => import('@/views/UserBatchImport.vue'),
        meta: { title: '账号批量导入' }
      },
      // 兼容老路径 /students → 重定向到 /account/students
      { path: 'students', redirect: '/account/students' },
      {
        path: 'classes',
        name: 'ClassList',
        component: () => import('@/views/ClassList.vue'),
        meta: { title: '班级管理', icon: 'School' }
      },
      {
        path: 'classes/:classId/students',
        name: 'ClassStudentList',
        component: () => import('@/views/ClassStudentList.vue'),
        meta: { title: '班级学生', hidden: true }
      },
      {
        path: 'courses',
        name: 'CourseList',
        component: () => import('@/views/CourseList.vue'),
        meta: { title: '课程管理', icon: 'Reading' }
      },
      // 课程信息页（管理端）：课程列表点击课程卡片进入，可查看并修改课程信息（含编码与任课教师）
      {
        path: 'courses/:courseId',
        name: 'CourseInfo',
        component: () => import('@/views/CourseInfo.vue'),
        meta: { title: '课程信息', hidden: true, description: '查看课程基础信息、课程介绍与建设数据，并可编辑课程信息。' }
      },
      {
        path: 'knowledge-points',
        name: 'KnowledgePointList',
        component: () => import('@/views/knowledgePoint/KnowledgePointList.vue'),
        meta: { title: '知识点管理', icon: 'Collection', description: '维护课程知识点，录入试题时按课程绑定知识点，供学情分析使用。' }
      },
      {
        path: 'study-resources',
        name: 'StudyResourceList',
        component: () => import('@/views/studyResource/StudyResourceList.vue'),
        meta: { title: '学习资料管理', icon: 'FolderOpened', description: '维护课程学习资料，上传 PDF/Word/PPT 等，学生在自主练习页查看下载。' }
      },
      {
        path: 'my-courses',
        name: 'TeacherMyCourse',
        component: () => import('@/views/TeacherMyCourse.vue'),
        meta: { title: '我的课程', group: 'courseMgmt', description: '只展示当前教师任教的课程，点击课程卡片可查看并修改课程信息，也可查看学生名单、跳转资料管理与申请审核。' }
      },
      // 课程信息页（教师端）：我的课程点击课程卡片进入，可查看并修改本人任教课程的介绍信息
      {
        path: 'my-courses/:courseId',
        name: 'TeacherCourseInfo',
        component: () => import('@/views/CourseInfo.vue'),
        meta: { title: '课程信息', group: 'courseMgmt', hidden: true, description: '查看并修改本人任教课程的基础信息与课程介绍（课程编码由管理员维护）。' }
      },
      {
        path: 'course/applies',
        name: 'CourseApplyAudit',
        component: () => import('@/views/CourseApplyAudit.vue'),
        meta: { title: '课程申请审核', group: 'courseMgmt', description: '审核学生加入课程的申请，同意/驳回并通知学生。' }
      },

      // ===== 个人中心（管理员 / 教师通用；侧边栏「个人中心」一级模块 + 右上角用户下拉双重入口）=====
      // 说明：「修改密码」已合并进 ProfileInfo.vue 底部，不再单独开路由
      {
        path: 'profile/info',
        name: 'ProfileInfo',
        component: () => import('@/views/ProfileInfo.vue'),
        meta: { title: '个人信息', group: 'profile', description: '查看与修改个人基本信息、修改登录密码。' }
      },
      {
        path: 'profile/about',
        name: 'AboutSystem',
        component: () => import('@/views/AboutSystem.vue'),
        meta: { title: '关于系统', group: 'profile', description: '系统版本信息与说明。' }
      }
    ]
  },
  {
    path: '/student',
    component: () => import('@/views/StudentLayout.vue'),
    // 学生端默认落地页：首页（功能总览 + 考试提醒 + 快捷入口）
    redirect: '/student/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('@/views/StudentDashboard.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'exams',
        name: 'StudentExamList',
        component: () => import('@/views/StudentExamList.vue'),
        meta: { title: '我的考试' }
      },
      {
        path: 'exams/:id/do',
        name: 'StudentExamDo',
        component: () => import('@/views/StudentExamDo.vue'),
        meta: { title: '开始考试', hidden: true }
      },
      {
        path: 'grades',
        name: 'StudentGrade',
        component: () => import('@/views/StudentGrade.vue'),
        meta: { title: '我的成绩' }
      },
      // ===== 自主练习（分组）：练习卷 / 自由刷题 =====
      // 老路径 /student/practice 保留重定向：数据库菜单还没执行拆分脚本时点击菜单不会白屏
      {
        path: 'practice',
        redirect: '/student/practice/papers'
      },
      {
        path: 'practice/papers',
        name: 'StudentPracticePaper',
        component: () => import('@/views/StudentPracticePaper.vue'),
        meta: { title: '作业列表' }
      },
      {
        path: 'practice/free',
        name: 'StudentPracticeFree',
        component: () => import('@/views/StudentPracticeFree.vue'),
        meta: { title: '自由刷题' }
      },
      {
        path: 'course/join',
        name: 'StudentCourseJoin',
        component: () => import('@/views/StudentCourseJoin.vue'),
        meta: { title: '加入课程', group: 'courseStudy', description: '浏览开放课程并申请加入，由任课教师审核。' }
      },
      {
        path: 'course/resources',
        name: 'StudentCourseResources',
        component: () => import('@/views/StudentCourseResources.vue'),
        meta: { title: '课程资料', group: 'courseStudy', description: '查看已加入课程的学习资料，支持预览、下载。' }
      },
      // 「加入课程」点击课程卡片进入的课程详情页：课程介绍 / 资料 / 公告 / 作业 / 考试
      // group 与「加入课程」一致，保证从侧边栏「加入课程」进来时菜单保持高亮
      {
        path: 'course/detail/:courseId',
        name: 'StudentCourseDetail',
        component: () => import('@/views/StudentCourseDetail.vue'),
        meta: { title: '课程详情', group: 'courseStudy', hidden: true, description: '课程介绍、课程资料、课程公告、课程作业与课程考试。' }
      },
      {
        path: 'wrong-questions',
        name: 'StudentWrongList',
        component: () => import('@/views/StudentWrongList.vue'),
        meta: { title: '我的错题' }
      },
      // ===== 我的收藏：与「我的错题」并列的一级菜单（自主练习中手动标记的重点题）=====
      {
        path: 'favorites',
        name: 'StudentFavoriteList',
        component: () => import('@/views/StudentFavoriteList.vue'),
        meta: { title: '我的收藏' }
      },
      // ===== 通知公告（侧边栏顶部固定入口，与右上角铃铛同源数据）=====
      {
        path: 'messages',
        name: 'StudentMessageList',
        component: () => import('@/views/StudentMessageList.vue'),
        meta: { title: '通知公告' }
      },
      // 兼容老路径：数据库菜单 route_path 可能还是 /student/profile/notifications（迁移 SQL 未全量执行时）
      // 重定向到 /student/messages，保证点击菜单不会白屏
      {
        path: 'profile/notifications',
        redirect: '/student/messages'
      },
      // ===== 个人中心 =====
      // 说明：「修改密码」已合并进 StudentProfileInfo.vue 底部，不再单独开路由
      {
        path: 'profile/info',
        name: 'StudentProfileInfo',
        component: () => import('@/views/StudentProfileInfo.vue'),
        meta: { title: '个人信息', group: 'profile', description: '查看与修改个人基本信息、修改登录密码。' }
      },
      {
        path: 'profile/my-courses',
        name: 'StudentMyCourse',
        component: () => import('@/views/StudentMyCourse.vue'),
        meta: { title: '我的课程', group: 'profile', description: '查看我已加入的课程信息，可进入课程资料与课程作业。' }
      },
      {
        path: 'profile/about',
        name: 'StudentAbout',
        component: () => import('@/views/StudentAbout.vue'),
        meta: { title: '关于系统', group: 'profile', description: '系统版本信息与说明。' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局路由守卫：没登录就跳到登录页；按角色分流首页
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const token = authStore.token
  const role = authStore.userInfo?.role

  if (to.path === '/login') {
    // 已登录就别再进登录页了：学生进学生端首页，其他进教师端
    token ? next(role === 0 ? '/student/dashboard' : '/') : next()
  } else {
    if (!token) {
      next('/login')
      return
    }
    // 学生不能进教师端首页，重定向到学生端首页
    if (role === 0 && (to.path === '/' || to.path === '/dashboard')) {
      next('/student/dashboard')
      return
    }
    // 教师 / 管理员不能进学生作答端（/student、/student/exams、/student/grades 等）
    // 注意：学生管理页路径是 /students（没有斜杠），必须排除，否则会被误杀
    const isStudentApp = to.path === '/student' || to.path.startsWith('/student/')
    if (role !== 0 && isStudentApp) {
      next('/')
      return
    }
    // 管理员端已下线的模块（试卷/考试/成绩归教师端）：URL 直达时提示并跳回首页
    if (role === 2 && ADMIN_HIDDEN_GROUPS.includes(to.meta?.group)) {
      ElMessage.warning(ADMIN_HIDDEN_TIPS[to.meta.group] || '该模块已归教师端使用')
      next('/')
      return
    }
    next()
  }
})

// 动态设置浏览器标签页标题：按角色显示"学生端 / 教师端 / 管理员端" + 当前页面名
function roleSuffix(role) {
  if (role === 0) return '学生端'
  if (role === 2) return '管理员端'
  return '教师端'
}

router.afterEach((to) => {
  const authStore = useAuthStore()
  const role = authStore.userInfo?.role
  const base = '在线考试与题库管理系统'
  const pageTitle = to.meta?.title
  if (to.path === '/login' || !authStore.token) {
    document.title = pageTitle ? `${pageTitle} - ${base}` : `${base} - 登录`
    return
  }
  const suffix = roleSuffix(role)
  document.title = pageTitle ? `${base} - ${suffix} - ${pageTitle}` : `${base} - ${suffix}`
})

export default router
