import request from '@/utils/request'

// ========== 教师/管理员端 ==========

/** 发布通知 */
export function sendNotice(data) {
  return request.post('/api/notices/send', data)
}

/** 通知记录列表 */
export function getNoticeList(params) {
  return request.get('/api/notices/list', { params })
}

/** 通知详情 */
export function getNoticeDetail(id) {
  return request.get(`/api/notices/detail/${id}`)
}

/** 删除通知 */
export function deleteNotice(id) {
  return request.delete(`/api/notices/delete/${id}`)
}

/** 编辑通知并重新发布（重置接收人已读状态，重新通知） */
export function updateNotice(data) {
  return request.put('/api/notices/update', data)
}

/** 学生确认收到消息（幂等） */
export function confirmNotice(noticeId) {
  return request.put(`/api/notices/confirm/${noticeId}`)
}

/** 学生把已读消息重新标记为未读 */
export function markStudentNoticeUnread(noticeId) {
  return request.put(`/api/notices/userUnread/${noticeId}`)
}

/** 学生置顶 / 取消置顶自己的消息 */
export function setStudentNoticeTop(userNoticeId, top) {
  return request.put(`/api/notices/userTop/${userNoticeId}?top=${top}`)
}

// ========== 学生端 ==========

/** 获取未读消息数 */
export function getUnreadCount() {
  return request.get('/api/notices/unreadCount')
}

/** 获取自己的消息列表 */
export function getUserNoticeList(limit = 50) {
  return request.get('/api/notices/userList', { params: { limit } })
}

/** 标记某条消息已读 */
export function markNoticeRead(noticeId) {
  return request.post(`/api/notices/read/${noticeId}`)
}

/** 全部标记已读 */
export function markAllRead() {
  return request.post('/api/notices/readAll')
}

/** 消息通知页面分页查询 */
export function getStudentNoticePage(params) {
  return request.get('/api/notices/userPage', { params })
}

/** 学生逻辑删除自己的一条消息 */
export function deleteStudentNotice(id) {
  return request.delete(`/api/notices/userDelete/${id}`)
}
