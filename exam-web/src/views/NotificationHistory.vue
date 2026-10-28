<template>
  <div class="notice-history">
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true">
        <el-form-item label="搜索标题">
          <el-input
            v-model="keyword"
            placeholder="输入通知标题关键字"
            clearable
            style="width: 240px"
            @keyup.enter="loadList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="listData" stripe border v-loading="loading" :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
        <!-- 序号列：第一列由表格自动生成，跨页连续编号（第 2 页第 1 行 = 11） -->
        <el-table-column type="index" label="序号" width="60" align="center" :index="indexMethod" />
        <!-- 标题：不再追加「管理员发布」标签，发送人身份统一在「发送人」列体现 -->
        <el-table-column label="标题" prop="title" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="title-text">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="接收范围" prop="receiveRangeText" min-width="220">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="rangeTagType(row.receiveType)"
              :title="rangeText(row)"
              class="range-tag"
            >
              {{ rangeText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接收人数" prop="receiveCount" width="100" align="center">
          <template #default="{ row }">
            {{ row.receiveCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="确认情况" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="(row.confirmedCount ?? 0) > 0"
              type="success"
              effect="plain"
              size="small"
            >已确认 {{ row.confirmedCount }}/{{ row.receiveCount ?? 0 }}</el-tag>
            <el-tag v-else type="info" effect="plain" size="small">待确认 0/{{ row.receiveCount ?? 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发送人" width="120" align="center">
          <template #default="{ row }">
            <!-- 考试自动通知 = 教师发布考试后系统自动发出，发送人统一显示「系统」；
                 发布老师仅作归属人，鼠标悬停可见；课程类等系统通知后端发送人本就为「系统」 -->
            <span :title="senderTip(row)">{{ senderName(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" prop="sendTime" width="180" align="center" />
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDetail(row)">
              查看详情
            </el-button>
            <el-button v-if="canDelete(row)" type="warning" link size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-popconfirm
              v-if="canDelete(row)"
              title="确定删除这条通知吗？学生端将无法再查看。"
              confirm-button-text="确定删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="通知详情"
      width="600px"
      destroy-on-close
    >
      <div v-if="detailData" class="detail-content">
        <div class="detail-row">
          <span class="detail-label">通知标题：</span>
          <span class="detail-title">{{ detailData.title }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">接收范围：</span>
          <el-tag size="small" :type="rangeTagType(detailData.receiveType)">
            {{ rangeText(detailData) }}
          </el-tag>
          <span class="detail-count">接收人数：{{ detailData.receiveCount || 0 }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">发送人：</span>
          <span :title="senderTip(detailData)">{{ senderName(detailData) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">发送时间：</span>
          <span>{{ detailData.sendTime }}</span>
        </div>
        <div class="detail-divider"></div>
        <div class="detail-row-text">
          <span class="detail-label">通知内容：</span>
        </div>
        <div class="notice-content">{{ detailData.content }}</div>
      </div>
    </el-dialog>

    <!-- 编辑并重新发布弹窗 -->
    <el-dialog
      v-model="editVisible"
      title="编辑通知并重新发布"
      width="600px"
      destroy-on-close
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="保存后将重新通知全部接收人：通知时间更新为当前时间，所有接收人的已读状态将重置为未读。"
        style="margin-bottom: 16px"
      />
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="通知标题" prop="title">
          <el-input
            v-model="editForm.title"
            maxlength="100"
            show-word-limit
            clearable
            placeholder="请输入通知标题"
          />
        </el-form-item>
        <el-form-item label="通知内容" prop="content">
          <el-input
            v-model="editForm.content"
            type="textarea"
            :rows="7"
            maxlength="2000"
            show-word-limit
            placeholder="请输入通知正文内容..."
          />
        </el-form-item>
        <el-form-item label="接收范围">
          <el-tag size="small" :type="rangeTagType(editNotice?.receiveType)">
            {{ rangeText(editNotice) }}
          </el-tag>
          <span class="detail-count">接收人数：{{ editNotice?.receiveCount || 0 }}（编辑不改变接收范围）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleEditSave">
          保存并重新发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getNoticeList, getNoticeDetail, deleteNotice, updateNotice } from '@/api/notice'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

// 删除权限：管理员可删全部；教师只能删自己发的（管理员发的通知教师端只读）
function canDelete(row) {
  const myRole = authStore.userInfo?.role
  if (myRole === 2) return true
  return row.sendUserId === authStore.userInfo?.userId
}

const loading = ref(false)
const listData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

const detailVisible = ref(false)
const detailData = ref(null)

function rangeTagType(type) {
  // 5=考试自动通知，6=课程类系统自动通知，统一用灰色 info 标签
  const map = { 1: '', 2: 'success', 3: 'warning', 4: 'danger', 5: '', 6: 'info' }
  return map[type] || 'info'
}

/**
 * 接收范围文案：常规范围（1~5）后端已给出文案，直接用；
 * 课程类系统通知（6）的接收人取决于具体业务，这里按通知标题细分，避免显示「未知」。
 */
function rangeText(row) {
  if (!row) return '未知'
  if (row.receiveType === 6) {
    const title = row.title || ''
    // 退课申请被驳回 / 课程申请被驳回 → 通知提交申请的学生本人
    if (title.includes('驳回')) return '申请学生（系统自动）'
    // 新的课程加入申请 / 新的退课申请 → 通知该课程的任课教师去审核
    if (title.includes('申请')) return '任课教师（系统自动）'
    // 课程加入成功 / 退课成功 / 课程移出通知 → 通知相关学生本人
    return '相关学生（系统自动）'
  }
  return row.receiveRangeText || '未知'
}

/**
 * 发送人显示：考试自动通知（receiveType=5）是教师发布考试后系统自动生成的，
 * 按产品要求统一显示「系统」；sendUserId 仍保留发布老师，编辑/删除权限与通知归属逻辑不变。
 */
function senderName(row) {
  if (!row) return '系统'
  if (row.receiveType === 5) return '系统'
  return row.sendUserName || '系统'
}

/** 发送人悬停提示：考试自动通知标注实际发布老师 */
function senderTip(row) {
  if (row && row.receiveType === 5 && row.sendUserName) {
    return `由 ${row.sendUserName} 发布考试后自动生成`
  }
  return ''
}

/**
 * 序号列的取值：表格自增索引每页都从 1 开始，这里补上页码偏移量，
 * 让序号跨页连续（第 1 页 1~10，第 2 页从 11 起）。
 */
function indexMethod(indexInPage) {
  return (pageNum.value - 1) * pageSize.value + indexInPage + 1
}

function loadList() {
  loading.value = true
  getNoticeList({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    keyword: keyword.value
  }).then(res => {
    listData.value = res.data?.records || []
    total.value = res.data?.total || 0
  }).catch(err => {
    ElMessage.error(err?.message || '加载失败')
  }).finally(() => {
    loading.value = false
  })
}

function resetSearch() {
  keyword.value = ''
  pageNum.value = 1
  loadList()
}

async function showDetail(row) {
  try {
    const res = await getNoticeDetail(row.id)
    detailData.value = res.data
    detailVisible.value = true
  } catch (err) {
    ElMessage.error(err?.message || '获取详情失败')
  }
}

async function handleDelete(row) {
  try {
    await deleteNotice(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '删除失败')
  }
}

/* ========== 编辑并重新发布 ========== */
const editVisible = ref(false)
const editSaving = ref(false)
const editNotice = ref(null)
const editFormRef = ref(null)
const editForm = ref({ id: null, title: '', content: '' })

const editRules = {
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }]
}

function openEdit(row) {
  editNotice.value = row
  editForm.value = {
    id: row.id,
    title: row.title,
    content: row.content || ''
  }
  editVisible.value = true
}

async function handleEditSave() {
  if (!editFormRef.value) return
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  try {
    await ElMessageBox.confirm(
      '保存后将重新通知全部接收人：所有接收人的已读状态将重置为未读。确定保存并重新发布吗？',
      '重新发布确认',
      { confirmButtonText: '保存并重新发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  editSaving.value = true
  try {
    await updateNotice({
      id: editForm.value.id,
      title: editForm.value.title,
      content: editForm.value.content
    })
    ElMessage.success('编辑成功，已重新通知全部接收人')
    editVisible.value = false
    loadList()
  } catch (err) {
    ElMessage.error(err?.message || '保存失败')
  } finally {
    editSaving.value = false
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.notice-history {
  padding: 4px;
}

.mb-4 {
  margin-bottom: 16px;
}

/* 标题：省略交给表格列（show-overflow-tooltip），这里只控制字重与颜色 */
.title-text {
  font-weight: 500;
  color: #303133;
}

.range-tag {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-content {
  line-height: 1.8;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.detail-row-text {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-label {
  color: #606266;
  min-width: 80px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.detail-count {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
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
