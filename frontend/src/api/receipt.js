import request from '@/utils/request'

export const getReceiptList = (params) => request.get('/receipts', { params })
export const getReceiptById = (id) => request.get(`/receipts/${id}`)
export const createReceipt = (data) => request.post('/receipts', data)
export const updateReceipt = (id, data) => request.put(`/receipts/${id}`, data)
export const deleteReceipt = (id) => request.delete(`/receipts/${id}`)
export const downloadTemplate = () => request.get('/receipts/template', { responseType: 'blob' })
export const importReceipts = (formData) => request.post('/receipts/import', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
