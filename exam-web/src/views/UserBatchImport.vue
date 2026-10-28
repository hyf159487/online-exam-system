<template>
  <div class="batch-import">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">账号批量导入</span>
          <span class="card-sub">支持学生 / 教师 / 管理员账号一次性导入：按模板填好 Excel 上传，系统逐行校验后批量建号，单行出错不影响其他行</span>
        </div>
      </template>

      <!-- 主体两列布局 -->
      <div class="main-grid">
        <!-- 左侧：Excel 格式示例 + 常见问题 -->
        <div class="col-left">
          <!-- Excel 格式：直接用示例数据当参考（下载的模板与这里完全一致） -->
          <div class="section">
            <div class="section-title">Excel 格式 + 示例数据（仅供参考）</div>
            <el-table :data="sampleData" border size="small" style="width: 100%">
              <el-table-column prop="username" min-width="130" align="center" header-align="center">
                <template #header>账号/学号 <span class="req">*</span></template>
              </el-table-column>
              <el-table-column prop="realName" min-width="80" align="center" header-align="center">
                <template #header>姓名 <span class="req">*</span></template>
              </el-table-column>
              <el-table-column min-width="90" align="center" header-align="center">
                <template #header>角色 <span class="req">*</span></template>
                <template #default="{ row }">
                  <el-tag :type="roleTagType(row.role)" effect="light" size="small">
                    {{ roleText(row.role) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="className" label="班级名" min-width="100" align="center" header-align="center" />
              <el-table-column prop="phone" label="手机号" min-width="120" align="center" header-align="center" />
              <el-table-column prop="email" label="邮箱" min-width="180" align="center" header-align="center" />
            </el-table>
            <div class="section-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>按 <b>A~F 列顺序</b>填写、第一行表头保留即可（只读第一个工作表）；<span class="req">*</span> 为必填：账号 / 学号、姓名、角色（角色只能填 0 = 学生 / 1 = 教师 / 2 = 管理员）。</span>
            </div>
            <div class="section-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>示例覆盖「学生有班级 / 学生无班级 / 教师 / 管理员」4 种场景；表中<b>没有密码列</b>，导入的账号统一使用默认密码 <code>123456</code>、状态「启用」。</span>
            </div>
          </div>

          <!-- 常见问题 -->
          <div class="section">
            <div class="section-title">常见问题</div>
            <ul class="faq-list">
              <li><b>账号 / 学号必须唯一。</b>与系统已有账号重复时，该行判为失败并提示「账号「xxxx」已存在」，其余行照常导入。</li>
              <li><b>前 3 列必填：</b>账号 / 学号、姓名、角色，任一为空该行失败；<b>密码不用填</b>，导入后统一是 <code>123456</code>、状态启用，可再用「重置密码」单独改。</li>
              <li><b>角色字段只能填数字：</b>0 = 学生、1 = 教师、2 = 管理员，留空或填其它值该行失败。</li>
              <li><b>列顺序不要调整。</b>程序固定按 A~F 取列，第一行表头只被跳过、不校验文字；表头那行务必保留（否则第一行数据会被当成表头丢弃）。</li>
              <li><b>班级名要和「班级管理」里的名字完全一致</b>（含数字、不要多加空格）。对不上时<b>该行仍会导入成功</b>，只是没有班级，明细里会提示「班级不存在」；导入后可去班级管理批量调整。</li>
              <li><b>教师 / 管理员</b>班级名留空即可，填了也不会生效。</li>
              <li><b>手机号 / 邮箱选填</b>，留空即不写入，之后可让学生自己在个人信息里补。</li>
              <li><b>学号建议先把单元格设成「文本」。</b>纯数字且以 0 开头的学号，Excel 会默默去掉开头的 0。</li>
              <li><b>文件格式用 .xlsx</b>（旧版 .xls 请先用 Excel 另存为 .xlsx）。行数没有硬性上限，建议单次 200 行以内，出错时更容易逐行核对。</li>
            </ul>
          </div>
        </div>

        <!-- 右侧：操作区 + 结果 -->
        <div class="col-right">
          <div class="section">
            <div class="section-title">操作步骤</div>
            <ol class="step-list">
              <li><span class="step-num">1</span>下载模板并用 Excel 打开</li>
              <li><span class="step-num">2</span>保留表头，从第 2 行起按 A~F 列填写账号信息</li>
              <li><span class="step-num">3</span>另存为 .xlsx，点「选择 Excel 文件」上传</li>
              <li><span class="step-num">4</span>点「开始导入」，等结果和明细出来</li>
            </ol>
          </div>

          <div class="section">
            <div class="section-title">上传文件</div>
            <div class="actions">
              <el-button type="primary" :icon="Download" @click="downloadTemplate">下载模板</el-button>
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
              <el-button
                type="primary"
                :loading="importing"
                :disabled="!selectedFile"
                @click="handleImport"
              >开始导入</el-button>
            </div>

            <div class="upload-hint">
              支持 .xlsx / .xls，建议用 .xlsx（旧版 .xls 请先用 Excel 另存为 .xlsx）；一次只能传一个文件。
            </div>

            <!-- 选中文件 -->
            <div v-if="selectedFile" class="file-info">
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ selectedFile.name }}</span>
              <span class="file-size">({{ (selectedFile.size / 1024).toFixed(1) }} KB)</span>
              <el-button link type="danger" size="small" @click="clearFile">移除</el-button>
            </div>
          </div>

          <div class="section">
            <div class="section-title">为什么选择批量导入</div>
            <ul class="benefit-list">
              <li>
                <el-icon class="benefit-icon"><CircleCheckFilled /></el-icon>
                <div>
                  <div class="benefit-h">高效</div>
                  <div class="benefit-d">一次导入上百条账号，比逐个新增快几十倍</div>
                </div>
              </li>
              <li>
                <el-icon class="benefit-icon"><CircleCheckFilled /></el-icon>
                <div>
                  <div class="benefit-h">容错</div>
                  <div class="benefit-d">单行失败不影响其他行，失败原因逐行列出（账号重复、角色非法、必填为空等）</div>
                </div>
              </li>
              <li>
                <el-icon class="benefit-icon"><CircleCheckFilled /></el-icon>
                <div>
                  <div class="benefit-h">安全</div>
                  <div class="benefit-d">导入前按账号查重，已存在的账号不会被覆盖或改密</div>
                </div>
              </li>
              <li>
                <el-icon class="benefit-icon"><CircleCheckFilled /></el-icon>
                <div>
                  <div class="benefit-h">省心</div>
                  <div class="benefit-d">不用填密码，导入即用默认 123456、状态启用，后续可单独重置</div>
                </div>
              </li>
            </ul>
          </div>

          <div class="section">
            <div class="section-title">导入完成后</div>
            <ul class="next-list">
              <li>
                <el-icon><User /></el-icon>
                <a @click="goToPage('/account/admins')">前往「管理员账号」列表</a>
                <span class="next-tag">查看已导入的管理员</span>
              </li>
              <li>
                <el-icon><User /></el-icon>
                <a @click="goToPage('/account/teachers')">前往「教师账号」列表</a>
                <span class="next-tag">查看已导入的教师</span>
              </li>
              <li>
                <el-icon><User /></el-icon>
                <a @click="goToPage('/account/students')">前往「学生账号」列表</a>
                <span class="next-tag">查看 / 禁用 / 重置密码</span>
              </li>
              <li>
                <el-icon><School /></el-icon>
                <a @click="goToPage('/classes')">前往「班级管理」</a>
                <span class="next-tag">批量调整学生班级</span>
              </li>
            </ul>
          </div>

          <!-- 导入结果 -->
          <div v-if="result" class="section result-area">
            <el-divider style="margin: 4px 0 12px" />
            <el-result
              :icon="result.failed === 0 ? 'success' : 'warning'"
              :title="`成功 ${result.success} 个，失败 ${result.failed} 个`"
            />
            <div v-if="result.failedReasons && result.failedReasons.length > 0" class="failed-list">
              <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
                <template #title>结果明细（最多显示 20 条）</template>
                <div style="font-size: 12px; line-height: 1.6">
                  含各行的失败原因，也包括「班级不存在，已跳过班级分配」这类提示——后者该行其实<b>已导入成功</b>，只是没分到班级。
                </div>
              </el-alert>
              <el-table :data="failedTable" border size="small" max-height="300">
                <el-table-column type="index" label="#" width="50" align="center" />
                <el-table-column prop="reason" label="失败原因" />
              </el-table>
            </div>
            <div v-if="result.success > 0" style="margin-top: 16px; text-align: center">
              <el-button type="primary" @click="goToStudentList">前往学生账号列表查看</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Upload, Document, InfoFilled, CircleCheckFilled, User, School } from '@element-plus/icons-vue'
import { importExcelUsers } from '@/api/sysUser'

const router = useRouter()

// Excel 格式即示例数据的列顺序：账号/学号 | 姓名 | 角色 | 班级名 | 手机号 | 邮箱
// （前 3 列必填，带 * 标记；表中无密码列，导入统一使用默认密码 123456）
// 覆盖 3 种角色、含/不含班级 4 种场景的示例数据
const sampleData = [
  { username: '2300580101', realName: '张三', role: 0, className: '23005801', phone: '13800138001', email: 'zhangsan@example.com' },
  { username: '2300580102', realName: '李四', role: 0, className: '23005801', phone: '13800138002', email: '' },
  { username: 'T001', realName: '王老师', role: 1, className: '', phone: '13800138003', email: 'wang@example.com' },
  { username: 'M001', realName: '赵主任', role: 2, className: '', phone: '', email: '' }
]
const roleText = (r) => ({ 0: '学生', 1: '教师', 2: '管理员' })[r] || '-'
const roleTagType = (r) => ({ 0: 'primary', 1: 'success', 2: 'danger' })[r] || 'info'

const uploadRef = ref(null)
const selectedFile = ref(null)
const importing = ref(false)
const result = ref(null)

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleExceed() {
  ElMessage.warning('每次只能上传一个文件，请先移除已选文件')
}

function clearFile() {
  selectedFile.value = null
  result.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
}

function downloadTemplate() {
  const header = '账号/学号,姓名,角色,班级名,手机号,邮箱\n'
  const example = '2300580101,张三,0,23005801,13800138001,zhangsan@example.com\nT001,王老师,1,,13800138003,wang@example.com'
  const blob = new Blob(['\ufeff' + header + example], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '账号导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('模板已下载，请用 Excel 打开编辑后另存为 .xlsx 格式再上传')
}

async function handleImport() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importing.value = true
  result.value = null
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    const res = await importExcelUsers(formData)
    result.value = res.data
    if (result.value.failed === 0) {
      ElMessage.success(`导入成功！共 ${result.value.success} 个账号`)
    } else {
      ElMessage.warning(`成功 ${result.value.success} 个，失败 ${result.value.failed} 个，请查看下方明细`)
    }
  } catch (err) {
    ElMessage.error(err?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function goToStudentList() {
  router.push('/account/students')
}

function goToPage(path) {
  router.push(path)
}

const failedTable = computed(() => {
  if (!result.value || !result.value.failedReasons) return []
  return result.value.failedReasons.slice(0, 20).map(r => ({ reason: r }))
})
</script>

<style scoped>
.batch-import {
  width: 100%;
  margin: 0;
}
.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-title {
  font-weight: 600;
  font-size: 16px;
}
.card-sub {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

/* 主体两列：左侧内容多、右侧操作 */
.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.col-left, .col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* 必填列标记（表头里的红色 *） */
.req {
  color: #f56c6c;
  font-weight: 600;
}

.section {
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}
.section-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 14px;
  background: #409eff;
  border-radius: 2px;
  margin-right: 8px;
}
.section-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
}
.section-tip .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.faq-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
  font-size: 13px;
  color: #606266;
}
.faq-list code,
.section-tip code {
  background: #f0f3fa;
  color: #c0365b;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: Consolas, monospace;
  font-size: 12px;
}

.step-list {
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: step;
}
.step-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  font-size: 13px;
  color: #606266;
}
.step-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.upload-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #f0f9ff;
  border-radius: 6px;
  border: 1px solid #bae6fd;
  color: #0369a1;
  font-size: 13px;
}
.file-name {
  flex: 1;
  word-break: break-all;
}
.file-size {
  color: #94a3b8;
  font-size: 12px;
}

.benefit-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.benefit-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}
.benefit-icon {
  color: #67c23a;
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 1px;
}
.benefit-h {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
  margin-bottom: 2px;
}
.benefit-d {
  color: #909399;
  font-size: 12px;
}

.next-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.next-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  flex-wrap: wrap;
}
.next-list li .el-icon {
  color: #409eff;
  font-size: 14px;
}
.next-list a {
  color: #409eff;
  cursor: pointer;
  text-decoration: none;
}
.next-list a:hover {
  text-decoration: underline;
}
.next-tag {
  color: #909399;
  font-size: 12px;
  background: #f4f4f5;
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: auto;
}

.result-area {
  background: #fff;
  border-color: #dcdfe6;
}

/* 中等屏幕：单列堆叠 */
@media (max-width: 1100px) {
  .main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
