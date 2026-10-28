<template>
  <div class="msg-page">
    <el-card shadow="never" class="msg-card">
      <template #header>
        <div class="card-header">
          <span class="page-title">通知公告</span>
          <div class="header-actions">
            <span v-if="unreadCount > 0" class="unread-tip">{{ unreadCount }} 条未读</span>
            <el-button
              type="primary"
              size="small"
              :disabled="unreadCount === 0"
              @click="handleMarkAllRead"
            >全部标为已读</el-button>
          </div>
        </div>
      </template>

      <!-- 消息查询 + 类型筛选 -->
      <div class="msg-filter-bar">
        <el-input
          v-model="searchInput"
          placeholder="输入消息标题关键字查询"
          clearable
          class="search-input"
          @keyup.enter="applySearch"
          @clear="applySearch"
        />
        <el-button type="primary" @click="applySearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <el-tabs v-model="activeTab" class="msg-tabs" @tab-change="handleTabChange">
        <el-tab-pane name="all">
          <template #label>
            全部<span class="tab-count">（{{ countOf('all') }}）</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="exam">
          <template #label>
            考试通知<span class="tab-count">（{{ countOf('exam') }}）</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="normal">
          <template #label>
            非考试通知<span class="tab-count">（{{ countOf('normal') }}）</span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <div v-loading="loading" class="msg-list">
        <template v-if="pagedList.length > 0">
          <div
            v-for="item in pagedList"
            :key="item.id"
            class="msg-item"
            :class="{ unread: item.isRead !== 1, topped: item.isTop === 1 }"
            @click="handleClickItem(item)"
          >
            <div class="msg-item-main">
              <div class="msg-item-header">
                <span class="msg-title">
                  <span v-if="item.isRead !== 1" class="unread-dot"></span>
                  <el-tag v-if="item.isTop === 1" type="warning" size="small" effect="dark" class="top-tag">置顶</el-tag>
                  {{ item.noticeTitle || '无标题' }}
                </span>
                <span class="msg-time">{{ formatTime(item.sendTime) }}</span>
              </div>
              <div class="msg-sender">{{ item.sendUserName || '系统' }}</div>
              <div class="msg-summary">{{ summary(item.noticeContent) }}</div>
            </div>

            <div class="msg-item-actions" @click.stop>
              <el-button
                v-if="item.isRead !== 1"
                link
                type="primary"
                size="small"
                @click="handleMarkRead(item)"
              >标为已读</el-button>
              <el-button
                v-else
                link
                type="warning"
                size="small"
                @click="handleMarkUnread(item)"
              >标记未读</el-button>
              <el-button
                link
                :type="item.isTop === 1 ? 'info' : 'success'"
                size="small"
                @click="handleToggleTop(item)"
              >{{ item.isTop === 1 ? '取消置顶' : '置顶' }}</el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete(item)"
              >删除</el-button>
            </div>
          </div>
        </template>
        <el-empty
          v-else
          :description="hasFilter ? '没有匹配的消息' : '暂无消息'"
          :image-size="80"
        />
      </div>

      <div v-if="filteredList.length > 0" class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="filteredList.length"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="() => { query.pageNum = 1 }"
        />
      </div>
    </el-card>

    <!-- 消息详情弹窗 -->
    <el-dialog v-model="detailVisible" title="消息详情" width="560px" destroy-on-close>
      <div v-if="current" class="msg-detail">
        <div class="detail-row">
          <span class="detail-label">标题：</span>
          <span class="detail-title">{{ current.noticeTitle }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">发送人：</span>
          <span>{{ current.sendUserName || '系统' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">发送时间：</span>
          <span>{{ formatTime(current.sendTime) }}</span>
        </div>
        <div class="detail-divider"></div>
        <div class="detail-content">{{ current.noticeContent }}</div>

        <!-- 确认收到区域 -->
        <div class="confirm-area">
          <!-- 已确认：显示确认标签；考试通知额外保留「立即前往考试」入口 -->
          <template v-if="current.confirmStatus === 1">
            <el-tag type="success" effect="light">
              已确认收到{{ current.confirmTime ? '（' + formatTime(current.confirmTime) + '）' : '' }}
            </el-tag>
            <el-button
              v-if="current.examId != null"
              type="primary"
              plain
              @click="goExam(current)"
            >立即前往考试</el-button>
          </template>
          <template v-else-if="current.examId != null">
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUserNoticeList,
  deleteStudentNotice,
  markNoticeRead,
  markAllRead,
  getUnreadCount,
  confirmNotice,
  markStudentNoticeUnread,
  setStudentNoticeTop
} from '@/api/notice'

const router = useRouter()

const loading = ref(false)
const allList = ref([])       // 全量数据（从 /userList 一次拉取）

/* ========== 类型 tab + 标题查询（前端过滤，保序：置顶优先） ========== */
const activeTab = ref('all')          // all | exam | normal
const searchInput = ref('')           // 输入框绑定
const appliedKeyword = ref('')        // 点查询后生效的关键字

// 考试通知判定：关联了考试ID，或标题为系统自动生成的「新考试通知：」前缀（兼容历史数据）
const isExamNotice = (item) =>
  item.examId != null || (item.noticeTitle || '').startsWith('新考试通知：')

// 按当前 tab + 关键字过滤（保持置顶优先排序）
const filteredList = computed(() => {
  let list = allList.value
  if (activeTab.value === 'exam') {
    list = list.filter(isExamNotice)
  } else if (activeTab.value === 'normal') {
    list = list.filter(item => !isExamNotice(item))
  }
  const kw = appliedKeyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(item => (item.noticeTitle || '').toLowerCase().includes(kw))
  }
  return list
})

const hasFilter = computed(() => activeTab.value !== 'all' || appliedKeyword.value.trim() !== '')

// 各 tab 数量（不含关键字，只按类型统计，便于预览）
function countOf(tab) {
  if (tab === 'all') return allList.value.length
  if (tab === 'exam') return allList.value.filter(isExamNotice).length
  return allList.value.filter(item => !isExamNotice(item)).length
}

function applySearch() {
  appliedKeyword.value = searchInput.value.trim()
  query.pageNum = 1
}

function resetSearch() {
  searchInput.value = ''
  appliedKeyword.value = ''
  activeTab.value = 'all'
  query.pageNum = 1
}

// 切换 tab 回到第一页
function handleTabChange() {
  query.pageNum = 1
}
const unreadCount = ref(0)

const query = reactive({ pageNum: 1, pageSize: 10 })

// 前端分页切片（基于过滤后的列表）
const pagedList = computed(() => {
  const start = (query.pageNum - 1) * query.pageSize
  return filteredList.value.slice(start, start + query.pageSize)
})

const detailVisible = ref(false)
const current = ref(null)

async function loadList() {
  loading.value = true
  try {
    // 复用铃铛用的 getUserNoticeList 接口，一次拉取全部（最多 50 条）
    const res = await getUserNoticeList(200)
    allList.value = res.data || []
  } catch (e) {
    // 拦截器已弹错误提示
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data || 0
  } catch {}
}

// 点击消息：打开详情弹窗，同时自动标为已读
async function handleClickItem(item) {
  current.value = item
  detailVisible.value = true
  if (item.isRead !== 1) {
    await markReadSilent(item)
  }
}

/* ========== 确认收到消息 ========== */
const confirming = ref(false)

async function handleConfirm(goExamFlag) {
  const item = current.value
  if (!item || !item.noticeId) return
  confirming.value = true
  try {
    await confirmNotice(item.noticeId)
    item.confirmStatus = 1
    ElMessage.success('已确认收到')
    if (goExamFlag) goExam(item)
  } catch (e) {
    ElMessage.error(e?.message || '确认失败')
  } finally {
    confirming.value = false
  }
}

// 跳转到考试列表页
function goExam() {
  detailVisible.value = false
  router.push('/student/exams')
}

// 单条「标为已读」按钮
async function handleMarkRead(item) {
  await markReadSilent(item)
}

// 「标记未读」：已读消息改回未读，未读数 +1
async function handleMarkUnread(item) {
  try {
    await markStudentNoticeUnread(item.noticeId)
    item.isRead = 0
    unreadCount.value++
    ElMessage.success('已标记为未读')
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 「置顶 / 取消置顶」
async function handleToggleTop(item) {
  const next = item.isTop === 1 ? false : true
  try {
    await setStudentNoticeTop(item.id, next)
    item.isTop = next ? 1 : 0
    ElMessage.success(next ? '置顶成功' : '已取消置顶')
    // 重新拉取列表让排序立即生效；置顶后跳回第一页确保可见
    if (next) query.pageNum = 1
    await loadList()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 标记已读公共逻辑：调接口 → 本地标已读 → 未读数同步 -1
async function markReadSilent(item) {
  try {
    await markNoticeRead(item.noticeId)
    item.isRead = 1
    if (unreadCount.value > 0) unreadCount.value--
  } catch {}
}

// 全部标为已读
async function handleMarkAllRead() {
  try {
    await markAllRead()
    allList.value.forEach(m => { m.isRead = 1 })
    unreadCount.value = 0
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 单条删除（逻辑删除）
async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(
      `确定要删除消息「${item.noticeTitle || '无标题'}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteStudentNotice(item.id)
    ElMessage.success('已删除')
    // 从本地列表移除
    const idx = allList.value.findIndex(m => m.id === item.id)
    if (idx >= 0) allList.value.splice(idx, 1)
    // 删除后若当前页空了且不是第一页，回退一页
    if (pagedList.value.length === 0 && query.pageNum > 1) {
      query.pageNum--
    }
    // 删除的若是未读，未读数同步 -1
    if (item.isRead !== 1 && unreadCount.value > 0) unreadCount.value--
    await loadUnread()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function summary(text) {
  if (!text) return ''
  return text.length > 60 ? text.slice(0, 60) + '...' : text
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  loadList()
  loadUnread()
})
</script>

<style scoped>
.msg-page {
  width: 100%;
}

.msg-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.unread-tip {
  font-size: 12px;
  color: #f56c6c;
}

.msg-list {
  min-height: 200px;
}

.msg-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.msg-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-color: #c6e2ff;
}

.msg-item.unread {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.msg-item.unread:hover {
  background: #e0eeff;
}

/* 置顶消息卡片：左侧橙色边条提示 */
.msg-item.topped {
  border-left: 3px solid #e6a23c;
}

.top-tag {
  margin-right: 6px;
}

.msg-item-main {
  flex: 1;
  min-width: 0;
}

.msg-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.msg-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.unread-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #f56c6c;
  border-radius: 50%;
  flex-shrink: 0;
}

.msg-time {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.msg-sender {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.msg-summary {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-item-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.msg-detail {
  line-height: 1.8;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
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

.detail-content {
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

/* 消息查询栏 + 类型 tab */
.msg-filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.msg-filter-bar .search-input {
  width: 280px;
}

.msg-tabs {
  margin-bottom: 4px;
}

.msg-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.tab-count {
  color: #909399;
  font-size: 12px;
}
</style>
