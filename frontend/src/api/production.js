import request from '@/utils/request'

export const getProductionList = (params) => request.get('/productions', { params })
export const getProductionById = (id) => request.get(`/productions/${id}`)
export const createProduction = (data) => request.post('/productions', data)
export const updateProduction = (id, data) => request.put(`/productions/${id}`, data)
export const deleteProduction = (id) => request.delete(`/productions/${id}`)
export const updateProductionStatus = (id, prodStatus) => request.put(`/productions/${id}/status`, null, { params: { prodStatus } })
