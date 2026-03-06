import request from '@/utils/request'

export const getRoleList = (params) => {
  return request.get('/roles', { params })
}

export const getRoleById = (id) => {
  return request.get(`/roles/${id}`)
}

export const createRole = (data) => {
  return request.post('/roles', data)
}

export const updateRole = (id, data) => {
  return request.put(`/roles/${id}`, data)
}

export const deleteRole = (id) => {
  return request.delete(`/roles/${id}`)
}