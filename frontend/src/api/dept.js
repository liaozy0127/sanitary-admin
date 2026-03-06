import request from '@/utils/request'

export const getDeptList = (params) => {
  return request.get('/depts', { params })
}

export const getDeptById = (id) => {
  return request.get(`/depts/${id}`)
}

export const createDept = (data) => {
  return request.post('/depts', data)
}

export const updateDept = (id, data) => {
  return request.put(`/depts/${id}`, data)
}

export const deleteDept = (id) => {
  return request.delete(`/depts/${id}`)
}