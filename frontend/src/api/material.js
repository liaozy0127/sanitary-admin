import request from '@/utils/request'

export const getMaterialList = (params) => request.get('/materials', { params })
export const searchMaterials = (params) => request.get('/materials/search', { params })
export const createMaterial = (data) => request.post('/materials', data)
export const updateMaterial = (id, data) => request.put(`/materials/${id}`, data)
export const deleteMaterial = (id) => request.delete(`/materials/${id}`)
export const updateMaterialStatus = (id, status) => request.put(`/materials/${id}/status`, null, { params: { status } })
