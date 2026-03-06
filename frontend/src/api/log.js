import request from '@/utils/request'

export const getLogList = (params) => {
  return request.get('/logs', { params })
}

export const getLogById = (id) => {
  return request.get(`/logs/${id}`)
}