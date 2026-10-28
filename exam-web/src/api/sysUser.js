import request from '@/utils/request'

// 按角色查用户列表（role=1 教师，role=2 管理员；试卷共享/筛选下拉用）
export function getUserListByRole(role) {
  return request.get('/api/sys-user/list', { params: { role } })
}

// 班级列表（账号管理页筛选下拉用）
export function getClassList() {
  return request.get('/api/teacher/classes')
}

// 分页查询用户（username 学号精确查询 / realName 姓名模糊查询 + 角色筛选 + 班级筛选）
export function getUserPage(params) {
  return request.get('/api/sys-user/page', { params })
}

// 新增账号（管理员权限）
export function createUser(data) {
  return request.post('/api/sys-user/create', data)
}

// 批量新增账号（管理员权限）：账号重复/字段缺失的自动跳过，返回 {success, skipped}
export function createUserBatch(users) {
  return request.post('/api/sys-user/createBatch', users)
}

// 批量删除账号（管理员权限）：admin 账号后端强制保护，返回实际删除条数
export function deleteUserBatch(ids) {
  return request.delete('/api/sys-user/batch', { params: { ids: ids.join(',') } })
}

// 批量校验学号/账号是否已存在（批量新增弹窗实时提醒用）：返回已存在的账号名列表
export function checkUsernamesExist(usernames) {
  return request.post('/api/sys-user/usernamesExist', usernames)
}

// 新建班级（批量新增弹窗里直接输入新班级名时调用）
export function createClassQuick(className, grade) {
  return request.post('/api/class', { className, grade: grade || '' })
}

// 按角色统计用户数
export function countUserByRole(role) {
  return request.get('/api/sys-user/count', { params: { role } })
}

// 启用/禁用账号
export function updateUserStatus(id, status) {
  return request.put('/api/sys-user/status', { id, status })
}

// 重置密码（前端用 BCrypt 哈希后传入）
export function updateUserPassword(id, password) {
  return request.put('/api/sys-user/password', { id, password })
}

// 调整账号所属班级（管理员调班用，classId 传 null 表示取消归属）
export function updateUserClass(id, classId) {
  return request.put('/api/sys-user/class', { id, classId: classId == null ? null : classId })
}

// ============ 调班双入口统一接口（账号管理 + 班级管理共用） ============

// 单人/批量调班（仅管理员）：studentIds 支持传 1 个或多个学生 id
export function updateStudentClass(studentIds, targetClassId) {
  return request.put('/api/student/updateClass', { studentIds, targetClassId })
}

// 批量移出本班（仅管理员）：把学生 class_id 置 null，账号保留
export function removeStudentClass(studentIds) {
  return request.put('/api/student/removeClass', null, {
    params: { studentIds: studentIds.join(',') }
  })
}

// Excel 批量导入账号（管理员权限）：上传 .xlsx 文件，后端解析后批量创建
export function importExcelUsers(file) {
  return request.post('/api/sys-user/importExcel', file, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
