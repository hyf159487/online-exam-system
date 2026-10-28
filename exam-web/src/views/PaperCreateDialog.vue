<template>
  <el-dialog
    v-model="visible"
    title="新增试卷"
    :width="dialogWidth"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <el-tabs v-model="activeTab" class="create-tabs">
      <!-- ==================== Tab1：普通创建（默认，单行表单） ==================== -->
      <el-tab-pane label="普通创建" name="single">
        <el-alert
          class="tip-alert"
          title="创建后试卷为草稿状态，完成组卷并发布，方可安排考试"
          type="info"
          :closable="false"
          show-icon
        />

        <el-form
          ref="singleFormRef"
          :model="singleForm"
          :rules="singleRules"
          label-width="92px"
          class="single-form"
        >
          <el-form-item label="试卷名称" prop="paperName">
            <div class="name-row">
              <el-input
                v-model="singleForm.paperName"
                placeholder="如：Java 基础期中考试卷"
                maxlength="100"
                clearable
              />
              <div class="name-quick">
                <span class="quick-label">快捷命名</span>
                <el-tooltip content="请先选择所属学科" :disabled="!!singleCourseName" placement="top">
                  <el-button-group size="small">
                    <el-button
                      v-for="s in NAME_SUFFIXES"
                      :key="s"
                      plain
                      :disabled="!singleCourseName"
                      @click="fillSingleName(s)"
                    >{{ s }}</el-button>
                  </el-button-group>
                </el-tooltip>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="所属学科" prop="courseId">
            <el-select v-model="singleForm.courseId" placeholder="请选择学科" filterable style="width: 320px">
              <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="组卷方式" prop="paperType">
            <el-select v-model="singleForm.paperType" placeholder="请选择组卷方式" style="width: 320px">
              <el-option v-for="o in paperTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-tooltip
              content="手动组卷：到组卷页逐题挑选题目；随机组卷：设置题型 / 难度 / 数量规则后自动抽题；AI 智能组卷：由 AI 按目标总分与难度倾向生成方案并自动抽题"
              placement="top"
            >
              <el-icon class="tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-form-item>

          <el-form-item label="考试时长" prop="duration">
            <el-input-number
              v-model="singleForm.duration"
              :min="10"
              :max="300"
              :step="10"
              :precision="0"
              controls-position="right"
              style="width: 140px"
            />
            <span class="unit">分钟</span>
          </el-form-item>

          <el-form-item label="可见教师">
            <el-select
              v-model="singleVisibleTeacherIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              filterable
              :loading="singleTeachersLoading"
              :disabled="!singleForm.courseId"
              :placeholder="singleForm.courseId ? '不选 = 仅自己可见' : '请先选择所属学科'"
              style="width: 320px"
            >
              <el-option v-for="t in singleTeacherOptions" :key="t.id" :label="t.label" :value="t.id" />
              <template #empty>
                <div class="select-empty">{{ singleTeachersLoading ? '加载中…' : '该学科暂无其他任课教师' }}</div>
              </template>
            </el-select>
            <el-tooltip :content="VISIBLE_TIP" placement="top">
              <el-icon class="tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ==================== Tab2：批量创建（高级，多行表格） ==================== -->
      <el-tab-pane label="批量创建" name="batch">
        <el-alert
          class="tip-alert"
          title="批量创建多张草稿试卷，适合一次建立多套章节练习卷；每张都需完成组卷并发布，方可安排考试"
          type="info"
          :closable="false"
          show-icon
        />

        <div class="batch-ops">
          <span class="batch-label">学科快捷</span>
          <el-select v-model="quickCourseId" placeholder="请选择学科（自动应用到全部行）" filterable style="width: 300px">
            <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
          <el-tooltip content="选一次学科即自动应用到全部行，新添加的行也会自动带上该学科" placement="top">
            <el-icon class="tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>

        <div class="batch-ops">
          <span class="batch-label">可见教师</span>
          <el-select
            v-model="batchVisibleTeacherIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            filterable
            :loading="batchTeachersLoading"
            :disabled="batchCourseId == null"
            :placeholder="batchCourseId == null ? batchTeacherPlaceholder : '不选 = 仅自己可见'"
            style="width: 300px"
          >
            <el-option v-for="t in batchTeacherOptions" :key="t.id" :label="t.label" :value="t.id" />
            <template #empty>
              <div class="select-empty">{{ batchTeachersLoading ? '加载中…' : '该学科暂无其他任课教师' }}</div>
            </template>
          </el-select>
          <el-tooltip :content="VISIBLE_TIP" placement="top">
            <el-icon class="tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>

        <div class="batch-ops">
          <span class="batch-label">名称快捷</span>
          <el-tooltip content="请先在学科快捷中选择学科" :disabled="!!selectedCourseName" placement="top">
            <el-button-group size="small">
              <el-button
                v-for="s in NAME_SUFFIXES"
                :key="s"
                plain
                :disabled="!selectedCourseName"
                @click="quickFillName(s)"
              >{{ s }}</el-button>
            </el-button-group>
          </el-tooltip>
          <el-input
            v-model="quickPaperName"
            placeholder="如：Java 期中试卷（也可直接填写）"
            maxlength="97"
            style="width: 220px"
            clearable
          />
          <el-button
            type="success"
            plain
            :icon="Check"
            :disabled="!quickPaperName.trim() || !rows.length"
            @click="applyQuickName"
          >编号应用到全部行</el-button>
        </div>

        <el-table ref="batchTableRef" :data="rows" border size="small" max-height="420" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="45" align="center" />
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="试卷名称" min-width="200">
            <template #default="{ row }">
              <el-input v-model="row.paperName" placeholder="如：Java 基础期中考试卷" maxlength="100" />
            </template>
          </el-table-column>
          <el-table-column label="所属学科" width="180">
            <template #default="{ row }">
              <el-select v-model="row.courseId" placeholder="请选择学科" filterable style="width: 100%">
                <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="组卷方式" width="120">
            <template #default="{ row }">
              <el-select v-model="row.paperType" style="width: 100%">
                <el-option v-for="o in paperTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="考试时长" width="150" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.duration"
                :min="10"
                :max="300"
                :step="10"
                :precision="0"
                controls-position="right"
                style="width: 120px"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" :disabled="rows.length <= 1" @click="removeRow($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="batch-ops">
          <el-button type="primary" plain :icon="Plus" :disabled="rows.length >= MAX_ROWS" @click="addRows">添加</el-button>
          <el-input-number
            v-model="addCount"
            :min="1"
            :max="Math.max(1, MAX_ROWS - rows.length)"
            :precision="0"
            controls-position="right"
            style="width: 110px"
            :disabled="rows.length >= MAX_ROWS"
          />
          <span class="batch-label">行</span>
          <el-tooltip content="请先勾选要删除的行" :disabled="selectedRows.length > 0" placement="top">
            <el-button
              type="danger"
              :icon="Delete"
              :disabled="selectedRows.length === 0"
              @click="handleBatchRemove"
            >批量删除{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}</el-button>
          </el-tooltip>
          <el-button type="warning" plain :icon="Brush" @click="handleClearAll">清空</el-button>
          <span class="batch-tip">
            已填 {{ rows.length }} 行，单次最多 {{ MAX_ROWS }} 行<template v-if="rows.length < MAX_ROWS">，还可添加 {{ MAX_ROWS - rows.length }} 行</template><template v-else>（已达上限）</template>
          </span>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="activeTab === 'single'" type="primary" :loading="saving" @click="handleSingleSubmit">确定</el-button>
      <el-button v-else type="primary" :loading="saving" @click="handleBatchSubmit">全部新增</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Brush, Check, QuestionFilled } from '@element-plus/icons-vue'
import { createPaper } from '@/api/examPaper'
import { getCourseList, getCourseDetail } from '@/api/course'
import { useAuthStore } from '@/stores/auth'
import { usePaperTypeOptions } from '@/composables/usePaperTypeOptions'

const props = defineProps({
  // 保存成功后回调（普通创建传新试卷 id，列表页可直接跳组卷；批量创建无参只刷新列表）
  saved: { type: Function, required: true },
  // 嵌入「课程信息 → 课程试卷」抽屉时传入：新增试卷默认归属该学科
  defaultCourseId: { type: [Number, String], default: null }
})

/** 组卷方式选项（字典 paper_type，字典缺失时回退到 1 手动 / 2 随机） */
const { paperTypeOptions, loadPaperTypeOptions } = usePaperTypeOptions()

/** 单次批量新增上限 */
const MAX_ROWS = 50
/** 名称快捷后缀（普通创建 / 批量创建共用） */
const NAME_SUFFIXES = ['平时测试', '章节练习', '期中考试', '期末考试']

const visible = ref(false)
const saving = ref(false)
const authStore = useAuthStore()

/** 当前 Tab：single 普通创建（默认） / batch 批量创建 */
const activeTab = ref('single')

/** 弹窗宽度：单行表单用窄弹窗，批量表格用宽弹窗 */
const dialogWidth = computed(() => (activeTab.value === 'single' ? '620px' : '900px'))

/** 学科下拉数据 */
const courseList = ref([])

onMounted(() => {
  loadPaperTypeOptions()
  getCourseList()
    .then((res) => {
      courseList.value = res.data || []
    })
    .catch((err) => console.error('获取学科列表失败:', err))
})

// ============ 可见教师：候选只取「该学科的任课教师」（sys_course_teacher 绑定） ============
/** 可见教师说明（两个 Tab 共用文案） */
const VISIBLE_TIP = '只能共享给该学科的任课教师；共享教师仅可查看和使用试卷（组卷、安排考试），不能编辑或删除'

/** 学科 → 任课教师选项缓存 { [courseId]: [{ id, label }] }（已排除当前登录人自己） */
const courseTeacherCache = reactive({})
/** 正在请求中的学科 id（同一学科不重复请求，同时驱动下拉 loading） */
const loadingCourseIds = reactive(new Set())

/** 按学科拉取任课教师（复用课程详情接口，内部已按 course_id 关联教师） */
async function ensureCourseTeachers(courseId) {
  if (courseId == null) return
  if (courseTeacherCache[courseId] || loadingCourseIds.has(courseId)) return
  loadingCourseIds.add(courseId)
  try {
    const res = await getCourseDetail(courseId)
    const myId = authStore.userInfo?.userId
    courseTeacherCache[courseId] = (res.data?.teachers || [])
      .filter((t) => t && t.id !== myId)
      .map((t) => ({ id: t.id, label: t.realName || t.username }))
  } catch (e) {
    courseTeacherCache[courseId] = []
    console.error('获取学科任课教师失败:', e)
  } finally {
    loadingCourseIds.delete(courseId)
  }
}

/** 按 id 取学科名 */
function courseNameById(id) {
  if (id == null) return ''
  const c = courseList.value.find((x) => x.id === id)
  return c ? c.courseName : ''
}

// ==================== Tab1：普通创建 ====================
const singleFormRef = ref(null)

const singleForm = reactive({
  paperName: '',
  courseId: null,
  paperType: 1,
  duration: 60
})

const singleRules = {
  paperName: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择学科', trigger: 'change' }],
  duration: [{ required: true, message: '请填写考试时长', trigger: 'blur' }]
}

/** 普通创建当前所选学科名 */
const singleCourseName = computed(() => courseNameById(singleForm.courseId))

/** 共享给哪些教师（空数组 = 仅自己可见） */
const singleVisibleTeacherIds = ref([])
/** 候选教师 = 当前所选学科的任课教师 */
const singleTeacherOptions = computed(() => courseTeacherCache[singleForm.courseId] || [])
const singleTeachersLoading = computed(
  () => singleForm.courseId != null && loadingCourseIds.has(singleForm.courseId)
)

/**
 * 切换学科时：
 * 1) 候选教师随学科变化，原选择失效 → 清空并重新加载该学科的任课教师
 * 2) 若名称以某个学科名开头（多半是快捷命名填进去的），自动把前缀换成新学科名；纯手写名称不动
 */
watch(
  () => singleForm.courseId,
  (newId, oldId) => {
    if (singleVisibleTeacherIds.value.length) {
      singleVisibleTeacherIds.value = []
      ElMessage.info('已切换学科，可见教师已重置，请重新选择')
    }
    ensureCourseTeachers(newId)

    const newName = courseNameById(newId)
    if (!newName) return
    const cur = singleForm.paperName
    if (!cur) return
    const oldName = courseNameById(oldId)
    let base = oldName && cur.startsWith(oldName) ? oldName : ''
    if (!base) {
      const hit = courseList.value.find((c) => c.courseName && cur.startsWith(c.courseName))
      base = hit ? hit.courseName : ''
    }
    if (base && base !== newName) {
      singleForm.paperName = newName + cur.slice(base.length)
    }
  }
)

/** 快捷命名：学科名 + 用途后缀 */
function fillSingleName(suffix) {
  if (!singleCourseName.value) {
    ElMessage.warning('请先选择所属学科')
    return
  }
  singleForm.paperName = `${singleCourseName.value}${suffix}`
}

/** 普通创建提交 */
async function handleSingleSubmit() {
  try {
    await singleFormRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    const res = await createPaper({
      paperName: singleForm.paperName.trim(),
      courseId: singleForm.courseId,
      paperType: singleForm.paperType,
      duration: singleForm.duration,
      visibleUserIds: singleVisibleTeacherIds.value
    })
    ElMessage.success('新增成功，接下来去组卷吧')
    visible.value = false
    // 第二个参数回传组卷方式：3 = AI 智能组卷，列表页会据此在组卷页自动打开 AI 弹窗
    props.saved(res.data, singleForm.paperType)
  } catch (e) {
    ElMessage.error(e.message || '新增失败')
  } finally {
    saving.value = false
  }
}

function resetSingle() {
  Object.assign(singleForm, { paperName: '', courseId: null, paperType: 1, duration: 60 })
  singleVisibleTeacherIds.value = []
  singleFormRef.value?.clearValidate()
}

// ==================== Tab2：批量创建 ====================
function newRow() {
  return { paperName: '', courseId: null, paperType: 1, duration: 60 }
}

const rows = ref([newRow()])
/** 自定义添加行数 */
const addCount = ref(1)
/** 快捷设置学科（一键应用到全部行，新行自动带上） */
const quickCourseId = ref(null)
/** 快捷设置试卷名称（基础名 + 行号编号 01/02/…） */
const quickPaperName = ref('')

/** 学科快捷所选学科名称（用于「编号应用到全部行」与前缀替换） */
const selectedCourseName = computed(() => courseNameById(quickCourseId.value))
/** 上次应用过的旧学科名（用于学科变更时同步更新名称中的学科前缀） */
const lastAutoCourseName = ref('')

/** 共享给哪些教师（空数组 = 仅自己可见） */
const batchVisibleTeacherIds = ref([])

/**
 * 批量创建时「可见教师」依据的学科：
 * 各行学科一致 → 用该学科；各行还没选 → 用学科快捷所选学科；学科不一致 → 无法统一共享（下拉禁用）
 */
const batchCourseId = computed(() => {
  const ids = new Set(rows.value.map((r) => r.courseId).filter((v) => v != null))
  if (ids.size === 1) return [...ids][0]
  if (ids.size === 0) return quickCourseId.value ?? null
  return null
})
/** 各行学科被改成不一致时，给出针对性的占位提示 */
const batchTeacherPlaceholder = computed(() => {
  const ids = new Set(rows.value.map((r) => r.courseId).filter((v) => v != null))
  if (ids.size > 1) return '各行学科不一致，暂不能共享'
  return '请先选择学科'
})
/** 候选教师 = 当前批次的学科的任课教师 */
const batchTeacherOptions = computed(() => courseTeacherCache[batchCourseId.value] || [])
const batchTeachersLoading = computed(
  () => batchCourseId.value != null && loadingCourseIds.has(batchCourseId.value)
)

/** 学科变化（学科快捷或各行学科）时：候选教师随之变化，原选择清空并重新加载 */
watch(batchCourseId, (id) => {
  if (batchVisibleTeacherIds.value.length) {
    batchVisibleTeacherIds.value = []
    ElMessage.info('学科已变化，可见教师已重置，请重新选择')
  }
  ensureCourseTeachers(id)
})

/**
 * 学科快捷变更时：
 * 1) 自动把学科应用到全部行（替代原来的「应用到全部行」按钮）
 * 2) 若名称以前一次学科名开头 → 自动替换成新学科名
 * 3) 若名称以任一学科名开头 → 同样替换为新学科名
 * 4) 否则保留原名称（用户纯手写的不强制替换）
 */
watch(
  () => [quickCourseId.value, courseList.value],
  ([newId], [oldId]) => {
    const newName = selectedCourseName.value
    if (!newName && !newId) {
      lastAutoCourseName.value = ''
      return
    }

    // 1) 自动应用学科到全部行
    if (quickCourseId.value != null) {
      rows.value.forEach((r) => {
        r.courseId = quickCourseId.value
      })
    }

    if (!newName) {
      lastAutoCourseName.value = ''
      return
    }

    let oldName = ''
    if (oldId) {
      const oldC = courseList.value.find((x) => x.id === oldId)
      if (oldC) oldName = oldC.courseName
    }
    if (!oldName) oldName = lastAutoCourseName.value

    const cur = quickPaperName.value
    if (cur && oldName && cur.startsWith(oldName)) {
      quickPaperName.value = newName + cur.slice(oldName.length)
      lastAutoCourseName.value = newName
      return
    }
    // 兜底：如果名称以任一学科名开头，也替换
    if (cur) {
      for (const c of courseList.value) {
        if (c.courseName && cur.startsWith(c.courseName) && c.courseName !== newName) {
          quickPaperName.value = newName + cur.slice(c.courseName.length)
          break
        }
      }
    }
    lastAutoCourseName.value = newName
  },
  { flush: 'sync' }
)

/** 按学科生成名称：学科名 + 用途后缀（平时测试/章节练习/期中考试/期末考试） */
function quickFillName(suffix) {
  if (!selectedCourseName.value) {
    ElMessage.warning('请先在学科快捷中选择学科')
    return
  }
  quickPaperName.value = `${selectedCourseName.value}${suffix}`
  lastAutoCourseName.value = selectedCourseName.value
}

function applyQuickName() {
  const base = quickPaperName.value.trim()
  if (!base) {
    ElMessage.warning('请先填写试卷名称（基础名）')
    return
  }
  rows.value.forEach((r, i) => {
    r.paperName = `${base}${String(i + 1).padStart(2, '0')}`
  })
  ElMessage.success(`已按「${base}01」起编号应用到全部 ${rows.value.length} 行`)
}

function addRows() {
  const available = MAX_ROWS - rows.value.length
  if (available <= 0) {
    ElMessage.warning(`单次最多新增 ${MAX_ROWS} 张试卷`)
    return
  }
  const count = Math.min(addCount.value || 1, available)
  for (let i = 0; i < count; i++) {
    const r = newRow()
    if (quickCourseId.value) r.courseId = quickCourseId.value
    rows.value.push(r)
  }
  ElMessage.success(`已添加 ${count} 行`)
  addCount.value = 1
}

function removeRow(index) {
  if (rows.value.length <= 1) return
  rows.value.splice(index, 1)
}

// ---------- 勾选批量删除 / 清空 ----------
const batchTableRef = ref(null)
const selectedRows = ref([])

function onSelectionChange(selection) {
  selectedRows.value = selection
}

/** 批量删除勾选行（至少保留一行） */
function handleBatchRemove() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要删除的行')
    return
  }
  if (selectedRows.value.length >= rows.value.length) {
    ElMessage.warning('至少保留一行，不能删除全部行')
    return
  }
  const selectedSet = new Set(selectedRows.value)
  rows.value = rows.value.filter((r) => !selectedSet.has(r))
  clearSelection()
}

/** 清空全部（保留一行空白行） */
async function handleClearAll() {
  try {
    await ElMessageBox.confirm(
      '确定清空所有行吗？清空后将保留一行空白行。',
      '清空确认',
      { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  rows.value = [newRow()]
  clearSelection()
}

function clearSelection() {
  selectedRows.value = []
  batchTableRef.value?.clearSelection()
}

/** 逐行校验必填项 + 批量内部名称重复校验，返回首个不合规提示；全部合规返回 null */
function firstInvalid() {
  const nameSet = new Set()
  for (let i = 0; i < rows.value.length; i++) {
    const r = rows.value[i]
    if (!r.paperName || !r.paperName.trim()) return `第 ${i + 1} 行请填写试卷名称`
    if (r.courseId == null) return `第 ${i + 1} 行请选择所属学科`
    if (!r.duration || r.duration < 10) return `第 ${i + 1} 行考试时长至少 10 分钟`
    const name = r.paperName.trim()
    if (nameSet.has(name)) {
      return `第 ${i + 1} 行试卷名称「${name}」与前面的行重复`
    }
    nameSet.add(name)
  }
  return null
}

async function handleBatchSubmit() {
  const invalidMsg = firstInvalid()
  if (invalidMsg) {
    ElMessage.warning(invalidMsg)
    return
  }
  saving.value = true
  try {
    // 复用单条新增接口，循环调用并统计结果（可见教师 List<Long> 应用到全部行）
    const visibleIds = batchVisibleTeacherIds.value  // List 形式，后端直接接收
    const results = await Promise.allSettled(
      rows.value.map((r) =>
        createPaper({
          paperName: r.paperName.trim(),
          courseId: r.courseId,
          paperType: r.paperType,
          duration: r.duration,
          visibleUserIds: visibleIds
        })
      )
    )
    const ok = results.filter((x) => x.status === 'fulfilled').length
    const fail = results.length - ok
    if (fail === 0) {
      ElMessage.success(
        batchVisibleTeacherIds.value.length
          ? `批量新增成功，共 ${ok} 张试卷（已共享给 ${batchVisibleTeacherIds.value.length} 位教师）`
          : `批量新增成功，共 ${ok} 张试卷（草稿状态）`
      )
      visible.value = false
    } else {
      // 取第一条失败的 reason 展示（重名/超范围等后端校验信息）
      const firstErr = results.find((x) => x.status === 'rejected')
      const errDetail = firstErr?.reason?.response?.data?.msg || firstErr?.reason?.message || ''
      ElMessage.warning(
        `新增成功 ${ok} 张，失败 ${fail} 张${errDetail ? `（失败原因：${errDetail}）` : ''}，请调整后重试失败的行`
      )
    }
    props.saved()
  } catch (e) {
    ElMessage.error(e.message || '批量新增失败')
  } finally {
    saving.value = false
  }
}

// ==================== 打开 / 关闭 ====================
function open() {
  activeTab.value = 'single'
  // 每次打开清一次任课教师缓存：管理员刚调整过课程教师绑定时也能拿到最新名单
  Object.keys(courseTeacherCache).forEach((k) => delete courseTeacherCache[k])
  // 嵌入「课程信息 → 课程试卷」抽屉时，新增试卷默认归属本课程（仍可手动改）
  if (props.defaultCourseId != null && props.defaultCourseId !== '') {
    singleForm.courseId = Number(props.defaultCourseId)
    ensureCourseTeachers(singleForm.courseId)
  }
  visible.value = true
  nextTick(() => singleFormRef.value?.clearValidate())
}

/** 关闭后重置全部状态，下次打开是干净的表单 */
function handleClosed() {
  resetSingle()
  rows.value = [newRow()]
  quickCourseId.value = null
  quickPaperName.value = ''
  lastAutoCourseName.value = ''
  addCount.value = 1
  batchVisibleTeacherIds.value = []
  selectedRows.value = []
}

defineExpose({ open })
</script>

<style scoped>
.create-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.tip-alert {
  margin-bottom: 16px;
}
.single-form {
  padding-right: 4px;
}
.name-row {
  width: 100%;
}
.name-quick {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.quick-label {
  font-size: 12.5px;
  color: #909399;
}
.unit {
  margin-left: 8px;
  color: #909399;
}
.tip-icon {
  margin-left: 6px;
  font-size: 14px;
  color: #a8abb2;
  cursor: help;
}
.batch-ops {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.batch-label {
  font-size: 14px;
  color: #606266;
}
.batch-tip {
  font-size: 13px;
  color: #909399;
}
.select-empty {
  padding: 8px 0;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
</style>
