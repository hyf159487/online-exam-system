<template>
  <div class="question-audit-page">
    <!-- 待审核试题 -->
    <template v-if="activeTab === 'wait'">
      <!-- 从审核历史"查看重提版本"跳转过来的提示 -->
      <el-alert v-if="resubmitTip" type="success" show-icon :closable="true" :title="resubmitTip"
        class="filter-card" @close="resubmitTip = ''" />

      <!-- 筛选条件 -->
      <el-card shadow="never" class="filter-card">
        <!-- 控件宽度交给栅格统一样式（.filter-form），保证两行左右边缘对齐 -->
        <el-form :inline="true" :model="waitQuery" class="filter-form filter-wait" @submit.prevent>
          <!-- 状态切换：待审核（默认）/ 已驳回（教师修改重提、管理员查看驳回原因） -->
          <el-form-item label="状态">
            <el-radio-group v-model="waitQuery.auditStatus" @change="handleWaitStatusChange">
              <el-radio-button :value="0">待审核</el-radio-button>
              <el-radio-button :value="2">已驳回</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <!-- 顺序按「列内上下配对」排列（4 列栅格，第 1、5 项同列，第 2、6 项同列……）：
               列1：状态 / 题型      列2：学科 / 难度
               列3：提交教师 / 提交时间   列4：题干关键词 / 查询重置 -->
          <el-form-item label="学科">
            <el-select v-model="waitQuery.courseId" placeholder="全部学科" clearable filterable>
              <el-option v-for="c in courseList" :key="c.id" :label="c.courseName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="isAdmin" label="提交教师" class="field-gap">
            <el-select v-model="waitQuery.creatorId" placeholder="全部教师" clearable filterable>
              <el-option v-for="t in teacherOptions" :key="t.id"
                :label="`${t.username}${t.realName ? '（' + t.realName + '）' : ''}`" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="题干关键词">
            <el-input v-model="waitQuery.keyword" placeholder="输入题干文字模糊检索" clearable
              @keyup.enter="handleWaitSearch" />
          </el-form-item>
          <el-form-item label="题型">
            <el-select v-model="waitQuery.questionType" placeholder="全部" clearable>
              <el-option label="单选题" :value="1" />
              <el-option label="多选题" :value="2" />
              <el-option label="判断题" :value="3" />
              <el-option label="填空题" :value="4" />
              <el-option label="简答题" :value="5" />
              <el-option label="名词解释" :value="6" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="waitQuery.difficulty" placeholder="全部" clearable>
              <el-option label="简单" :value="1" />
              <el-option label="中等" :value="2" />
              <el-option label="困难" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="提交时间" class="field-gap">
            <el-date-picker v-model="waitDateRange" type="daterange" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" :icon="Search" @click="handleWaitSearch">查询</el-button>
            <el-button :icon="Refresh" @click="resetWaitQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 待审核 / 已驳回 列表 -->
      <el-card shadow="never">
        <!-- 数据范围说明：按状态 + 角色给出对应操作指引 -->
        <div class="scope-tip">
          <el-icon><InfoFilled /></el-icon>
          <span v-if="waitQuery.auditStatus === 2 && isTeacher">
            被驳回的题目集中显示在这里（附驳回原因）：点「修改」保存后题目自动回到待审核，也可直接「重提」。
          </span>
          <span v-else-if="waitQuery.auditStatus === 2">
            这里是被驳回的题目（含驳回原因）。驳回题不可直接通过，教师修改重提后会自动回到「待审核」。
          </span>
          <span v-else-if="isTeacher">
            这里显示你提交后正在等待审核的题目；被驳回的题目请切换到「已驳回」查看并修改。
          </span>
          <span v-else>教师新提交、编辑后的题目都会进入这里，审核通过后进入公共题库。</span>
        </div>

        <!-- 批量操作栏：仅管理员 + 待审核状态可批量审核 -->
        <div v-if="isAdmin && waitQuery.auditStatus === 0" class="batch-bar">
          <el-button type="success" :disabled="!waitSelection.length"
            @click="handleBatchPass">批量通过（{{ waitSelection.length }}）</el-button>
          <el-button type="danger" :disabled="!waitSelection.length"
            @click="handleBatchReject">批量驳回（{{ waitSelection.length }}）</el-button>
          <span v-if="!waitSelection.length" class="batch-tip">请先勾选要批量审核的题目</span>
        </div>

        <el-table ref="waitTableRef" :data="waitList" v-loading="waitLoading" border stripe
          :empty-text="waitQuery.auditStatus === 2 ? '暂无已驳回试题' : '暂无待审核试题'"
          @selection-change="onWaitSelectionChange"
          :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
          <!-- 勾选框仅管理员在「待审核」状态需要（批量审核） -->
          <el-table-column v-if="isAdmin && waitQuery.auditStatus === 0" type="selection" width="45" />
          <el-table-column label="序号" width="70" align="center">
            <template #default="{ $index }">
              {{ (waitQuery.pageNum - 1) * waitQuery.pageSize + $index + 1 }}
            </template>
          </el-table-column>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="content" label="题干" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="content-cell">{{ row.content }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="courseName" label="学科" width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="course-cell">{{ row.courseName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="题型" width="90">
            <template #default="{ row }">{{ typeText(row.questionType) }}</template>
          </el-table-column>
          <el-table-column label="难度" width="80">
            <template #default="{ row }">{{ difficultyText(row.difficulty) }}</template>
          </el-table-column>
          <!-- 题库查重：待审核服务端已逐条查重，重复项标记红色且不允许勾选批量通过 -->
          <el-table-column v-if="waitQuery.auditStatus === 0" label="题库查重" width="150">
            <template #default="{ row }">
              <el-tooltip v-if="row.duplicateBankIds"
                :content="`本学科题库已有同题干已通过题目（题库 id：${row.duplicateBankIds.split(',').join('、')}）`"
                placement="top">
                <el-tag type="danger" size="small" effect="plain" style="cursor: pointer"
                  @click="handleShowDuplicate(row)">与题库重复</el-tag>
              </el-tooltip>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="审核状态" width="100">
            <template #default>
              <el-tag :type="waitQuery.auditStatus === 2 ? 'danger' : 'warning'" size="small">
                {{ waitQuery.auditStatus === 2 ? '已驳回' : '待审核' }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 已驳回视图：直接展示驳回原因，教师据此修改后重提 -->
          <el-table-column v-if="waitQuery.auditStatus === 2" prop="auditOpinion" label="驳回原因"
            min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.auditOpinion" class="opinion-cell">{{ row.auditOpinion }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="submitterName" label="提交教师" width="110" />
          <el-table-column prop="createTime" label="提交时间" width="170" />
          <el-table-column label="操作" :width="opsWidth" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button type="info" link size="small" @click="handleDetail(row)">详情</el-button>
                <!-- 管理员：仅「待审核」状态可审核（驳回题需教师重提后审核） -->
                <template v-if="isAdmin && waitQuery.auditStatus === 0">
                  <el-button type="success" link size="small" @click="handlePass(row)">通过</el-button>
                  <el-button type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
                </template>
                <!-- 教师：被驳回的题目可修改或直接重提 -->
                <template v-if="isTeacher && waitQuery.auditStatus === 2">
                  <el-button type="primary" link size="small" @click="handleEditQuestion(row)">修改</el-button>
                  <el-button type="warning" link size="small" @click="handleResubmit(row)">重提</el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="waitQuery.pageNum"
            v-model:page-size="waitQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="waitTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadWaitList"
            @current-change="loadWaitList"
          />
        </div>
      </el-card>
    </template>

    <!-- 审核历史 -->
    <template v-else>
      <!-- 统计小面板：可点击卡片快速筛选 -->
      <div class="stats-panel">
        <div class="stat-card" :class="{ active: !historyQuery.auditStatus }" @click="filterByStat(null)">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">总审核条数</div>
        </div>
        <div class="stat-card pass" :class="{ active: historyQuery.auditStatus === 1 }" @click="filterByStat(1)">
          <div class="stat-value">{{ stats.passCount }}</div>
          <div class="stat-label">审核通过</div>
        </div>
        <div class="stat-card reject" :class="{ active: historyQuery.auditStatus === 2 }" @click="filterByStat(2)">
          <div class="stat-value">{{ stats.rejectCount }}</div>
          <div class="stat-label">审核驳回</div>
        </div>
        <div class="stat-card resubmit" :class="{ active: historyQuery.auditStatus === 3 }" @click="filterByStat(3)">
          <div class="stat-value">{{ stats.resubmitCount }}</div>
          <div class="stat-label">重新提交</div>
        </div>
        <div class="stat-tip">点击卡片可快速筛选对应记录</div>
        <el-button class="stat-export" type="success" plain :icon="Download"
          :loading="exportLoading" @click="handleExport">导出Excel</el-button>
      </div>

      <!-- 筛选条件 -->
      <el-card shadow="never" class="filter-card">
        <el-form :inline="true" :model="historyQuery" class="filter-form filter-history" @submit.prevent>
          <el-form-item label="题干关键词">
            <el-input v-model="historyQuery.keyword" placeholder="输入题干文字模糊检索" clearable
              @keyup.enter="handleHistorySearch" />
          </el-form-item>
          <el-form-item label="审核结果">
            <el-select v-model="historyQuery.auditStatus" placeholder="全部" clearable>
              <el-option label="已通过" :value="1" />
              <el-option label="已驳回" :value="2" />
              <el-option label="重新提交" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="题型">
            <el-select v-model="historyQuery.questionType" placeholder="全部" clearable>
              <el-option label="单选题" :value="1" />
              <el-option label="多选题" :value="2" />
              <el-option label="判断题" :value="3" />
              <el-option label="填空题" :value="4" />
              <el-option label="简答题" :value="5" />
              <el-option label="名词解释" :value="6" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="historyQuery.difficulty" placeholder="全部" clearable>
              <el-option label="简单" :value="1" />
              <el-option label="中等" :value="2" />
              <el-option label="困难" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="提交教师">
            <el-select v-model="historyQuery.creatorId" placeholder="全部教师" clearable filterable>
              <el-option v-for="t in teacherOptions" :key="t.id"
                :label="`${t.username}${t.realName ? '（' + t.realName + '）' : ''}`" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="审核人">
            <el-select v-model="historyQuery.auditUserId" placeholder="全部审核人" clearable filterable>
              <el-option v-for="a in auditorOptions" :key="a.id"
                :label="`${a.username}${a.realName ? '（' + a.realName + '）' : ''}`" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="审核时间">
            <el-date-picker v-model="historyDateRange" type="daterange" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" :icon="Search" @click="handleHistorySearch">查询</el-button>
            <el-button :icon="Refresh" @click="resetHistoryQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <el-alert type="info" :closable="false" class="log-alert"
          title="审核历史为系统留痕流水记录（含教师重新提交记录），全程可追溯，仅支持查看与导出，不可修改删除。" />

        <el-table :data="historyList" v-loading="historyLoading" border stripe
          empty-text="暂无试题审核历史记录"
          :header-cell-style="{ textAlign: 'center' }" :cell-style="{ textAlign: 'center' }">
          <el-table-column label="序号" width="70" align="center">
            <template #default="{ $index }">
              {{ (historyQuery.pageNum - 1) * historyQuery.pageSize + $index + 1 }}
            </template>
          </el-table-column>
          <el-table-column prop="questionId" label="题目ID" width="80" />
          <el-table-column prop="contentSnapshot" label="题干" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="content-cell">{{ row.contentSnapshot || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="courseName" label="学科" width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="course-cell">{{ row.courseName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="题型" width="90">
            <template #default="{ row }">{{ typeText(row.questionType) }}</template>
          </el-table-column>
          <el-table-column label="难度" width="80">
            <template #default="{ row }">{{ difficultyText(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column prop="submitterName" label="提交教师" width="110" />
          <el-table-column label="审核结果" width="100">
            <template #default="{ row }">
              <el-tag :type="actionTagType(row.action)" size="small">
                {{ actionText(row.action) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="opinion" label="驳回原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.opinion" class="opinion-cell">{{ row.opinion }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="审核操作人" width="110">
            <template #default="{ row }">
              {{ row.action === 3 ? (row.submitterName || '-') : (row.auditorName || '-') }}
            </template>
          </el-table-column>
          <el-table-column prop="auditTime" label="审核时间" width="170" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <div class="ops">
                <el-button type="info" link size="small" @click="handleDetail(row)">详情</el-button>
                <!-- 跳转题库：仅审核通过记录可用，携带试题ID定位 -->
                <el-button type="primary" link size="small" :disabled="row.action !== 1"
                  @click="handleGotoBank(row)">跳转题库</el-button>
                <!-- 查看重提版本：仅驳回记录可用，跳待审核页按教师筛选 -->
                <el-button type="warning" link size="small" :disabled="row.action !== 2"
                  @click="handleViewResubmit(row)">查看重提版本</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="historyQuery.pageNum"
            v-model:page-size="historyQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="historyTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadHistoryList"
            @current-change="loadHistoryList"
          />
        </div>
      </el-card>
    </template>

    <!-- 试题详情弹窗 -->
    <el-dialog v-model="detailVisible" title="试题详情" width="680px">
      <!-- 待审核页连续审核的进度条 -->
      <div v-if="detailData && auditProgress.total > 0 && detailData.auditStatus === 0" class="audit-progress">
        <el-progress
          :percentage="Math.round((auditProgress.current / auditProgress.total) * 100)"
          :stroke-width="6"
          :show-text="false"
          style="flex:1;margin-right:12px"
        />
        <span class="audit-progress-text">
          第 <b>{{ auditProgress.current }}</b> / {{ auditProgress.total }} 题
        </span>
      </div>
      <div v-if="detailData" class="detail-content" v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="题目ID">{{ detailData.id }}</el-descriptions-item>
          <el-descriptions-item label="当前审核状态">
            <el-tag :type="statusTagType(detailData.auditStatus)" size="small">
              {{ statusText(detailData.auditStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="题型">{{ typeText(detailData.questionType) }}</el-descriptions-item>
          <el-descriptions-item label="学科">{{ detailData.courseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ difficultyText(detailData.difficulty) }}</el-descriptions-item>
          <el-descriptions-item label="分值">{{ detailData.score }}</el-descriptions-item>
          <el-descriptions-item label="提交教师">{{ detailData.submitterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detailData.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核操作人">{{ detailData.auditorName || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.auditTime" label="审核时间">
            {{ detailData.auditTime }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-section">
          <div class="section-title">题干</div>
          <div class="section-body">{{ detailData.content }}</div>
        </div>
        <div v-if="detailData.options" class="detail-section">
          <div class="section-title">选项</div>
          <div class="section-body">
            <div v-for="(opt, idx) in parseOptions(detailData.options)" :key="idx">
              {{ letter(idx) }}. {{ opt }}
            </div>
          </div>
        </div>
        <div class="detail-section">
          <div class="section-title">参考答案</div>
          <div class="section-body">{{ detailData.answer || '-' }}</div>
        </div>
        <div v-if="detailData.analysis" class="detail-section">
          <div class="section-title">解析</div>
          <div class="section-body">{{ detailData.analysis }}</div>
        </div>
        <div v-if="detailData.auditStatus === 2 && detailData.auditOpinion" class="detail-section reject">
          <div class="section-title">当前驳回原因</div>
          <div class="section-body">{{ detailData.auditOpinion }}</div>
        </div>

        <!-- 审核流水时间线：多次提交多次审核全程可追溯 -->
        <div v-if="detailLogs.length" class="detail-section">
          <div class="section-title">审核流水（{{ detailLogs.length }} 条，全程留痕）</div>
          <div class="section-body timeline-body">
            <el-timeline>
              <el-timeline-item v-for="log in detailLogs" :key="log.id"
                :type="log.action === 1 ? 'success' : log.action === 2 ? 'danger' : 'primary'"
                :timestamp="log.auditTime" placement="top">
                <div class="timeline-item">
                  <el-tag :type="actionTagType(log.action)" size="small">{{ actionText(log.action) }}</el-tag>
                  <span class="timeline-actor">
                    {{ log.action === 3 ? (log.submitterName || '教师') : (log.auditorName || '管理员') }}
                  </span>
                </div>
                <div v-if="log.action === 2 && log.opinion" class="timeline-opinion">
                  驳回原因：{{ log.opinion }}
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>
      </div>
      <!-- 弹窗底部操作：待审核可直接审核；已通过可跳转题库定位 -->
      <template #footer>
        <!-- 管理员：待审核题目可直接审核 -->
        <div v-if="detailData && detailData.auditStatus === 0 && isAdmin" class="detail-footer">
          <el-button @click="detailVisible = false">
            {{ auditProgress.total > 0 ? '结束审核' : '关闭' }}
          </el-button>
          <el-button type="danger" @click="handleReject(detailData)">驳回</el-button>
          <el-button type="success" @click="handlePass(detailData)">通过</el-button>
        </div>
        <!-- 教师：被驳回的题目可修改后重提或直接重提 -->
        <div v-else-if="detailData && detailData.auditStatus === 2 && isTeacher" class="detail-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button type="warning" @click="handleResubmit(detailData)">直接重提</el-button>
          <el-button type="primary" @click="handleEditQuestion(detailData)">修改并重提</el-button>
        </div>
        <div v-else-if="detailData && detailData.auditStatus === 1" class="detail-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button type="primary" @click="goQuestionBank">前往题库查看</el-button>
        </div>
        <div v-else class="detail-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 题目编辑弹窗（复用「我的试题」的编辑表单）：教师修改被驳回的题目，
         保存后后端会把审核状态重置为待审核，题目自动回到「待审核」列表 -->
    <QuestionFormDialog
      v-model="editVisible"
      :question="editQuestion"
      @success="handleEditSuccess"
    />

    <!-- 驳回弹窗（单条/批量共用） -->
    <el-dialog v-model="rejectVisible" :title="rejectTitle" width="480px"
      :close-on-click-modal="false" :close-on-press-escape="false">
      <div class="reject-target">
        <template v-if="rejectForm.ids.length === 1">
          题目ID：<b>{{ rejectForm.ids[0] }}</b>
        </template>
        <template v-else>
          已勾选 <b>{{ rejectForm.ids.length }}</b> 道题目，驳回后教师可修改后重新提交审核
        </template>
      </div>
      <el-form :model="rejectForm" label-width="80px" style="margin-top:12px">
        <el-form-item label="驳回原因" required>
          <div class="quick-reasons">
            <el-tag v-for="r in quickReasons" :key="r" class="quick-reason" type="info"
              effect="plain" @click="rejectForm.opinion = r">{{ r }}</el-tag>
          </div>
          <el-input v-model="rejectForm.opinion" type="textarea" :rows="4"
            placeholder="请填写驳回原因，告知教师需要修改的地方（必填）" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectLoading" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, InfoFilled } from '@element-plus/icons-vue'
import {
  getAuditWaitList, getAuditHistoryList, auditPass, auditReject,
  auditBatchPass, auditBatchReject, exportAuditHistory, getAuditDetail,
  getUserListByRole, getAuditStats, getAuditLogs, resubmitQuestion
} from '@/api/audit'
import { getCourseList } from '@/api/course'
import { useAuthStore } from '@/stores/auth'
import QuestionFormDialog from './QuestionFormDialog.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref('wait')

/**
 * 角色差异（本页管理员与教师共用）：
 *   管理员(role=2)：审核待办 + 审核历史/导出，「待审核」状态可勾选批量通过/驳回，已驳回题只读；
 *   教师(role=1)  ：只看自己提交的题目，用于查看审核进度，并在「已驳回」中修改/重提；
 *                  不展示提交教师筛选、批量审核与审核历史（菜单也未授权）。
 */
const isAdmin = computed(() => authStore.userInfo?.role === 2)
const isTeacher = computed(() => authStore.userInfo?.role === 1)

// ===== 下拉数据 =====
const teacherOptions = ref([])
const auditorOptions = ref([])
const courseList = ref([])

// ===== 待审核 / 已驳回 =====
const waitLoading = ref(false)
const waitList = ref([])
const waitTotal = ref(0)
const waitQuery = reactive({
  pageNum: 1, pageSize: 10,
  // 0=待审核（默认） 2=已驳回；该字段既是筛选条件也是后端数据范围的一部分
  auditStatus: 0,
  questionType: null, difficulty: null,
  courseId: null, creatorId: null, keyword: ''
})
const waitDateRange = ref(null)
const waitSelection = ref([])
const waitTableRef = ref(null)
/** 从审核历史"查看重提版本"跳转过来的提示文案 */
const resubmitTip = ref('')

/** 操作列宽度：管理员待审核时有 详情/通过/驳回 三个按钮，其余场景两个以内 */
const opsWidth = computed(() => (isAdmin.value && waitQuery.auditStatus === 0 ? 200 : 170))

// ===== 教师修改被驳回题目（复用题库编辑表单） =====
const editVisible = ref(false)
const editQuestion = ref(null)

// ===== 审核统计（卡片） =====
const stats = reactive({ total: 0, passCount: 0, rejectCount: 0, resubmitCount: 0 })

// ===== 审核历史 =====
const historyLoading = ref(false)
const historyList = ref([])
const historyTotal = ref(0)
const historyQuery = reactive({
  pageNum: 1, pageSize: 10,
  auditStatus: null, questionType: null, difficulty: null,
  creatorId: null, auditUserId: null, keyword: ''
})
const historyDateRange = ref(null)
const exportLoading = ref(false)

// ===== 详情弹窗 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)
const detailLogs = ref([])
/** 详情弹窗的审核进度：仅在「待审核页 + 审核连续切换」场景下展示 */
const auditProgress = reactive({ current: 0, total: 0 })

// ===== 驳回弹窗（单条/批量共用） =====
const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectForm = reactive({ ids: [], opinion: '' })
const quickReasons = ['题干表述不严谨', '答案错误', '格式有误', '选项有歧义', '难度设置不合理', '与已有题目重复']
/** 驳回是否从「详情页单题操作」发起（成功后要自动切下一题，不是就关闭弹窗） */
const rejectFromDetail = ref(false)

const rejectTitle = computed(() =>
  rejectForm.ids.length === 1 ? '审核驳回' : `批量驳回（${rejectForm.ids.length} 道题目）`)

onMounted(() => {
  loadCourses()
  loadTeachers()
  loadAuditors()
  const wantsHistory = route.path.includes('/audit/history')
  if (wantsHistory && !isAdmin.value) {
    // 审核历史（统计/导出）仅管理员，菜单也未授权教师：直接输 URL 时回退到待审核视图
    router.replace('/audit/pending')
    activeTab.value = 'wait'
    loadWaitList()
    return
  }
  if (wantsHistory) {
    activeTab.value = 'history'
    loadStats()
    loadHistoryList()
  } else {
    applyPendingRouteQuery()
    activeTab.value = 'wait'
    loadWaitList()
  }
})

watch(activeTab, (val) => {
  if (val === 'history') {
    loadStats()
    loadHistoryList()
  } else {
    loadWaitList()
  }
})

watch(() => route.path, (newPath) => {
  if (newPath.includes('/audit/history')) {
    // 审核历史仅管理员；教师端直接改地址时保持待审核视图，不请求管理员接口
    if (!isAdmin.value) return
    activeTab.value = 'history'
  } else {
    // 从审核历史"查看重提版本"跳回待审核页：先应用路由筛选参数再加载
    applyPendingRouteQuery()
    activeTab.value = 'wait'
  }
})

/**
 * 待审核页路由参数应用：审核历史"查看重提版本"跳转携带 creatorId，
 * 自动按提交教师筛选重新提交的待审核试题
 */
function applyPendingRouteQuery() {
  if (route.query.creatorId) {
    waitQuery.creatorId = Number(route.query.creatorId)
    const t = teacherOptions.value.find(item => item.id === waitQuery.creatorId)
    resubmitTip.value = t
      ? `正在查看教师「${t.username}${t.realName ? '（' + t.realName + '）' : ''}」重新提交的待审核试题，可接续审核`
      : '正在查看重新提交的待审核试题，可接续审核'
  } else {
    resubmitTip.value = ''
  }
}

async function loadCourses() {
  try {
    const res = await getCourseList()
    courseList.value = res.data || []
  } catch {}
}

async function loadTeachers() {
  // 提交教师筛选仅管理员使用（教师端固定只看自己，无需求）
  if (!isAdmin.value) return
  try {
    const res = await getUserListByRole(1)
    teacherOptions.value = res.data || []
    // 教师列表加载完成后，若已带重提跳转筛选，刷新提示文案中的教师姓名
    if (waitQuery.creatorId) {
      applyPendingRouteQuery()
    }
  } catch {}
}

async function loadAuditors() {
  // 审核人筛选仅审核历史（管理员）使用
  if (!isAdmin.value) return
  try {
    // 审核人是管理员/教师账号，取两类合并去重
    const [admins, teachers] = await Promise.all([
      getUserListByRole(2), getUserListByRole(1)
    ])
    const map = new Map()
    for (const u of [...(admins.data || []), ...(teachers.data || [])]) {
      map.set(u.id, u)
    }
    auditorOptions.value = [...map.values()]
  } catch {}
}

async function loadStats() {
  try {
    const res = await getAuditStats()
    Object.assign(stats, res.data || {})
  } catch {}
}

/** 时间范围转 startTime/endTime（结束日期含当天，补 23:59:59） */
function rangeParams(range) {
  if (!range || range.length !== 2) return {}
  return { startTime: `${range[0]} 00:00:00`, endTime: `${range[1]} 23:59:59` }
}

function buildWaitParams() {
  return {
    ...waitQuery,
    keyword: waitQuery.keyword?.trim() || undefined,
    ...rangeParams(waitDateRange.value)
  }
}

async function loadWaitList() {
  waitLoading.value = true
  try {
    const res = await getAuditWaitList(buildWaitParams())
    waitList.value = res.data.records || []
    waitTotal.value = res.data.total || 0
    waitSelection.value = []
  } catch {} finally { waitLoading.value = false }
}

function handleWaitSearch() {
  waitQuery.pageNum = 1
  loadWaitList()
}

/** 状态切换（待审核 / 已驳回）：回到第一页并清空勾选，避免残留上一次的选择 */
function handleWaitStatusChange() {
  waitQuery.pageNum = 1
  waitSelection.value = []
  loadWaitList()
}

/** 教师：打开题目编辑弹窗（重新拉完整题目，编辑需要知识点 ID 等字段） */
async function handleEditQuestion(row) {
  const id = row.id ?? row.questionId
  if (!id) return
  try {
    const res = await getAuditDetail(id)
    editQuestion.value = res.data
    editVisible.value = true
  } catch (e) {
    ElMessage.error('加载题目失败：' + (e?.message || '网络错误'))
  }
}

/**
 * 教师：修改保存成功 → 后端已把审核状态重置为待审核，
 * 自动切回「待审核」列表，方便教师确认题目已重新进入审核队列
 */
function handleEditSuccess() {
  detailVisible.value = false
  editQuestion.value = null
  waitQuery.auditStatus = 0
  waitQuery.pageNum = 1
  loadWaitList()
}

/** 教师：题目内容无需调整时直接重新提交审核 */
async function handleResubmit(row) {
  const id = row.id ?? row.questionId
  if (!id) return
  try {
    await ElMessageBox.confirm(
      '重新提交后题目会回到「待审核」，需要等待管理员再次审核。\n如果题目内容还需要调整，请改用「修改」。',
      '重新提交审核',
      { type: 'warning', confirmButtonText: '确认重提', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    const res = await resubmitQuestion(id)
    ElMessage.success(res?.msg || '已重新提交，等待审核')
    detailVisible.value = false
    waitQuery.auditStatus = 0
    waitQuery.pageNum = 1
    loadWaitList()
  } catch (e) {
    ElMessage.error('重新提交失败：' + (e?.message || '操作失败'))
  }
}

function resetWaitQuery() {
  waitQuery.questionType = null
  waitQuery.difficulty = null
  waitQuery.courseId = null
  waitQuery.creatorId = null
  waitQuery.keyword = ''
  waitDateRange.value = null
  waitQuery.pageNum = 1
  loadWaitList()
}

function onWaitSelectionChange(rows) {
  waitSelection.value = rows
}

/** 点击"与题库重复"标记：查看重复的题库题目 ID（仅提示，不跳转题库） */
function handleShowDuplicate(row) {
  const ids = (row.duplicateBankIds || '').split(',').filter(Boolean)
  if (!ids.length) return
  ElMessageBox.alert(
    `该待审题与本学科题库中 ${ids.length} 道已通过题目题干相同，题库题目 id：${ids.join('、')}。` +
    '\n\n为避免重复放行，请勿审核通过，建议驳回让教师修改后重新提交。',
    `题库已有该题（待审题目 ID ${row.id}）`,
    { type: 'warning', confirmButtonText: '知道了' }
  ).catch(() => {})
}

function buildHistoryParams() {
  return {
    ...historyQuery,
    keyword: historyQuery.keyword?.trim() || undefined,
    ...rangeParams(historyDateRange.value)
  }
}

async function loadHistoryList() {
  historyLoading.value = true
  try {
    const res = await getAuditHistoryList(buildHistoryParams())
    historyList.value = res.data.records || []
    historyTotal.value = res.data.total || 0
  } catch {} finally { historyLoading.value = false }
}

function handleHistorySearch() {
  historyQuery.pageNum = 1
  loadHistoryList()
}

function resetHistoryQuery() {
  historyQuery.auditStatus = null
  historyQuery.questionType = null
  historyQuery.difficulty = null
  historyQuery.creatorId = null
  historyQuery.auditUserId = null
  historyQuery.keyword = ''
  historyDateRange.value = null
  historyQuery.pageNum = 1
  loadHistoryList()
}

/** 统计卡片点击联动筛选（再次点击同一卡片取消筛选） */
function filterByStat(action) {
  historyQuery.auditStatus = historyQuery.auditStatus === action ? null : action
  historyQuery.pageNum = 1
  loadHistoryList()
}

async function handleExport() {
  exportLoading.value = true
  try {
    const response = await exportAuditHistory(buildHistoryParams())
    const blob = response.data
    if (blob.type && blob.type.includes('json')) {
      const reader = new FileReader()
      reader.onload = (e) => {
        try {
          const err = JSON.parse(e.target.result)
          ElMessage.error(err.msg || '导出失败')
        } catch { ElMessage.error('导出失败') }
      }
      reader.readAsText(blob)
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    // 文件名带导出时间：试题审核历史记录_20260830_153045.xlsx
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const timestamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
    a.href = url
    a.download = `试题审核历史记录_${timestamp}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (err) {
    ElMessage.error('导出失败：' + (err?.message || '网络错误'))
  } finally {
    exportLoading.value = false
  }
}

/**
 * 打开详情弹窗（待审核页传题目行 / 历史页传流水行）
 * 同时加载题目本体和该题完整审核流水
 */
async function handleDetail(row) {
  const questionId = row.questionId ?? row.id
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  detailLogs.value = []
  // 仅在待审核 Tab 中初始化进度
  if (activeTab.value === 'wait') {
    const idx = waitList.value.findIndex((r) => (r.questionId ?? r.id) === questionId)
    auditProgress.current = idx >= 0 ? idx + 1 : 0
    auditProgress.total = waitList.value.length
  } else {
    auditProgress.current = 0
    auditProgress.total = 0
  }
  try {
    const [detailRes, logsRes] = await Promise.all([
      getAuditDetail(questionId),
      getAuditLogs(questionId).catch(() => null)
    ])
    detailData.value = detailRes.data
    detailLogs.value = logsRes?.data || []
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/**
 * 在当前待审核列表中找到「当前题之后的下一道待审核题」并打开详情。
 * 如果当前页已全部审核完 → 自动翻下一页；若无下一页 → 关闭弹窗并刷新列表。
 */
async function loadNextPendingDetail(justFinishedId) {
  if (activeTab.value !== 'wait') {
    detailVisible.value = false
    return
  }
  // 1. 从列表中移除刚审核完的题目（本地先过滤，保证顺序与展示一致）
  const curId = justFinishedId
  const remaining = waitList.value.filter((r) => (r.questionId ?? r.id) !== curId)

  if (remaining.length > 0) {
    // 直接从当前页剩余题目中取下一道
    const nextRow = remaining[0]
    await _openAuditDetailByRow(nextRow, remaining.length - 1)
    return
  }

  // 2. 当前页已空，尝试翻到下一页继续
  const totalPages = Math.ceil(waitTotal.value / waitQuery.pageSize)
  if (waitQuery.pageNum < totalPages) {
    waitQuery.pageNum += 1
    try {
      await loadWaitList()
      if (waitList.value.length > 0) {
        await _openAuditDetailByRow(waitList.value[0], waitList.value.length - 1)
        return
      }
    } catch {
      /* fallthrough */
    }
  }

  // 3. 全部审核完毕
  try {
    loadWaitList()
  } finally {
    detailVisible.value = false
    ElMessage.success('本页待审核题目已全部处理完毕')
  }
}

/** 内部：用行数据打开详情，并更新进度 */
async function _openAuditDetailByRow(row, remainingCount) {
  const questionId = row.questionId ?? row.id
  detailLoading.value = true
  detailData.value = null
  detailLogs.value = []
  try {
    const [detailRes, logsRes] = await Promise.all([
      getAuditDetail(questionId),
      getAuditLogs(questionId).catch(() => null)
    ])
    detailData.value = detailRes.data
    detailLogs.value = logsRes?.data || []
    // 进度：基于（已完成 1 + 剩余 remainingCount）展示「第 N / 总计 M」
    auditProgress.total = 1 + remainingCount
    auditProgress.current = 1
  } catch (err) {
    detailVisible.value = false
    ElMessage.error('加载下一题详情失败，已关闭详情')
    console.error(err)
  } finally {
    detailLoading.value = false
  }
}

/** 跳转题库：携带试题ID，题库页自动检索定位该试题（历史操作栏/详情弹窗共用） */
function gotoBankByQuestionId(questionId) {
  if (!questionId) return
  router.push({ path: '/questions', query: { questionId } })
}

/** 通过失败且因题库重复：弹窗询问是否前往题库定位查看；命中返回 true 表示已处理 */
function showDuplicateJump(error) {
  const data = error?.res?.data
  if (data?.duplicate && Array.isArray(data?.duplicateIds) && data.duplicateIds.length) {
    const ids = data.duplicateIds.join('、')
    ElMessageBox.confirm(
      `通过失败，题库已有该题。本学科下已存在题干相同的已通过题目（id=${ids}）。\n\n是否前往题库定位查看？`,
      '通过失败，题库已有该题',
      {
        type: 'warning',
        confirmButtonText: '查看重复题目',
        cancelButtonText: '取消',
        dangerouslyUseHTMLString: false
      }
    ).then(() => {
      gotoBankByQuestionId(data.duplicateIds[0])
    }).catch(() => {})
    return true
  }
  return false
}

/** 历史操作栏：跳转题库定位该试题（仅审核通过记录可用） */
function handleGotoBank(row) {
  gotoBankByQuestionId(row.questionId)
}

/** 历史操作栏：查看教师重新提交的版本（仅驳回记录可用），跳待审核页自动按教师筛选 */
function handleViewResubmit(row) {
  if (!row.submitterId) return
  router.push({ path: '/audit/pending', query: { creatorId: row.submitterId } })
}

/** 详情弹窗底部：已通过题目前往题库定位 */
function goQuestionBank() {
  const qid = detailData.value?.id
  detailVisible.value = false
  gotoBankByQuestionId(qid)
}

/** 单条通过（列表按钮 / 详情弹窗底部共用） */
async function handlePass(row) {
  const id = row.id
  const title = row.content?.slice(0, 20) || '（无题干）'
  try {
    await ElMessageBox.confirm(
      `即将「审核通过」题目（ID：${id}）\n「${title}${row.content && row.content.length > 20 ? '...' : ''}」\n\n通过后将进入公共题库，是否确认？`,
      '✅ 审核通过确认', {
        type: 'success',
        confirmButtonText: '确认通过',
        cancelButtonText: '取消',
        dangerouslyUseHTMLString: false
      }
    )
  } catch { return }

  const fromDetail = detailVisible.value && detailData.value
    && (detailData.value.id === id || detailData.value.questionId === id)

  try {
    const res = await auditPass(id)
    // 后端返回的 msg 必须以「审核通过」开头，否则视为异常（防止 API/路由映射混淆）
    if (!res?.msg || !/^审核通过/.test(res.msg)) {
      throw new Error(`服务端返回异常：期望 "审核通过"，实际 "${res?.msg || '空消息'}"`)
    }
    if (fromDetail && activeTab.value === 'wait') {
      // 详情页 → 刷新后切下一题（连续审核）
      try { await loadWaitList() } catch { /* ignore */ }
      ElMessage.success({
        message: `✅ ID ${id} 已通过 → 自动进入下一题`,
        duration: 2200
      })
      loadNextPendingDetail(id)
      return
    }
    ElMessage.success(`✅ 审核通过（ID ${id}）`)
    detailVisible.value = false
    if (activeTab.value === 'wait') {
      loadWaitList()
    } else {
      loadHistoryList()
      loadStats()
    }
  } catch (e) {
    if (!showDuplicateJump(e)) {
      ElMessage.error({
        message: '审核通过失败：' + (e?.message || '操作失败'),
        duration: 4000
      })
      console.error('[Audit PASS error] id=', id, e)
    }
  }
}

/** 单条驳回（打开弹窗） */
function handleReject(row) {
  rejectForm.ids = [row.id]
  rejectForm.opinion = ''
  rejectFromDetail.value = !!(detailVisible.value && detailData.value
    && (detailData.value.id === row.id || detailData.value.questionId === row.id))
  rejectVisible.value = true
}

/** 批量通过：勾选中含"与题库重复"的题会先剔除（重复题不能通过、仅支持驳回）再放行其余 */
async function handleBatchPass() {
  const rows = waitSelection.value
  if (!rows.length) return
  const dupRows = rows.filter(r => r.duplicateBankIds)
  const passRows = rows.filter(r => !r.duplicateBankIds)

  // 全部都是重复题：无题可通过，引导改用批量驳回
  if (dupRows.length === rows.length) {
    const dupIds = dupRows.map(r => r.id).join('、')
    try {
      await ElMessageBox.confirm(
        `勾选的 ${rows.length} 道题目全部与题库已有题目重复（ID ${dupIds}），无法审核通过、仅支持驳回。\n\n是否打开批量驳回窗口？`,
        '无法批量通过', { type: 'warning', confirmButtonText: '批量驳回', cancelButtonText: '取消' }
      )
    } catch { return }
    handleBatchReject()
    return
  }

  let confirmMsg
  if (dupRows.length) {
    const dupIds = dupRows.map(r => r.id).join('、')
    confirmMsg = `勾选的 ${rows.length} 道题目中，有 ${dupRows.length} 道与题库已有题目重复（ID ${dupIds}），重复题不能通过、仅支持驳回。\n\n将自动跳过这些重复题，仅对剩余 ${passRows.length} 道执行批量通过，是否继续？`
  } else {
    confirmMsg = `确认将勾选的 ${rows.length} 道题目全部审核通过？通过后进入公共题库。`
  }
  try {
    await ElMessageBox.confirm(confirmMsg, '批量通过确认', { type: 'success' })
  } catch { return }

  try {
    await auditBatchPass(passRows.map(r => r.id))
    ElMessage.success(dupRows.length
      ? `已通过 ${passRows.length} 道，跳过了 ${dupRows.length} 道与题库重复的题目（可勾选后批量驳回）`
      : `已批量通过 ${passRows.length} 道题目`)
    loadWaitList()
  } catch (e) {
    if (!showDuplicateJump(e)) {
      // 失败原因已由全局请求拦截器统一红字提示，这里只打印日志，避免重复弹窗
      console.error('[Audit BatchPass error]', e)
    }
  }
}

/** 批量驳回（打开弹窗） */
function handleBatchReject() {
  rejectForm.ids = waitSelection.value.map(r => r.id)
  rejectForm.opinion = ''
  rejectFromDetail.value = false
  rejectVisible.value = true
}

/** 确认驳回（单条/批量共用） */
async function confirmReject() {
  if (!rejectForm.opinion || !rejectForm.opinion.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  rejectLoading.value = true
  const isSingleDetail = rejectForm.ids.length === 1 && rejectFromDetail.value
  const finishedId = rejectForm.ids[0]
  const isSingle = rejectForm.ids.length === 1
  try {
    let res
    if (isSingle) {
      res = await auditReject(rejectForm.ids[0], rejectForm.opinion.trim())
      // 后端 msg 必须以「已驳回」开头，防路由/接口混淆
      if (!res?.msg || !/^已驳回/.test(res.msg)) {
        throw new Error(`服务端返回异常：期望 "已驳回"，实际 "${res?.msg || '空消息'}"`)
      }
      ElMessage.success({
        message: isSingleDetail
          ? `🚫 ID ${finishedId} 已驳回 → 自动进入下一题`
          : `🚫 审核驳回（ID ${finishedId}）`,
        duration: 2200
      })
    } else {
      res = await auditBatchReject(rejectForm.ids, rejectForm.opinion.trim())
      if (!res?.msg || !/^已批量驳回/.test(res.msg)) {
        throw new Error(`服务端返回异常：期望 "已批量驳回"，实际 "${res?.msg || '空消息'}"`)
      }
      ElMessage.success(`🚫 已批量驳回 ${rejectForm.ids.length} 道题目`)
    }
    rejectVisible.value = false
    rejectForm.opinion = ''
    if (isSingleDetail && activeTab.value === 'wait') {
      // 详情弹窗内单题驳回：刷新后自动切下一题
      try { await loadWaitList() } catch { /* ignore */ }
      loadNextPendingDetail(finishedId)
    } else {
      // 列表点驳回 / 批量驳回：关闭详情 + 刷新
      detailVisible.value = false
      if (activeTab.value === 'wait') {
        loadWaitList()
      } else {
        loadHistoryList()
        loadStats()
      }
    }
  } catch (e) {
    ElMessage.error({
      message: (isSingle ? '驳回' : '批量驳回') + '失败：' + (e?.message || '操作失败'),
      duration: 4000
    })
    console.error('[Audit REJECT error] ids=', rejectForm.ids, e)
  } finally {
    rejectLoading.value = false
    rejectFromDetail.value = false
  }
}

function statusText(s) {
  return { 0: '待审核', 1: '已通过', 2: '已驳回' }[s] ?? '-'
}
function statusTagType(s) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[s] ?? 'info'
}
function actionText(a) {
  return { 1: '已通过', 2: '已驳回', 3: '重新提交' }[a] ?? '-'
}
function actionTagType(a) {
  return { 1: 'success', 2: 'danger', 3: 'primary' }[a] ?? 'info'
}
function typeText(t) {
  return { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }[t] || '未知'
}
function difficultyText(d) {
  return { 1: '简单', 2: '中等', 3: '困难', 4: '较难', 5: '困难' }[d] || '-'
}
function parseOptions(json) {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}
function letter(idx) { return String.fromCharCode(65 + idx) }
</script>

<style scoped>
.filter-card { margin-bottom: 12px; }
/* ===== 筛选区栅格对齐 =====
   统一所有标签的宽度 + 控件撑满各自列宽，使「待审核试题 / 审核历史」的筛选条件
   按 4 列栅格排列，上下两行的标签、控件左右边缘完全对齐 */
.filter-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 14px;
}
/* 待审核试题页：
   第 1 列（状态 / 题型）、第 2 列（学科 / 难度）统一收窄为 238px
   （84px 标签 + 10px 间距 + 144px 控件），四个下拉框宽度一致；
   第 3 列（提交教师 / 提交时间）控件适度收窄并通过 .field-gap 在列内右侧留白，
   与第 4 列（题干关键词 / 查询重置）之间形成明显间隔；
   第 4 列吃掉剩余空间，保证题干关键词输入框足够长 */
.filter-form.filter-wait {
  grid-template-columns: 238px 238px 400px minmax(0, 1fr);
}
/* 提交教师 / 提交时间：控件右侧预留空白，避免与「题干关键词」挨在一起 */
.filter-form .field-gap { padding-right: 44px; }
/* 审核历史页：4 列按内容分配宽度并铺满整行 */
.filter-form.filter-history {
  grid-template-columns: minmax(260px, 1.3fr) minmax(200px, 1fr) minmax(200px, 1fr) minmax(220px, 1.1fr);
}
/* 状态按钮组撑满本列内容宽（两个按钮均分），与下方题型下拉宽度严格一致 */
.filter-form :deep(.el-radio-group) {
  display: flex;
  width: 100%;
}
.filter-form :deep(.el-radio-group .el-radio-button) { flex: 1; }
.filter-form :deep(.el-radio-group .el-radio-button__inner) { width: 100%; }
.filter-form :deep(.el-form-item) {
  display: flex;
  align-items: center;
  margin: 0;
  min-width: 0;
}
/* 标签固定宽度并右对齐，保证同行、跨行控件左边缘对齐 */
.filter-form :deep(.el-form-item__label) {
  flex: 0 0 84px;
  width: 84px;
  padding-right: 10px;
  justify-content: flex-end;
  text-align: right;
}
.filter-form :deep(.el-form-item__content) {
  flex: 1 1 auto;
  min-width: 0;
  flex-wrap: nowrap;
}
/* 输入类控件统一撑满所在列（含日期范围，其默认宽度较大会导致长短不一） */
.filter-form :deep(.el-input),
.filter-form :deep(.el-select),
.filter-form :deep(.el-date-editor) {
  width: 100% !important;
}
/* 日期范围控件与同列其它控件严格等宽：
   内部两个输入允许压缩（默认有最小宽度），避免撑破栅格列导致与上下控件错位 */
.filter-form :deep(.el-date-editor) { min-width: 0; }
.filter-form :deep(.el-date-editor .el-range-input) { min-width: 0; }
.filter-form :deep(.el-date-editor .el-range-separator) { flex: 0 0 auto; padding: 0 6px; }
/* 查询 / 重置 / 导出Excel：按钮组靠右，右边缘与上方控件对齐 */
.filter-form .filter-actions :deep(.el-form-item__content) { justify-content: flex-end; }
.content-cell { font-size: 13px; }
.text-muted { color: #909399; font-size: 12px; }
.opinion-cell { color: #f56c6c; font-size: 13px; }
.pagination-wrapper {
  display: flex; justify-content: flex-end; margin-top: 16px;
}
.batch-bar {
  display: flex; align-items: center; gap: 12px; margin-bottom: 12px;
}
.batch-tip { color: #909399; font-size: 12px; }
/* 数据范围 / 操作指引提示：文案随「待审核/已驳回」状态与角色变化 */
.scope-tip {
  display: flex; align-items: flex-start; gap: 6px;
  padding: 8px 12px; margin-bottom: 12px;
  background: #f5f7fa; border-radius: 4px;
  font-size: 12px; color: #909399; line-height: 1.7;
}
.scope-tip .el-icon { flex-shrink: 0; margin-top: 2px; color: #409eff; }
.ops {
  display: flex; align-items: center; gap: 10px;
}
.ops :deep(.el-button + .el-button) { margin-left: 0; }

/* 统计小面板 */
.stats-panel {
  display: flex; align-items: center; gap: 14px; margin-bottom: 12px;
}
.stat-card {
  min-width: 130px; padding: 12px 20px; border-radius: 8px;
  background: #f5f7fa; border: 1px solid #e4e7ed;
  cursor: pointer; text-align: center; transition: all .2s;
}
.stat-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.stat-card.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.stat-card .stat-value { font-size: 24px; font-weight: bold; color: #303133; }
.stat-card .stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
.stat-card.pass .stat-value { color: var(--el-color-success); }
.stat-card.reject .stat-value { color: var(--el-color-danger); }
.stat-card.resubmit .stat-value { color: var(--el-color-primary); }
.stat-tip { color: #909399; font-size: 12px; margin-left: 4px; }
.stat-export { margin-left: auto; height: 56px; padding: 0 20px; border-radius: 8px; font-weight: 600; }

.log-alert { margin-bottom: 12px; }

.detail-content { line-height: 1.7; }
.audit-progress {
  display: flex;
  align-items: center;
  padding: 0 2px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 12px;
}
.audit-progress-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.audit-progress-text b { color: var(--el-color-primary); font-size: 14px; }
.detail-section { margin-top: 16px; }
.detail-section .section-title {
  font-weight: bold; color: #303133; margin-bottom: 6px; font-size: 13px;
}
.detail-section .section-body {
  background: #f5f7fa; padding: 10px 14px; border-radius: 4px; color: #606266;
}
.detail-section.reject .section-body {
  background: #fef0f0; color: #f56c6c;
}
.timeline-body { max-height: 260px; overflow-y: auto; }
.timeline-item { display: flex; align-items: center; gap: 8px; }
.timeline-actor { color: #606266; font-size: 13px; }
.timeline-opinion { color: #f56c6c; font-size: 13px; margin-top: 4px; }
.detail-footer {
  display: flex; justify-content: flex-end; gap: 10px;
}
.reject-target {
  background: #fdf6ec; color: #e6a23c; padding: 10px 14px; border-radius: 4px; font-size: 13px;
}
.quick-reasons {
  display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px;
}
.quick-reason { cursor: pointer; }
.quick-reason:hover { color: var(--el-color-primary); border-color: var(--el-color-primary); }
</style>
