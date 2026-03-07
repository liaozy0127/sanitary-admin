import request from '@/utils/request'

export const getShipmentList = (params) => request.get('/shipments', { params })
export const getShipmentById = (id) => request.get(`/shipments/${id}`)
export const createShipment = (data) => request.post('/shipments', data)
export const updateShipment = (id, data) => request.put(`/shipments/${id}`, data)
export const deleteShipment = (id) => request.delete(`/shipments/${id}`)
