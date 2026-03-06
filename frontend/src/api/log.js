import request from '@/utils/request'

export const getLogList = (params) => {
  return request.get('/logs', { params })
}

export const getLogById = (id) => {
  return request.get(`/logs/${id}`)
}

export const deleteLog = (id) => {
  return request.delete(`/logs/${id}`)
}

export const clearLogs = (params) => {
  return request.delete('/logs/clear', { params })
}