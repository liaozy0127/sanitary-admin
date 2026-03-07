import request from '@/utils/request'

export const getReworkList = (params) => request.get('/reworks', { params })
export const getReworkById = (id) => request.get(`/reworks/${id}`)
export const createRework = (data) => request.post('/reworks', data)
export const updateRework = (id, data) => request.put(`/reworks/${id}`, data)
export const deleteRework = (id) => request.delete(`/reworks/${id}`)
