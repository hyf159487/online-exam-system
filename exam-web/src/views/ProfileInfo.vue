<template>
  <div class="profile-page">
    <el-card shadow="never" class="profile-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">个人信息</span>
          <span class="header-tip">{{ roleTip }}</span>
        </div>
      </template>

      <!-- ===== 头像区：头像 + 姓名 + 角色标签；未上传头像时用姓名首字占位 ===== -->
      <div class="avatar-block">
        <div class="avatar-wrap">
          <img v-if="form.avatar" class="avatar-img" :src="form.avatar" alt="头像" />
          <span v-else class="avatar-text">{{ avatarText }}</span>
        </div>
        <div class="avatar-info">
          <div class="avatar-name">
            <span>{{ form.realName || '未命名用户' }}</span>
            <el-tag size="small" effect="plain" type="info">{{ form.roleName || '用户' }}</el-tag>
          </div>
          <div class="avatar-sub">{{ form.role === 0 ? '学号' : '账号' }}：{{ form.username || '-' }}</div>
          <div class="avatar-actions">
            <el-upload
              class="avatar-upload"
              :show-file-list="false"
              :auto-upload="false"
              accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
              :on-change="handleAvatarChange"
            >
              <el-button size="small" :icon="Upload" :loading="avatarUploading">
                {{ form.avatar ? '更换头像' : '上传头像' }}
              </el-button>
            </el-upload>
            <span class="avatar-tip">jpg/png/gif/webp/bmp，不超过 2MB</span>
          </div>
        </div>
      </div>

      <!-- ===== 表单①：基础信息（只读）+ 联系方式（可修改） ===== -->
      <el-form
        ref="infoFormRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="profile-form"
      >
        <!-- 只读区：身份信息（置灰不可编辑，由管理员统一维护） -->
        <el-divider content-position="left">基础信息（只读）</el-divider>
        <el-form-item v-if="form.role === 0" label="学号">
          <el-input v-model="form.username" disabled />
        </el-form-item>
        <el-form-item v-else label="账号">
          <el-input v-model="form.username" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-input :model-value="form.roleName || '-'" disabled />
        </el-form-item>

        <!-- 学生专属：班级 / 年级 / 院系 -->
        <template v-if="form.role === 0">
          <el-form-item label="班级">
            <el-input v-model="form.className" placeholder="未分配班级" disabled />
          </el-form-item>
          <el-form-item label="年级">
            <el-input v-model="form.grade" placeholder="-" disabled />
          </el-form-item>
          <el-form-item label="院系">
            <el-input v-model="form.department" placeholder="-" disabled />
          </el-form-item>
        </template>

        <!-- 创建时间：只读展示（后端 create_time），让账号信息更完整 -->
        <el-form-item label="创建时间">
          <el-input :model-value="createTimeText" disabled />
        </el-form-item>

        <!-- 可编辑区：联系方式（所有角色通用） -->
        <el-divider content-position="left">联系方式（可修改）</el-divider>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="请输入 11 位手机号（可留空）"
            maxlength="11"
            clearable
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱（可留空）"
            maxlength="100"
            clearable
          />
        </el-form-item>
      </el-form>

      <!-- 底部按钮：保存 / 重置只作用于联系方式；「修改密码」是独立功能，用竖线分隔 -->
      <div class="footer-actions">
        <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
        <el-button @click="handleReset">重置</el-button>
        <span class="actions-divider" aria-hidden="true"></span>
        <el-button :icon="Key" @click="openPwdDialog">修改密码</el-button>
      </div>
    </el-card>

    <!-- =========================================================================
         修改密码弹窗（由「修改密码」入口按钮触发）
         - 独立 model（pwdForm）+ 独立 rules（pwdRules）+ 独立提交方法
         - 与联系方式互不干扰：改手机号报错不会影响密码表单，反之亦然
         ========================================================================= -->
    <el-dialog
      v-model="pwdDialogVisible"
      title="修改密码"
      width="480px"
      :close-on-click-modal="false"
      @closed="resetPwdForm"
    >
      <div class="dialog-tip">修改成功后需使用新密码重新登录，当前登录状态会自动退出。</div>
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="100px"
        class="pwd-form"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            :type="showOld ? 'text' : 'password'"
            placeholder="请输入当前正在使用的密码"
            maxlength="32"
            clearable
          >
            <template #suffix>
              <el-icon class="eye-icon" @click="showOld = !showOld">
                <View v-if="showOld" />
                <Hide v-else />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            :type="showNew ? 'text' : 'password'"
            placeholder="6-16 位字母或数字"
            maxlength="16"
            clearable
          >
            <template #suffix>
              <el-icon class="eye-icon" @click="showNew = !showNew">
                <View v-if="showNew" />
                <Hide v-else />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            :type="showConfirm ? 'text' : 'password'"
            placeholder="请再次输入新密码"
            maxlength="16"
            clearable
            @keyup.enter="handleChangePassword"
          >
            <template #suffix>
              <el-icon class="eye-icon" @click="showConfirm = !showConfirm">
                <View v-if="showConfirm" />
                <Hide v-else />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="pwdDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="handleChangePassword">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { View, Hide, Key, Upload } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/auth'
import { getProfile, updateProfile, updatePassword, uploadAvatar } from '@/api/profile'

const router = useRouter()
const authStore = useAuthStore()

const infoFormRef = ref(null)        // 本页表单：身份信息 + 联系方式
const pwdFormRef = ref(null)         // 弹窗里的修改密码表单
const pwdDialogVisible = ref(false)  // 修改密码弹窗开关（由「修改密码」入口按钮触发）
const loading = ref(false)
const saving = ref(false)
const pwdSaving = ref(false)
const avatarUploading = ref(false)   // 头像上传中

// 三个密码框的明文 / 密文切换
const showOld = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)

// 表单字段（与后端通用 profile 字段名对齐）
const form = reactive({
  username: '',
  realName: '',
  role: authStore.userInfo?.role ?? null,
  roleName: '',
  className: '',
  grade: '',
  department: '',
  phone: '',
  email: '',
  avatar: '',        // 头像可访问路径（/uploads/avatar/...），空串表示未设置
  createTime: '',    // 账号创建时间（只读展示）
  status: 1
})

// 服务器端原始快照，供「重置」还原
const snapshot = ref({ ...form })

// 顶部提示：身份字段由管理员维护；联系方式与密码可自行维护（文案统一、更精简）
const roleTip = computed(() =>
  form.role === 0
    ? '学号、姓名、班级等身份信息不可自行编辑，由管理员统一维护；手机号、邮箱支持自主修改，可点击修改密码重置登录密码。'
    : '账号、姓名、角色信息不可自行编辑，由超级管理员统一维护；手机号、邮箱支持自主修改，可点击修改密码重置登录密码。'
)

/** 头像占位文字：优先姓名首字，其次账号首字 */
const avatarText = computed(() => {
  const name = (form.realName || form.username || '用').trim()
  return name.slice(0, 1).toUpperCase()
})

/** 创建时间展示：兼容 "2026-09-13T10:00:00" / "2026-09-13 10:00:00" / 时间戳数组 */
const createTimeText = computed(() => formatDateTime(form.createTime))

function formatDateTime(value) {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = value
    const pad = (n) => String(n).padStart(2, '0')
    return `${y}-${pad(mo)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`
  }
  const raw = String(value)
  const pad = (n) => String(n).padStart(2, '0')
  // 后端可能返回时间戳（毫秒数）或 "2026-09-13 10:00:00" / ISO 字符串，这里统一成 yyyy-MM-dd HH:mm:ss
  const date = /^\d+$/.test(raw) ? new Date(Number(raw)) : new Date(raw.replace('T', ' ').replace(/-/g, '/'))
  if (!date || Number.isNaN(date.getTime())) {
    return raw.replace('T', ' ').slice(0, 19)
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/**
 * 头像：选中图片后立即上传（/api/profile/avatar），成功后把返回路径写入表单与登录态缓存，
 * 页面无需刷新即可看到新头像。前端先做一次格式/大小校验，后端也会再校验一次。
 */
async function handleAvatarChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  if (!raw.type || !raw.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (raw.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像图片不能超过 2MB')
    return
  }
  avatarUploading.value = true
  try {
    const res = await uploadAvatar(raw)
    form.avatar = res.data || ''
    if (authStore.userInfo) authStore.userInfo.avatar = form.avatar
    snapshot.value = { ...snapshot.value, avatar: form.avatar }
    ElMessage.success('头像已更新')
  } catch (e) {
    // 拦截器已弹错误提示
  } finally {
    avatarUploading.value = false
  }
}

// 手机号 + 邮箱前端校验：允许为空，填写需符合格式
const rules = {
  phone: [
    {
      validator: (rule, value, callback) => {
        const v = value === null || value === undefined ? '' : String(value).trim()
        if (!v) return callback()
        if (!/^1\d{10}$/.test(v)) return callback(new Error('请输入正确的 11 位手机号'))
        callback()
      },
      trigger: 'blur'
    }
  ],
  email: [
    {
      validator: (rule, value, callback) => {
        const v = value === null || value === undefined ? '' : String(value).trim()
        if (!v) return callback()
        if (!/^[\w.\-+]+@[\w\-]+(\.[\w\-]+)+$/.test(v)) return callback(new Error('请输入正确的邮箱'))
        callback()
      },
      trigger: 'blur'
    }
  ]
}

/* ================= 密码表单（独立于联系方式：单独 model / rules / 提交） ================= */
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 确认密码必须与新密码完全一致
function validateConfirm(rule, value, callback) {
  if (value === null || value === undefined || value === '') {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const pwdRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9]{6,16}$/,
      message: '密码长度需为 6-16 位，仅支持字母和数字',
      trigger: 'blur'
    },
    {
      // 前端先拦一道：新密码不能和原密码相同，后端 BCrypt 也会再校验一次
      validator: (rule, value, callback) => {
        if (value && value === pwdForm.oldPassword) {
          callback(new Error('新密码不能与原密码相同'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirm, trigger: 'blur' }
  ]
}

async function loadProfile() {
  loading.value = true
  try {
    const res = await getProfile()
    const d = (res && res.data) || {}
    form.username  = d.username  ?? d.studentNo ?? ''
    form.realName  = d.realName  ?? d.name      ?? ''
    form.role      = d.role      ?? authStore.userInfo?.role ?? null
    form.roleName  = d.roleName  ?? (form.role === 2 ? '管理员' : form.role === 1 ? '教师' : '学生')
    form.className = d.className ?? ''
    form.grade     = d.grade     ?? ''
    form.department= d.department?? ''
    form.phone     = d.phone     ?? ''
    form.email     = d.email     ?? ''
    form.avatar    = d.avatar    ?? ''
    form.createTime= d.createTime?? ''
    form.status    = d.status    ?? 1
    // 更新 authStore 里的姓名缓存（避免右上角下拉「用户」还是旧的）
    if (form.realName && authStore.userInfo) {
      authStore.userInfo.realName = form.realName
    }
    snapshot.value = { ...form }
  } catch (e) {
    // request 拦截器已弹错误
  } finally {
    loading.value = false
  }
}

/** 保存修改：只校验/提交联系方式（表单①），不触碰密码 */
async function handleSave() {
  if (!infoFormRef.value) return
  try {
    await infoFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    // 仅提交 phone/email 两个字段；其他字段即使误传后端也会丢弃
    await updateProfile({
      phone: form.phone?.trim() || null,
      email: form.email?.trim() || null
    })
    ElMessage.success('个人信息修改成功')
    snapshot.value = { ...form }
  } catch (e) {
    // 拦截器处理
  } finally {
    saving.value = false
  }
}

/** 重置：只把手机号、邮箱还原成上次加载/保存的值，只读身份信息不受影响 */
function handleReset() {
  form.phone = snapshot.value.phone ?? ''
  form.email = snapshot.value.email ?? ''
  infoFormRef.value?.clearValidate()
}

/** 打开「修改密码」弹窗：每次都从空白状态开始，避免残留上次输入 */
function openPwdDialog() {
  resetPwdForm()
  pwdDialogVisible.value = true
}

/** 重置密码表单（弹窗关闭时触发），同时把「小眼睛」恢复成密文状态 */
function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  showOld.value = false
  showNew.value = false
  showConfirm.value = false
  pwdFormRef.value?.clearValidate()
}

/** 修改密码（弹窗内提交）：只校验/提交密码表单，成功后退登重新登录 */
async function handleChangePassword() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  pwdSaving.value = true
  try {
    // 调用通用版修改密码接口（管理员 / 教师 / 学生统一）
    await updatePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    // 修改成功：先关弹窗，再登出当前 token、清前端登录态，跳登录页
    pwdDialogVisible.value = false
    try { await logout() } catch {}
    authStore.clearAuth()
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    router.push('/login')
  } catch (e) {
    // 拦截器已处理错误提示（原密码错误 / 新旧相同 / 格式不合规等）
  } finally {
    pwdSaving.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.profile-page {
  display: flex;
  justify-content: center;
}
.profile-card {
  width: 100%;
  max-width: 760px;
  border-radius: 12px;
  /* 阴影比默认略重一点，卡片层次感更强 */
  box-shadow: 0 8px 24px rgba(31, 41, 55, 0.06);
}
.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.header-tip {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
  line-height: 1.6;
}

/* ============ 头像区：头像 + 姓名/角色 + 上传按钮 ============ */
.avatar-block {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 10px;
}
.avatar-wrap {
  flex: none;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #409eff, #79bbff);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.avatar-text {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}
.avatar-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.avatar-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.avatar-sub {
  font-size: 12px;
  color: #909399;
}
.avatar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.avatar-upload :deep(.el-upload) {
  display: inline-block;
}
.avatar-tip {
  font-size: 12px;
  color: #a8abb2;
}

.profile-form {
  margin-top: 8px;
}

/* 只读输入框：灰底 + 禁用光标，一眼就能看出不可编辑 */
.profile-form :deep(.el-input.is-disabled .el-input__inner) {
  color: #606266;
  -webkit-text-fill-color: #606266;
  background-color: #f3f5f9;
  border-color: #e4e7ed;
  cursor: not-allowed;
}
/* 可编辑输入框保持白底，和只读区形成对比 */
.profile-form :deep(.el-input:not(.is-disabled) .el-input__inner) {
  background-color: #fff;
}

/* 弹窗内说明文字 */
.dialog-tip {
  margin-bottom: 14px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

/* 密码输入框：字距稍微拉开，密文更易读 */
.pwd-form :deep(.el-input__inner) {
  letter-spacing: 1px;
}

/* 底部按钮：与输入框左边对齐（label-width 100px）；保存/重置在左，「修改密码」用竖线隔开 */
.footer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 100px;
  margin-top: 8px;
  flex-wrap: wrap;
}
/* 独立功能（修改密码）与「保存资料」之间的分隔竖线 */
.actions-divider {
  width: 1px;
  height: 20px;
  background-color: #e4e7ed;
  margin: 0 4px;
}
/* 按钮 hover：轻微上浮 + 阴影，点击反馈更明显 */
.footer-actions :deep(.el-button) {
  transition: transform 0.2s, box-shadow 0.2s;
}
.footer-actions :deep(.el-button:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(64, 158, 255, 0.18);
}

/* 窄屏：去掉左侧留白，按钮允许换行 */
@media (max-width: 600px) {
  .footer-actions {
    padding-left: 0;
    flex-wrap: wrap;
    gap: 8px;
  }
}

.eye-icon {
  cursor: pointer;
  color: #909399;
}
.eye-icon:hover {
  color: var(--app-primary);
}
</style>
