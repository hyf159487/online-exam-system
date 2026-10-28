<template>
  <div class="course-detail-page">
    <!-- 顶部：返回「加入课程」（返回后仍是加入课程页面，浏览状态由页面自身恢复） -->
    <div class="page-header">
      <el-button link type="primary" class="back-btn" @click="goBack">← 返回加入课程</el-button>
      <span class="page-sub">课程详情 · 申请加入并通过教师审核后，可查看课程资料、公告、作业与考试</span>
    </div>

    <!-- 课程基础信息区：封面 + 课程概况 + 状态与操作 -->
    <el-card v-if="course" v-loading="loading" shadow="never" class="base-card">
      <div class="base">
        <div class="base__cover">
          <img v-if="course.cover" :src="course.cover" :alt="course.courseName" />
          <span v-else class="base__cover-text">{{ (course.courseName || '课').slice(0, 1) }}</span>
        </div>

        <div class="base__info">
          <div class="base__title-row">
            <h2 class="base__title">{{ course.courseName }}</h2>
            <el-tag :type="statusTagType" effect="light">{{ statusText }}</el-tag>
            <el-tag v-if="course.courseType" type="info" effect="plain">{{ course.courseType }}</el-tag>
          </div>

          <p class="base__desc">{{ course.description || '该课程暂无简介' }}</p>

          <div class="base__meta">
            <div class="meta-item">
              <span class="meta-label">授课教师</span>
              <span class="meta-value">{{ course.teacherNames || '暂未安排' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">课程编码</span>
              <span class="meta-value">{{ course.courseCode || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">开课时间</span>
              <span class="meta-value">{{ formatTime(course.startTime) || '长期开放' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">报名截止</span>
              <span class="meta-value">{{ formatTime(course.enrollDeadline) || '长期开放' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">已加入人数</span>
              <span class="meta-value">{{ course.studentCount || 0 }} 人</span>
            </div>
          </div>

          <!-- 教师信息卡片：悬停查看教师联系方式 -->
          <div v-if="teachers.length" class="teacher-chips">
            <span class="teacher-chips__label">任课教师</span>
            <el-popover v-for="t in teachers" :key="t.id" trigger="hover" placement="top" width="240">
              <template #reference>
                <span class="teacher-chip">{{ t.realName || t.username }}</span>
              </template>
              <div class="teacher-card">
                <div class="teacher-card__name">{{ t.realName || t.username }}</div>
                <div class="teacher-card__row">登录账号：{{ t.username || '-' }}</div>
                <div class="teacher-card__row">手机号：{{ t.phone || '未填写' }}</div>
                <div class="teacher-card__row">邮箱：{{ t.email || '未填写' }}</div>
              </div>
            </el-popover>
          </div>
        </div>

        <!-- 底部操作按钮：按学生与课程的关系状态动态切换 -->
        <div class="base__ops">
          <el-button v-if="state.canApply" type="primary" @click="handleApply">申请加入</el-button>
          <el-button v-else-if="state.inPending" plain disabled>申请审核中</el-button>
          <template v-else-if="state.joined">
            <el-button type="primary" @click="goStudy">进入课程学习</el-button>
            <el-button v-if="state.canQuit" plain @click="handleQuit">退出课程</el-button>
            <el-button v-else-if="state.quitPending" plain disabled>退课审核中</el-button>
          </template>
          <el-button v-else plain disabled>报名已截止</el-button>
        </div>
      </div>
    </el-card>

    <!-- 分 Tab 展示课程内容：课程介绍 / 资料 / 公告 / 作业 / 考试 -->
    <el-card v-if="course" shadow="never" class="tab-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Tab1 课程介绍：详细介绍 / 学习目标 / 适用人群 / 课程大纲 -->
        <el-tab-pane label="课程介绍" name="intro">
          <div class="intro-wrap">
            <!-- 课程类型：与卡片角标、标题后的标签同源，方便学生确认课程性质 -->
            <div class="intro-type">
              <span class="intro-type__label">课程类型</span>
              <el-tag :type="course.courseType ? 'primary' : 'info'" effect="light" size="small">
                {{ courseTypeText }}
              </el-tag>
            </div>

            <template v-if="hasIntroContent">
              <div v-if="course.intro" class="intro-block">
                <h4 class="intro-block__title">课程介绍</h4>
                <p class="intro-block__text">{{ course.intro }}</p>
              </div>
              <div v-if="course.learningObjectives" class="intro-block">
                <h4 class="intro-block__title">学习目标</h4>
                <p class="intro-block__text">{{ course.learningObjectives }}</p>
              </div>
              <div v-if="course.targetAudience" class="intro-block">
                <h4 class="intro-block__title">适用人群</h4>
                <p class="intro-block__text">{{ course.targetAudience }}</p>
              </div>
              <div v-if="syllabusLines.length" class="intro-block">
                <h4 class="intro-block__title">课程大纲</h4>
                <ol class="syllabus">
                  <li v-for="(line, idx) in syllabusLines" :key="idx">{{ line }}</li>
                </ol>
              </div>
            </template>

            <!-- 未填写详细介绍时保留空态引导，课程类型仍可见 -->
            <el-empty v-else :image-size="90" description="该课程暂未填写详细介绍，可先申请加入后查看资料与作业" />
          </div>
        </el-tab-pane>

        <!-- Tab2 课程资料：仅已加入学生可预览/下载 -->
        <el-tab-pane name="resource">
          <template #label>
            <span>课程资料</span>
            <span v-if="!isUnlocked" class="tab-lock-tip">已锁定</span>
          </template>

          <div v-if="!isUnlocked" class="tab-locked">
            <p class="tab-locked__title">加入课程后才能查看课程资料</p>
            <p class="tab-locked__desc">申请加入并由任课教师审核通过后，可在线预览（PDF）与下载课程资料。</p>
            <el-button v-if="state.canApply" type="primary" @click="handleApply">立即申请加入</el-button>
            <el-button v-else-if="state.inPending" plain disabled>申请审核中</el-button>
            <el-button v-else plain disabled>报名已截止</el-button>
          </div>

          <el-table v-else v-loading="loadingResource" :data="resources" stripe border
            empty-text="该课程暂无学习资料">
            <el-table-column label="资料名称" min-width="240">
              <template #default="{ row }">
                <div class="res-name">{{ row.resourceName }}</div>
                <div v-if="row.description" class="res-desc">{{ row.description }}</div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">{{ resourceTypeText(row.fileType) }}</template>
            </el-table-column>
            <el-table-column prop="fileSizeText" label="大小" width="100" align="center" />
            <el-table-column label="上传教师" width="120" align="center">
              <template #default="{ row }">{{ row.uploadTeacherName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="上传时间" width="170" align="center" />
            <el-table-column label="操作" width="150" align="center" fixed="right">
              <template #default="{ row }">
                <!-- 外部链接资料（第三方教学视频）：新窗口跳转学习，不提供下载 -->
                <el-button v-if="row.fileType === 'link'" type="primary" link @click="openLink(row)">去学习</el-button>
                <template v-else>
                  <el-button v-if="row.fileType === 'pdf'" type="primary" link @click="previewResource(row)">预览</el-button>
                  <el-button type="primary" link @click="downloadResource(row)">下载</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab3 课程公告：任课教师发布的课程通知，按时间倒序 -->
        <el-tab-pane name="notice">
          <template #label>
            <span>课程公告</span>
            <span v-if="!isUnlocked" class="tab-lock-tip">已锁定</span>
          </template>

          <div v-if="!isUnlocked" class="tab-locked">
            <p class="tab-locked__title">加入课程后才能查看课程公告</p>
            <p class="tab-locked__desc">课程公告由任课教师发布，加入课程后即可查看最新通知。</p>
            <el-button v-if="state.canApply" type="primary" @click="handleApply">立即申请加入</el-button>
            <el-button v-else-if="state.inPending" plain disabled>申请审核中</el-button>
            <el-button v-else plain disabled>报名已截止</el-button>
          </div>

          <div v-else v-loading="loadingNotice" class="notice-list">
            <div v-for="n in notices" :key="n.id" class="notice-item">
              <div class="notice-item__head">
                <span class="notice-item__title">{{ n.title }}</span>
                <span class="notice-item__time">{{ n.createTime || '-' }}</span>
              </div>
              <div class="notice-item__meta">发布人：{{ n.teacherName || '任课教师' }}</div>
              <p class="notice-item__content">{{ n.content }}</p>
            </div>
            <el-empty v-if="!loadingNotice && notices.length === 0" :image-size="90" description="该课程暂无公告" />
          </div>
        </el-tab-pane>

        <!-- Tab4 课程作业：与「作业列表」同源，这里按课程筛选展示 -->
        <el-tab-pane name="homework">
          <template #label>
            <span>课程作业</span>
            <span v-if="!isUnlocked" class="tab-lock-tip">已锁定</span>
          </template>

          <div v-if="!isUnlocked" class="tab-locked">
            <p class="tab-locked__title">加入课程后才能查看课程作业</p>
            <p class="tab-locked__desc">加入课程后可在作业列表中在线作答，或下载附件作业线下完成后回传。</p>
            <el-button v-if="state.canApply" type="primary" @click="handleApply">立即申请加入</el-button>
            <el-button v-else-if="state.inPending" plain disabled>申请审核中</el-button>
            <el-button v-else plain disabled>报名已截止</el-button>
          </div>

          <template v-else>
            <div class="tab-toolbar">
              <span class="tab-tip">共 {{ homeworkTotal }} 份作业，点击「去作业列表」可在线作答并查看我的练习记录</span>
              <el-button type="primary" plain size="small" @click="goHomework">去作业列表</el-button>
            </div>
            <el-table v-loading="loadingHomework" :data="homeworkList" stripe border
              empty-text="该课程暂无作业">
              <el-table-column label="作业名称" min-width="220">
                <template #default="{ row }">
                  <div class="res-name">{{ row.paperName }}</div>
                  <div v-if="row.description" class="res-desc">{{ row.description }}</div>
                </template>
              </el-table-column>
              <el-table-column label="作业类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.questionCount > 0 ? 'primary' : 'warning'" effect="plain">
                    {{ row.questionCount > 0 ? '在线作业' : '附件作业' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="题目数" width="90" align="center">
                <template #default="{ row }">{{ row.questionCount || '—' }}</template>
              </el-table-column>
              <el-table-column label="满分" width="90" align="center">
                <template #default="{ row }">{{ row.totalScore != null ? row.totalScore : '-' }}</template>
              </el-table-column>
              <el-table-column label="创建教师" width="120" align="center">
                <template #default="{ row }">{{ row.createTeacherName || '-' }}</template>
              </el-table-column>
              <el-table-column label="我的练习" width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.myPracticeCount > 0">{{ row.myPracticeCount }} 次</span>
                  <span v-else class="muted">未练过</span>
                </template>
              </el-table-column>
              <el-table-column label="发布时间" width="170" align="center">
                <template #default="{ row }">{{ row.createTime || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center" fixed="right">
                <template #default="{ row }">
                  <el-link v-if="row.attachmentPath" type="primary" :href="row.attachmentPath" target="_blank">
                    下载作业文件
                  </el-link>
                  <el-button type="primary" link @click="goHomework">去完成</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-tab-pane>

        <!-- Tab5 课程考试：绑定本课程的正式考试 -->
        <el-tab-pane name="exam">
          <template #label>
            <span>课程考试</span>
            <span v-if="!isUnlocked" class="tab-lock-tip">已锁定</span>
          </template>

          <div v-if="!isUnlocked" class="tab-locked">
            <p class="tab-locked__title">加入课程后才能参加课程考试</p>
            <p class="tab-locked__desc">考试开始后可在「我的考试」中进入作答，考完可查看成绩与试卷。</p>
            <el-button v-if="state.canApply" type="primary" @click="handleApply">立即申请加入</el-button>
            <el-button v-else-if="state.inPending" plain disabled>申请审核中</el-button>
            <el-button v-else plain disabled>报名已截止</el-button>
          </div>

          <el-table v-else v-loading="loadingExam" :data="exams" stripe border
            empty-text="该课程暂无考试">
            <el-table-column prop="examName" label="考试名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="paperName" label="试卷" min-width="160" show-overflow-tooltip />
            <el-table-column label="题数" width="80" align="center">
              <template #default="{ row }">{{ row.questionCount || '-' }}</template>
            </el-table-column>
            <el-table-column label="总分" width="80" align="center">
              <template #default="{ row }">{{ row.totalScore || '-' }}</template>
            </el-table-column>
            <el-table-column label="时长" width="90" align="center">
              <template #default="{ row }">{{ row.duration }} 分钟</template>
            </el-table-column>
            <el-table-column label="考试时间" min-width="300">
              <template #default="{ row }">
                {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="examStatusTag(row)" effect="light">{{ examStatusText(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="我的成绩" width="100" align="center">
              <template #default="{ row }">
                <strong v-if="row.myRecordId != null" :class="row.myScore >= row.passScore ? 'pass' : 'fail'">
                  {{ row.myScore }}
                </strong>
                <span v-else class="muted">未参加</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canTakeExam(row)" type="primary" size="small" @click="goExam(row)">去考试</el-button>
                <el-button v-else-if="row.myRecordId != null" size="small"
                  @click="goGrades(row)">{{ row.runtimeStatus === 2 ? '查看试卷' : '查看成绩' }}</el-button>
                <el-button v-else-if="row.runtimeStatus === 0" size="small" disabled>未开始</el-button>
                <el-button v-else size="small" disabled>已结束</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-empty v-if="!loading && !course" :image-size="110" description="课程不存在或已被删除">
      <el-button type="primary" @click="goBack">返回加入课程</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applyCourse, getStudentCourseDetail, getStudentCourseNotices, quitCourse } from '@/api/studentCourse'
import { getPublishedPracticePapers } from '@/api/studentPractice'
import { getMyExams } from '@/api/studentExam'
import {
  downloadStudentStudyResource,
  getStudentStudyResources,
  previewStudentStudyResource,
  viewStudentStudyResource
} from '@/api/studyResource'

const route = useRoute()
const router = useRouter()

const courseId = Number(route.params.courseId)
const loading = ref(false)
const course = ref(null)
const teachers = ref([])
const state = reactive({
  applyStatus: null,
  canApply: false,
  inPending: false,
  joined: false,
  quitPending: false,
  canQuit: false,
  applyClosed: false
})

const activeTab = ref('intro')
// 资料 / 公告 / 作业 / 考试数据均为「切到该 Tab 时才加载」，避免未加入时白白发请求
const resources = ref([])
const notices = ref([])
const homeworkList = ref([])
const homeworkTotal = ref(0)
const exams = ref([])
const loadingResource = ref(false)
const loadingNotice = ref(false)
const loadingHomework = ref(false)
const loadingExam = ref(false)
const loadedTabs = reactive({ resource: false, notice: false, homework: false, exam: false })

/** 是否已解锁内容型 Tab（资料 / 公告 / 作业 / 考试）：只有「已加入」才解锁 */
const isUnlocked = computed(() => state.joined)

/** 课程类型（专业课 / 公共课 等），未设置时归为「未分类」 */
const courseTypeText = computed(() => course.value?.courseType || '未分类')

/** 是否填写了课程介绍相关内容（都没有时展示空态引导） */
const hasIntroContent = computed(() => Boolean(
  course.value?.intro || course.value?.learningObjectives
  || course.value?.targetAudience || course.value?.syllabus
))

/** 课程大纲：后端按「每行一个章节」存储，这里逐行渲染成有序列表 */
const syllabusLines = computed(() => String(course.value?.syllabus || '')
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter(Boolean))

const statusText = computed(() => {
  if (state.joined) return '已加入'
  if (state.inPending) return '审核中'
  if (state.applyClosed) return '报名截止'
  return '开放报名'
})

const statusTagType = computed(() => {
  if (state.joined) return 'success'
  if (state.inPending) return 'warning'
  if (state.applyClosed) return 'info'
  return 'primary'
})

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

const RESOURCE_TYPE_LABEL = {
  pdf: 'PDF', word: 'Word', ppt: 'PPT', excel: 'Excel',
  text: '文本', compress: '压缩包', image: '图片', video: '视频', audio: '音频',
  link: '外部链接', other: '其他'
}
const resourceTypeText = (t) => RESOURCE_TYPE_LABEL[t] || t || '-'

/** 加载课程详情 + 当前学生关系状态 */
async function loadDetail() {
  loading.value = true
  try {
    const res = await getStudentCourseDetail(courseId)
    course.value = res.data?.course || null
    teachers.value = res.data?.teachers || []
    Object.assign(state, {
      applyStatus: null,
      canApply: false,
      inPending: false,
      joined: false,
      quitPending: false,
      canQuit: false,
      applyClosed: false
    }, res.data?.state || {})
    if (state.joined) {
      // 已加入：当前 Tab 需要的课程内容直接拉取
      await loadTabData(activeTab.value)
    }
  } catch {
    course.value = null
  } finally {
    loading.value = false
  }
}

/** Tab 内容懒加载：未加入时全部锁定，不请求后端（后端也会对未加入的学生返回 403） */
async function loadTabData(name) {
  if (!isUnlocked.value || !name || name === 'intro' || loadedTabs[name]) return
  loadedTabs[name] = true
  if (name === 'resource') {
    loadingResource.value = true
    try {
      const res = await getStudentStudyResources(courseId)
      resources.value = res.data || []
    } catch {
      resources.value = []
    } finally {
      loadingResource.value = false
    }
  } else if (name === 'notice') {
    loadingNotice.value = true
    try {
      const res = await getStudentCourseNotices(courseId)
      notices.value = res.data || []
    } catch {
      notices.value = []
    } finally {
      loadingNotice.value = false
    }
  } else if (name === 'homework') {
    loadingHomework.value = true
    try {
      // 与「作业列表」同一接口：按课程过滤后取全部（课程内作业数量有限，不分页）
      const res = await getPublishedPracticePapers({ courseId, pageNum: 1, pageSize: 100 })
      homeworkList.value = res.data?.records || []
      homeworkTotal.value = res.data?.total || 0
    } catch {
      homeworkList.value = []
      homeworkTotal.value = 0
    } finally {
      loadingHomework.value = false
    }
  } else if (name === 'exam') {
    loadingExam.value = true
    try {
      // 学生考试接口按学生维度返回全部已发布考试，这里筛出绑定本课程的考试
      const res = await getMyExams()
      exams.value = (res.data || []).filter((e) => Number(e.courseId) === courseId)
    } catch {
      exams.value = []
    } finally {
      loadingExam.value = false
    }
  }
}

function handleTabChange(name) {
  loadTabData(name)
}

/** 申请加入：提交后重新拉详情，状态变为「审核中」 */
async function handleApply() {
  try {
    await ElMessageBox.confirm(`确认要申请加入《${course.value?.courseName || ''}》课程吗？`, '申请加入', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await applyCourse(courseId)
    ElMessage.success(res.msg || '申请已提交，请等待教师审核')
    await loadDetail()
  } catch {
    // 拦截器已提示错误
  }
}

/** 退出课程：提交退课申请，等待教师审核 */
async function handleQuit() {
  try {
    await ElMessageBox.confirm(`确认要申请退出《${course.value?.courseName || ''}》课程吗？`, '退出课程', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const res = await quitCourse(courseId)
    ElMessage.success(res.msg || '退出申请已提交，请等待教师审核')
    await loadDetail()
  } catch {
    // 拦截器已提示错误
  }
}

/** 进入课程学习：留在本页切到「课程资料」Tab，不跳出当前上下文 */
function goStudy() {
  activeTab.value = 'resource'
  loadTabData('resource')
}

/** 去作业列表：与「作业列表」页同源，可在线作答 / 查看练习记录 */
function goHomework() {
  router.push('/student/practice/papers')
}

function canTakeExam(row) {
  return row.status === 1 && row.runtimeStatus === 1 && row.myRecordId == null
}

function examStatusText(row) {
  if (row.status === 0) return '已撤回'
  if (row.runtimeStatus === 0) return '未开始'
  if (row.runtimeStatus === 1) return '进行中'
  return '已结束'
}

function examStatusTag(row) {
  if (row.runtimeStatus === 1) return 'success'
  return 'info'
}

function goExam(row) {
  router.push(`/student/exams/${row.id}/do`)
}

/** 已参加考试：考完可直接打开试卷回看，未结束只能先看分数 */
function goGrades(row) {
  if (row.runtimeStatus === 2) {
    router.push({ path: '/student/grades', query: { recordId: row.myRecordId } })
  } else {
    router.push('/student/grades')
  }
}

async function previewResource(row) {
  try {
    const res = await previewStudentStudyResource(row.id)
    const blob = res.data || res
    if (!(blob instanceof Blob)) {
      ElMessage.error('预览失败')
      return
    }
    window.open(URL.createObjectURL(blob), '_blank')
  } catch (e) {
    ElMessage.error(e?.message || '预览失败')
  }
}

async function downloadResource(row) {
  try {
    const res = await downloadStudentStudyResource(row.id)
    const blob = res.data || res
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.resourceName || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}

/** 外部链接资料（第三方教学视频）：记录一次浏览后新窗口打开 */
async function openLink(row) {
  if (!row.filePath) {
    ElMessage.error('链接地址缺失，请联系老师重新设置')
    return
  }
  try {
    await viewStudentStudyResource(row.id)
  } catch {
    // 统计失败不阻断跳转
  }
  window.open(row.filePath, '_blank', 'noopener,noreferrer')
}

/** 返回「加入课程」：优先回退历史（保留加入课程页的筛选与页码），没有历史时直接跳转 */
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/student/course/join')
  }
}

onMounted(() => {
  if (!courseId || Number.isNaN(courseId)) {
    ElMessage.error('课程参数有误')
    router.replace('/student/course/join')
    return
  }
  loadDetail()
})
</script>

<style scoped>
.course-detail-page {
  padding: 4px;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.back-btn {
  font-size: 14px;
}
.page-sub {
  font-size: 12px;
  color: #909399;
}

/* ============ 顶部课程基础信息区 ============ */
.base-card {
  margin-bottom: 12px;
  border-radius: 12px;
}
.base {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.base__cover {
  position: relative;
  flex: none;
  width: 200px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 10px;
  background: linear-gradient(135deg, #409eff, #79bbff);
}
.base__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.base__cover-text {
  font-size: 34px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
}
.base__info {
  flex: 1;
  min-width: 0;
}
.base__title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.base__title {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
  color: #1f2937;
}
.base__desc {
  margin: 8px 0 10px;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}
.base__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 6px;
  font-size: 13px;
}
.meta-item {
  display: flex;
  gap: 8px;
  min-width: 0;
}
.meta-label {
  flex: none;
  width: 72px;
  color: #909399;
}
.meta-value {
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.teacher-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.teacher-chips__label {
  font-size: 13px;
  color: #909399;
}
.teacher-chip {
  padding: 2px 10px;
  border: 1px solid #d9ecff;
  border-radius: 12px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}
.teacher-chip:hover {
  background: #d9ecff;
}
.teacher-card__name {
  font-weight: 600;
  margin-bottom: 6px;
}
.teacher-card__row {
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}
/* 底部操作按钮：按状态动态切换，右对齐固定高度避免页面跳动 */
.base__ops {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-self: center;
}
.base__ops :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* ============ Tab 区 ============ */
.tab-card {
  border-radius: 12px;
}
.tab-lock-tip {
  margin-left: 6px;
  font-size: 12px;
  color: #c0c4cc;
}
.tab-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 34px 0;
  text-align: center;
}
.tab-locked__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.tab-locked__desc {
  margin: 0;
  max-width: 460px;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.tab-tip {
  font-size: 12px;
  color: #909399;
}

/* 课程介绍 */
.intro-wrap {
  padding: 4px 2px;
}
/* 课程介绍顶部的课程类型（专业课 / 公共课 等） */
.intro-type {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.intro-type__label {
  font-size: 13px;
  color: #909399;
}
.intro-block + .intro-block {
  margin-top: 18px;
}
.intro-block__title {
  margin: 0 0 8px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.intro-block__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}
.syllabus {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: #606266;
}

/* 资料 / 作业表格中的名称与描述 */
.res-name {
  font-weight: 500;
  color: #303133;
}
.res-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.muted {
  color: #c0c4cc;
}
.pass {
  color: #67c23a;
}
.fail {
  color: #f56c6c;
}

/* 课程公告列表 */
.notice-list {
  min-height: 80px;
}
.notice-item {
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
}
.notice-item + .notice-item {
  margin-top: 10px;
}
.notice-item__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.notice-item__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.notice-item__time {
  flex: none;
  font-size: 12px;
  color: #909399;
}
.notice-item__meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.notice-item__content {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  white-space: pre-wrap;
}

/* 窄屏：信息区改为竖向排布 */
@media (max-width: 900px) {
  .base {
    flex-direction: column;
  }
  .base__ops {
    flex-direction: row;
    align-self: flex-start;
  }
  .base__meta {
    grid-template-columns: 1fr;
  }
}
</style>
