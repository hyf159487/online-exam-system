<template>
  <div class="exam-list">
    <div class="page-title">
      <h2>我的考试</h2>
      <span class="tip">只显示已发布的考试，状态由系统时间自动判断</span>
    </div>

    <el-card shadow="never">
      <el-table :data="pagedData" v-loading="loading" stripe border class="my-exam-table">
        <el-table-column prop="examName" label="考试名称" min-width="170" show-overflow-tooltip />

        <el-table-column prop="paperName" label="试卷" min-width="170" show-overflow-tooltip />

        <el-table-column label="题数" width="80" align="center">
          <template #default="{ row }">{{ row.questionCount || '-' }}</template>
        </el-table-column>

        <el-table-column label="总分" width="80" align="center">
          <template #default="{ row }">{{ row.totalScore || '-' }}</template>
        </el-table-column>

        <el-table-column label="时长" width="90" align="center">
          <template #default="{ row }">{{ row.duration }} 分钟</template>
        </el-table-column>

        <el-table-column label="开始时间" width="200" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.startTime }}</template>
        </el-table-column>

        <el-table-column label="结束时间" width="200" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.endTime }}</template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row)" effect="light">
              {{ statusText(row) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="我的成绩" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.myRecordId !== null && row.myRecordId !== undefined">
              <strong :class="row.myScore >= row.passScore ? 'pass' : 'fail'">{{ row.myScore }}</strong>
            </span>
            <span v-else class="muted">未参加</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <!-- 进行中且未参加 → 去考试 -->
            <el-button
              v-if="canTake(row)"
              type="primary"
              size="small"
              @click="goExam(row)"
            >去考试</el-button>

            <!-- 已参加：考试结束后可直接打开这份卷子，未结束只能先看分数 -->
            <el-button
              v-else-if="row.myRecordId !== null && row.myRecordId !== undefined"
              size="small"
              @click="goGrades(row.myRecordId, row.runtimeStatus === 2)"
            >{{ row.runtimeStatus === 2 ? '查看试卷' : '查看成绩' }}</el-button>

            <!-- 未开始 -->
            <el-button v-else-if="row.runtimeStatus === 0" size="small" disabled>未开始</el-button>

            <!-- 已结束未参加 -->
            <el-button v-else size="small" disabled>已结束</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tableData.length === 0" description="暂无已发布的考试" />

      <!-- 分页 -->
      <div v-if="tableData.length" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="tableData.length"
          :page-sizes="[5, 10, 15, 20, 30, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handlePageChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyExams } from '@/api/studentExam'
import { useDefaultPageSize } from '@/composables/useDefaultPageSize'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])

// 分页：一个学生的考试条数有限，直接在前端切片，不再单独加后端分页接口
const pageNum = ref(1)
const pageSize = ref(10)

// 当前页数据
const pagedData = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return tableData.value.slice(start, start + pageSize.value)
})

// 页码 / 每页条数变化后若超出总页数，回退到最后一页
function handlePageChange() {
  const maxPage = Math.max(1, Math.ceil(tableData.value.length / pageSize.value))
  if (pageNum.value > maxPage) pageNum.value = maxPage
}

onMounted(async () => {
  try {
    pageSize.value = await useDefaultPageSize()
  } catch {}
  fetchData()
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getMyExams()
    tableData.value = res.data || []
    handlePageChange() // 数据变化后校正页码，避免停在空白页
  } catch (err) {
    console.error('加载考试列表失败:', err)
  } finally {
    loading.value = false
  }
}

// 是否可参加：已发布 + 进行中 + 未参加过
function canTake(row) {
  return row.status === 1 && row.runtimeStatus === 1 && row.myRecordId == null
}

// 状态文字
function statusText(row) {
  if (row.status === 0) return '已撤回'
  if (row.runtimeStatus === 0) return '未开始'
  if (row.runtimeStatus === 1) return '进行中'
  return '已结束'
}

// 状态标签颜色
function statusTagType(row) {
  if (row.status === 0) return 'info'
  if (row.runtimeStatus === 1) return 'success'
  return 'info'
}

function goExam(row) {
  router.push(`/student/exams/${row.id}/do`)
}

/**
 * 去成绩页
 *
 * withDetail 为 true（考试已结束）时带上 recordId，成绩页会自动展开那份卷子，
 * 省得跳过去再找一遍；考试还没结束时只跳列表看分数——试卷要等考完才放行。
 */
function goGrades(recordId, withDetail) {
  if (withDetail && recordId !== null && recordId !== undefined) {
    router.push({ path: '/student/grades', query: { recordId } })
  } else {
    router.push('/student/grades')
  }
}
</script>

<style scoped>
/* 表头与内容统一居中 */
.my-exam-table :deep(.el-table__header th.el-table__cell),
.my-exam-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}

/* 单元格内容一律单行显示（过长时由 show-overflow-tooltip 出省略号 + 悬停查看全文） */
.my-exam-table :deep(.el-table__cell) {
  white-space: nowrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.page-title h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.tip {
  font-size: 13px;
  color: #909399;
}
.pass {
  color: #67c23a;
}
.fail {
  color: #f56c6c;
}
.muted {
  color: #c0c4cc;
}
</style>
