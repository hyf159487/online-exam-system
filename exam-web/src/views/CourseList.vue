<template>
  <div class="course-list">
    <!-- 搜索筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="课程名称">
          <el-input v-model="query.keyword" placeholder="课程名称 / 编码搜索" clearable style="width: 240px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-toolbar">
        <span class="total-info">共 {{ filteredData.length }} 门课程</span>
        <span class="total-info">（点击课程卡片可查看课程信息）</span>
        <span v-if="query.keyword" class="total-info">（关键字筛选中）</span>
        <span v-if="batchMode" class="total-info batch-hint">已勾选 {{ selectedIds.size }} 门，支持跨页勾选</span>
        <div v-if="isAdmin" class="toolbar-actions">
          <!-- 批量删除采用「先进入勾选模式」：默认不显示勾选框，点按钮后才出现，避免误勾 -->
          <el-button v-if="!batchMode" type="danger" plain :icon="Delete" @click="enterBatchMode">批量删除</el-button>
          <template v-else>
            <el-button :icon="Close" @click="exitBatchMode">退出批量删除</el-button>
            <el-button type="danger" :icon="Delete" :disabled="selectedIds.size === 0" @click="handleBatchDelete">
              确认删除{{ selectedIds.size > 0 ? `（${selectedIds.size}）` : '' }}
            </el-button>
          </template>
          <el-button type="success" :icon="UserFilled" @click="openAssignDialog">一键分配老师</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增课程</el-button>
          <el-button type="warning" :icon="Upload" @click="openImportDialog">Excel 导入</el-button>
        </div>
      </div>

      <!-- 课程卡片视图：封面 + 课程名 + 任课教师 + 简介 + 编码/参加人数 + 操作
           批量删除进入勾选模式后，卡片左上角出现勾选框，勾选状态跨页保留 -->
      <div v-loading="loading" class="course-grid-wrap">
        <div v-if="pagedData.length" class="course-card-grid">
          <CourseCard
            v-for="row in pagedData"
            :key="row.id"
            :course="row"
            :badge="row.courseType || '未分类'"
            badge-type="open"
            :selectable="isAdmin && batchMode"
            :clickable="!batchMode"
            @click="handleCardClick(row)"
          >
            <template #select>
              <el-checkbox
                :model-value="selectedIds.has(row.id)"
                @change="toggleSelect(row)"
              />
            </template>

            <template #meta>
              <span :title="row.courseCode || ''">{{ row.courseCode || '未设编码' }}</span>
              <span>参加人数 <b>{{ row.studentCount || 0 }}</b> 人</span>
            </template>

            <template #ops>
              <el-button size="small" type="success" plain @click="goInfo(row)">
                <el-icon><View /></el-icon> 课程信息
              </el-button>
              <el-button v-if="isAdmin" size="small" type="primary" plain @click="openEditDialog(row)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button v-if="isAdmin" size="small" type="danger" plain @click="handleDelete(row)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </template>
          </CourseCard>
        </div>

        <el-empty
          v-else-if="!loading"
          description="暂无课程数据，请点击右上角「新增课程」或「Excel 导入」"
        />
      </div>

      <!-- 分页（每页 8 门，与卡片 4 列 × 2 行对齐） -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[8, 16, 24, 32]"
          :total="filteredData.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="pageNum = 1"
        />
      </div>
    </el-card>

    <!-- 新增/编辑课程对话框（仅管理员） -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑课程' : '新增课程'" width="500px" @closed="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" placeholder="如 数据结构" maxlength="50" />
        </el-form-item>
        <el-form-item label="课程编码" prop="courseCode">
          <el-input v-model="form.courseCode" placeholder="如 CS202（唯一，用于题库/试卷关联）" maxlength="20" />
        </el-form-item>
        <el-form-item label="课程类型" prop="courseType">
          <el-select
            v-model="form.courseType"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="请选择或手动填写课程类型"
            style="width: 100%"
          >
            <el-option v-for="type in courseTypes" :key="type" :label="type" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="任课教师" prop="teacherIds">
          <el-select
            v-model="form.teacherIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择任课教师（可多选，可留空）"
            style="width: 100%"
          >
            <el-option
              v-for="t in teacherOptions"
              :key="t.id"
              :label="`${t.realName}（${t.username}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="卡片上的简短简介，选填" maxlength="200" show-word-limit />
        </el-form-item>
        <!-- 以下 4 项供学生端「课程详情 → 课程介绍」Tab 展示，留空时该 Tab 显示「暂未填写」提示 -->
        <el-form-item label="详细介绍" prop="intro">
          <el-input v-model="form.intro" type="textarea" :rows="4"
            placeholder="课程完整介绍（学生端课程详情页展示），与上方卡片简介区分，选填" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="学习目标" prop="learningObjectives">
          <el-input v-model="form.learningObjectives" type="textarea" :rows="2"
            placeholder="学完本课程后能掌握什么，选填" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="适用人群" prop="targetAudience">
          <el-input v-model="form.targetAudience" type="textarea" :rows="2"
            placeholder="如：计算机专业二年级学生 / 零基础自学者，选填" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="课程大纲" prop="syllabus">
          <el-input v-model="form.syllabus" type="textarea" :rows="4"
            placeholder="每行一个章节，例如：&#10;第一章 绪论&#10;第二章 线性表" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="课程封面">
          <div class="cover-field">
            <div class="cover-preview">
              <img v-if="form.cover" :src="form.cover" alt="课程封面" />
              <span v-else class="cover-preview__tip">暂无封面</span>
            </div>
            <div class="cover-actions">
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
                :on-change="handleCoverChange"
              >
                <el-button size="small" :icon="Upload" :loading="coverUploading">
                  {{ form.cover ? '更换封面' : '上传封面' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.cover" size="small" link type="danger" @click="form.cover = ''">移除</el-button>
              <span class="cover-tip">jpg/png/gif/webp/bmp，不超过 5MB；留空时学生端显示默认封面</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="开课时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选填，学生端展示课程何时开始"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报名截止" prop="enrollDeadline">
          <el-date-picker
            v-model="form.enrollDeadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选填，留空表示长期开放报名"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- Excel 导入对话框 -->
    <el-dialog v-model="importVisible" title="Excel 批量导入课程" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <template #title>Excel 列顺序：A 课程名称（必填）、B 课程类型（可填）、C 课程编码（唯一可空）、D 课程描述；第一行表头自动跳过</template>
      </el-alert>
      <div class="import-actions">
        <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
          :on-exceed="handleExceed"
          :show-file-list="false"
        >
          <el-button type="success" :icon="Upload">选择 Excel 文件</el-button>
        </el-upload>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">开始导入</el-button>
      </div>
      <div v-if="importFile" class="file-info">
        <el-icon><Document /></el-icon>
        <span>{{ importFile.name }}</span>
        <el-button link type="danger" size="small" @click="clearImportFile">移除</el-button>
      </div>
      <div v-if="importResult" class="import-result">
        <el-divider />
        <el-result
          :icon="importResult.failed === 0 ? 'success' : 'warning'"
          :title="`成功 ${importResult.success} 门，失败 ${importResult.failed} 门`"
        />
        <div v-if="importResult.failedReasons && importResult.failedReasons.length" class="failed-list">
          <el-table :data="importResult.failedReasons.slice(0, 10).map(r => ({ reason: r }))" border size="small" max-height="220">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="reason" label="失败原因" />
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- 一键分配任课教师对话框（仅管理员） -->
    <el-dialog v-model="assignVisible" title="一键分配任课教师" width="540px">
      <el-alert
        :type="teacherOptions.length < 2 ? 'warning' : 'info'"
        :closable="false"
        style="margin-bottom: 14px"
      >
        <template #title>
          <template v-if="teacherOptions.length === 0">
            当前没有可用的教师账号，请先到「账号管理」添加教师
          </template>
          <template v-else>
            将从全部 {{ teacherOptions.length }} 位启用教师中随机分配
            <b>2 ~ 3 名</b> 任课教师到每门课程
            <span v-if="teacherOptions.length < 2">（教师不足 2 人，将按实际人数分配）</span>
          </template>
        </template>
      </el-alert>

      <el-form label-width="90px">
        <el-form-item label="分配范围">
          <el-radio-group v-model="assignForm.scope">
            <el-radio value="all">全部课程（{{ tableData.length }} 门）</el-radio>
            <el-radio value="selected" :disabled="selectedIds.size === 0">
              已勾选课程（{{ selectedIds.size }} 门）
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理方式">
          <el-radio-group v-model="assignForm.mode">
            <el-radio value="skip">仅补未绑定教师的课程</el-radio>
            <el-radio value="all">全部重新分配（覆盖已有教师）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="教师人数">
          <el-tag type="warning" effect="plain">2 ~ 3 人 / 门</el-tag>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="assigning"
          :disabled="teacherOptions.length === 0"
          @click="submitAssign"
        >开始分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, View, Search, RefreshLeft, Upload, Download, Document, UserFilled, Close } from '@element-plus/icons-vue'
import { getCourseList, createCourse, updateCourse, deleteCourse, getCourseDetail, importCoursesExcel, batchDeleteCourses, assignCourseTeachers, uploadCourseCover } from '@/api/course'
import { getUserListByRole } from '@/api/sysUser'
import { useAuthStore } from '@/stores/auth'
// 课程卡片与「学生端加入课程」「教师端我的课程」共用同一套视觉
import CourseCard from '@/components/CourseCard.vue'

const authStore = useAuthStore()
const router = useRouter()
const isAdmin = computed(() => authStore.userInfo?.role === 2)

const loading = ref(false)
const tableData = ref([])
const teacherOptions = ref([])
const courseTypes = ['专业课', '公共课', '选修课']

// 勾选状态：卡片视图由卡片左上角勾选框维护，Set 与分页无关，因此勾选天然跨页保留
const selectedIds = ref(new Set())

/** 清空全部勾选（退出勾选模式 / 批量删除完成后调用） */
function clearSelection() {
  selectedIds.value = new Set()
}

/** 勾选 / 取消勾选单张课程卡片：赋新 Set 触发视图更新 */
function toggleSelect(row) {
  const next = new Set(selectedIds.value)
  if (next.has(row.id)) {
    next.delete(row.id)
  } else {
    next.add(row.id)
  }
  selectedIds.value = next
}

/** 是否处于批量删除的勾选模式：默认不显示勾选框，点「批量删除」后才进入 */
const batchMode = ref(false)

/** 进入批量删除勾选模式（清掉上一次的勾选，保证是全新一批操作） */
function enterBatchMode() {
  batchMode.value = true
  clearSelection()
  ElMessage.info('请勾选需要删除的课程，支持跨页勾选')
}

/** 退出批量删除勾选模式 */
function exitBatchMode() {
  batchMode.value = false
  clearSelection()
  ElMessage.info('已退出批量删除')
}

/** 当前列表里真实存在的行 id（用于过滤已删除课程的残留勾选） */
function aliveIdSet() {
  return new Set(tableData.value.map(r => r.id))
}

// 筛选条件
const query = reactive({ keyword: '' })

// 分页状态：卡片视图每页 8 门（4 列 × 2 行，避免出现半行卡片）
const pageNum = ref(1)
const pageSize = ref(8)

/** 关键字过滤后的全量数据（分页只负责展示切片） */
const filteredData = computed(() => {
  const kw = query.keyword.trim().toLowerCase()
  if (!kw) return tableData.value
  return tableData.value.filter(c =>
    c.courseName?.toLowerCase().includes(kw) ||
    (c.courseCode || '').toLowerCase().includes(kw)
  )
})

/** 当前页数据 */
const pagedData = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

/** 已勾选行（跨页，来自全部过滤结果而非仅当页） */
const selectedRows = computed(() =>
  filteredData.value.filter(r => selectedIds.value.has(r.id))
)

function handleSearch() {
  pageNum.value = 1
}
function handleReset() {
  query.keyword = ''
  pageNum.value = 1
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCourseList()
    tableData.value = res.data || []
    // 列表刷新后清掉已不存在的勾选，避免删除 / 导入覆盖后计数残留
    const alive = aliveIdSet()
    Array.from(selectedIds.value).forEach(id => {
      if (!alive.has(id)) selectedIds.value.delete(id)
    })
    // 若当前页超出总页数（如删除/筛选后），自动回退到最后一页
    const maxPage = Math.max(1, Math.ceil(filteredData.value.length / pageSize.value))
    if (pageNum.value > maxPage) pageNum.value = maxPage
  } catch (err) {
    console.error('加载课程列表失败:', err)
    ElMessage.error('加载失败：' + (err.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

// 关键字变化时回到第一页（实时筛选 + 查询按钮闭环）；恢复缓存状态时不重置页码
watch(() => query.keyword, () => {
  if (!restoringState) pageNum.value = 1
})

async function loadTeachers() {
  try {
    const res = await getUserListByRole(1)
    teacherOptions.value = res.data || []
  } catch (err) {
    console.error('加载教师列表失败:', err)
  }
}

onMounted(() => {
  // 从「课程信息」页返回时恢复筛选与页码
  restoreListState()
  loadData()
  if (isAdmin.value) loadTeachers()
})

// ============ 新增/编辑（管理员） ============
const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(false)
const formRef = ref(null)
const form = reactive({
  id: null,
  courseName: '',
  courseCode: '',
  courseType: '',
  description: '',
  cover: '',
  startTime: '',
  enrollDeadline: '',
  teacherIds: [],
  // 学生端「课程详情 → 课程介绍」Tab 的 4 个扩展字段（管理端填写，可空）
  intro: '',
  learningObjectives: '',
  targetAudience: '',
  syllabus: ''
})
// 封面上传中状态（上传成功后把返回路径写入 form.cover）
const coverUploading = ref(false)
const rules = {
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  courseCode: [{ required: true, message: '请输入课程编码', trigger: 'blur' }]
}

function openCreateDialog() {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

async function openEditDialog(row) {
  editing.value = true
  resetForm()
  form.id = row.id
  form.courseName = row.courseName
  form.courseCode = row.courseCode || ''
  form.courseType = row.courseType || ''
  form.description = row.description || ''
  form.cover = row.cover || ''
  form.startTime = row.startTime || ''
  form.enrollDeadline = row.enrollDeadline || ''
  dialogVisible.value = true
  // 拉取已绑定教师ID + 课程详情页扩展字段回填
  // （列表接口不返回 intro/学习目标/适用人群/大纲，必须从详情接口取，否则编辑会把已有内容清空）
  try {
    const res = await getCourseDetail(row.id)
    const detail = res.data?.course || {}
    form.teacherIds = detail.teacherIds || []
    form.intro = detail.intro || ''
    form.learningObjectives = detail.learningObjectives || ''
    form.targetAudience = detail.targetAudience || ''
    form.syllabus = detail.syllabus || ''
  } catch (err) {
    console.error('加载课程详情失败:', err)
  }
}

function resetForm() {
  form.id = null
  form.courseName = ''
  form.courseCode = ''
  form.courseType = ''
  form.description = ''
  form.cover = ''
  form.startTime = ''
  form.enrollDeadline = ''
  form.teacherIds = []
  form.intro = ''
  form.learningObjectives = ''
  form.targetAudience = ''
  form.syllabus = ''
  coverUploading.value = false
  formRef.value?.clearValidate?.()
}

/**
 * 课程封面：选中图片后立即上传到后端（/api/course/cover），
 * 返回的访问路径先暂存在表单里，点「确定」保存课程时才真正写库。
 */
async function handleCoverChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  coverUploading.value = true
  try {
    const res = await uploadCourseCover(raw)
    form.cover = res.data || ''
    ElMessage.success('封面已上传，保存后生效')
  } catch (e) {
    console.error('封面上传失败:', e)
  } finally {
    coverUploading.value = false
  }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        courseName: form.courseName.trim(),
        courseCode: form.courseCode.trim(),
        courseType: form.courseType || null,
        description: form.description ? form.description.trim() : null,
        // 封面：留空传空串（后端按"显式清空"处理），已回填时原样提交
        cover: form.cover || '',
        startTime: form.startTime || null,
        enrollDeadline: form.enrollDeadline || null,
        // 课程详情页扩展字段（留空传 null，后端按空保存）
        intro: form.intro ? form.intro.trim() : null,
        learningObjectives: form.learningObjectives ? form.learningObjectives.trim() : null,
        targetAudience: form.targetAudience ? form.targetAudience.trim() : null,
        syllabus: form.syllabus ? form.syllabus.trim() : null,
        teacherIds: form.teacherIds
      }
      if (editing.value) {
        await updateCourse(form.id, payload)
        ElMessage.success('课程更新成功')
      } else {
        await createCourse(payload)
        ElMessage.success('课程创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {
      console.error('保存课程失败:', e)
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除课程「${row.courseName}」吗？若该课程已被试题/试卷/考试引用将无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', distinguishCancelAndClose: true }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    // 失败原因自行处理（errorTip:false 抑制全局红提示，避免与引导弹窗重复）
    await deleteCourse(row.id, { errorTip: false })
    // 若被删卡片处于勾选态，先取消勾选，避免残留计数
    if (selectedIds.value.has(row.id)) toggleSelect(row)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    const msg = e?.res?.msg || e?.message || ''
    if (/知识点/.test(msg)) {
      // 仅剩知识点引用：弹窗提示数量并提供「一键清理并删除」
      offerForceDeleteKnowledge(row)
    } else {
      ElMessage.error(msg || '删除失败')
      loadData()
    }
  }
}

/** 删除失败原因为「仅剩知识点引用」时：展示知识点数量，提供一键清理入口 */
async function offerForceDeleteKnowledge(row) {
  let knowledgeCount = 0
  try {
    const res = await getCourseDetail(row.id)
    knowledgeCount = res.data?.knowledgeCount ?? 0
  } catch { /* 数量取不到按 0 展示，不影响一键清理 */ }
  try {
    await ElMessageBox.confirm(
      h('div', null, [
        h('p', { style: 'margin:0 0 8px' },
          `课程「${row.courseName}」下仍有 ${knowledgeCount} 个知识点。建议先到「课程管理 → 知识点管理」中删除或迁移这些知识点，避免题库内试题绑定悬空。`),
        h('p', { style: 'margin:0;color:#e6a23c;font-size:12px' },
          '点击「一键清理并删除」将强制删除该课程及其全部知识点，操作不可恢复。')
      ]),
      '课程存在知识点引用',
      { type: 'warning', confirmButtonText: '一键清理并删除', cancelButtonText: '取消', distinguishCancelAndClose: true }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteCourse(row.id, { params: { force: true } })
    if (selectedIds.value.has(row.id)) toggleSelect(row)
    ElMessage.success(knowledgeCount > 0 ? `已清理 ${knowledgeCount} 个知识点并删除课程` : '课程删除成功')
    loadData()
  } catch (e) {
    ElMessage.error(e?.res?.msg || e?.message || '删除失败')
    loadData()
  }
}

async function handleBatchDelete() {
  const ids = Array.from(selectedIds.value)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(
      h('div', null, [
        h('p', { style: 'margin:0 0 8px' }, `确定批量删除选中的 ${ids.length} 门课程吗？已被试题/试卷/考试引用的课程将自动跳过。`),
        h('p', { style: 'margin:0;color:#e6a23c;font-size:12px' }, '删除不可恢复')
      ]),
      '批量删除确认',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消', distinguishCancelAndClose: true }
    )
  } catch (e) {
    return
  }
  try {
    const res = await batchDeleteCourses(ids)
    const result = res.data || { success: 0, failed: 0, failedReasons: [] }
    const skipped = result.failed || 0
    if (skipped > 0) {
      const reasons = (result.failedReasons || []).slice(0, 8)
      ElMessageBox.alert(
        h('div', null, [
          h('p', { style: 'margin:0 0 8px' }, `成功删除 ${result.success} 门，跳过 ${skipped} 门受保护课程：`),
          h('ul', { style: 'margin:0;padding-left:18px;color:#e6a23c;font-size:12px' },
            reasons.map(r => h('li', { style: 'margin:2px 0' }, r)))
        ]),
        '删除结果',
        { type: 'warning', confirmButtonText: '知道了' }
      ).catch(() => {})
    } else {
      ElMessage.success(`批量删除成功（${result.success} 门）`)
    }
    // 删除完成自动退出勾选模式，勾选框随之隐藏
    batchMode.value = false
    clearSelection()
    loadData()
  } catch (err) {
    console.error('批量删除失败:', err)
  }
}

// ============ 一键分配任课教师（管理员） ============
const assignVisible = ref(false)
const assigning = ref(false)
const assignForm = reactive({ scope: 'all', mode: 'skip' })

function openAssignDialog() {
  // 有勾选默认作用于勾选课程，否则作用于全部课程
  assignForm.scope = selectedIds.value.size > 0 ? 'selected' : 'all'
  assignForm.mode = 'skip'
  assignVisible.value = true
}

async function submitAssign() {
  const useSelected = assignForm.scope === 'selected'
  const courseIds = useSelected ? Array.from(selectedIds.value) : null
  if (useSelected && courseIds.length === 0) {
    ElMessage.warning('请先勾选要分配的课程')
    return
  }
  const skipBound = assignForm.mode === 'skip'
  const scopeText = useSelected ? `已勾选的 ${courseIds.length} 门课程` : `全部 ${tableData.value.length} 门课程`
  try {
    await ElMessageBox.confirm(
      skipBound
        ? `将为${scopeText}中「未绑定教师」的课程随机分配 2~3 名任课教师，已绑定教师的课程保持不变。`
        : `将为${scopeText}重新随机分配 2~3 名任课教师，原有教师绑定将被覆盖。`,
      '一键分配老师',
      { type: 'warning', confirmButtonText: '确认分配', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }

  assigning.value = true
  try {
    const res = await assignCourseTeachers({
      courseIds,
      minCount: 2,
      maxCount: 3,
      skipBound
    })
    const r = res.data || {}
    const success = r.success || 0
    const skipped = r.skipped || 0
    const failed = r.failed || 0
    if (success === 0 && skipped > 0) {
      ElMessage.info(`没有需要分配的课程（${skipped} 门已绑定教师）`)
    } else if (failed > 0) {
      ElMessage.warning(`分配完成：成功 ${success} 门，跳过 ${skipped} 门，失败 ${failed} 门`)
    } else {
      ElMessage.success(
        skipped > 0
          ? `已为 ${success} 门课程随机分配 2~3 名任课教师，跳过 ${skipped} 门（已绑定）`
          : `已为 ${success} 门课程随机分配 2~3 名任课教师`
      )
    }
    assignVisible.value = false
    clearSelection()
    loadData()
  } catch (e) {
    // 拦截器已弹错误提示
  } finally {
    assigning.value = false
  }
}

// ============ 课程信息页（点击课程卡片进入，可查看并修改课程信息）============
/**
 * 列表浏览状态缓存：进入课程信息页再返回后，仍保持原来的关键字、页码与每页条数。
 * 只写 sessionStorage（关闭窗口即失效），不影响其它页面。
 */
const LIST_STATE_KEY = 'courseList:state'
// 恢复状态期间不重置页码（keyword 赋值会触发 watch，避免把页码打回第 1 页）
let restoringState = false

function saveListState() {
  try {
    sessionStorage.setItem(LIST_STATE_KEY, JSON.stringify({
      keyword: query.keyword,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }))
  } catch {
    // 隐私模式等场景写入失败不影响跳转
  }
}

function restoreListState() {
  let saved = null
  try {
    saved = JSON.parse(sessionStorage.getItem(LIST_STATE_KEY) || 'null')
  } catch {
    // 缓存损坏时按默认条件展示
  }
  if (!saved) return
  restoringState = true
  query.keyword = saved.keyword || ''
  pageSize.value = Number(saved.pageSize) || 8
  nextTick(() => {
    pageNum.value = Number(saved.pageNum) || 1
    restoringState = false
  })
}

/** 进入课程信息页：先缓存列表浏览状态，返回时可恢复 */
function goInfo(row) {
  saveListState()
  router.push({ name: 'CourseInfo', params: { courseId: row.id } })
}

/** 卡片点击：批量删除的勾选模式下不跳转，避免点空白处误入详情页 */
function handleCardClick(row) {
  if (batchMode.value) return
  goInfo(row)
}

// ============ Excel 导入 ============
const importVisible = ref(false)
const importFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

function openImportDialog() {
  importFile.value = null
  importResult.value = null
  importVisible.value = true
}

function handleFileChange(file) {
  importFile.value = file.raw
  importResult.value = null
}

function handleExceed() {
  ElMessage.warning('每次只能上传一个文件，请先移除已选文件')
}

function clearImportFile() {
  importFile.value = null
  importResult.value = null
  uploadRef.value?.clearFiles?.()
}

function downloadTemplate() {
  const content = '﻿课程名称,课程类型,课程编码,课程描述\n数据结构,专业课,CS202,计算机专业核心课程\n操作系统,专业课,CS301,操作系统原理与实践'
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '课程导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('模板已下载，用 Excel 编辑后另存为 .xlsx 再上传')
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importing.value = true
  importResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    const res = await importCoursesExcel(formData)
    importResult.value = res.data
    if (importResult.value.failed === 0) {
      ElMessage.success(`导入成功（${importResult.value.success} 门）`)
    } else {
      ElMessage.warning(`成功 ${importResult.value.success} 门，失败 ${importResult.value.failed} 门`)
    }
    loadData()
  } catch (err) {
    ElMessage.error(err?.message || '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.course-list {
  padding: 0;
}

.filter-card {
  margin-bottom: 12px;
}
.filter-card :deep(.el-card__body) {
  padding: 14px 18px 0;
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.table-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toolbar-actions {
  display: inline-flex;
  gap: 10px;
}

.total-info {
  color: #909399;
  font-size: 14px;
}
/* 批量删除勾选模式下的实时提示 */
.batch-hint {
  color: var(--el-color-danger);
}
.total-info + .total-info {
  margin-left: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 卡片网格容器：加载中给个最小高度，避免内容区高度抖动 */
.course-grid-wrap {
  min-height: 200px;
}

.import-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 12px 0;
}
.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #f0f9ff;
  border-radius: 8px;
  border: 1px solid #bae6fd;
  color: #0369a1;
}
.import-result {
  margin-top: 8px;
}
.import-result :deep(.el-result) {
  padding: 12px 0;
}
.failed-list {
  margin-top: 8px;
}

/* ============ 课程封面：表单预览 / 详情大图（列表封面由 CourseCard 组件渲染） ============ */
.cover-field {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.cover-preview {
  flex: none;
  width: 132px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  background: #f7f8fa;
}
.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-preview__tip {
  font-size: 12px;
  color: #c0c4cc;
}
.cover-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.cover-actions :deep(.el-upload) {
  display: inline-block;
}
.cover-tip {
  flex-basis: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}
</style>
