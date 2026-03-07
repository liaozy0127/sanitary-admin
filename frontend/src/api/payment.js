import request from '@/utils/request'

export const getPaymentList = (params) => request.get('/payments', { params })
export const getPaymentById = (id) => request.get(`/payments/${id}`)
export const createPayment = (data) => request.post('/payments', data)
export const updatePayment = (id, data) => request.put(`/payments/${id}`, data)
export const deletePayment = (id) => request.delete(`/payments/${id}`)
