import request from '@/utils/request'

export function getTemplateList(params) {
  return request.get('/api/paper-template/list', { params })
}

export function getTemplateDetail(id) {
  return request.get(`/api/paper-template/${id}`)
}

export function createTemplate(data) {
  return request.post('/api/paper-template', data)
}

export function updateTemplate(data) {
  return request.put('/api/paper-template', data)
}

export function deleteTemplate(id) {
  return request.delete(`/api/paper-template/${id}`)
}
