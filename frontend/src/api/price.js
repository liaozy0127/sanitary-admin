import request from '@/utils/request'

export const getPriceList = (params) => request.get('/material-process-prices', { params })

export const queryPrice = (customerId, materialId, processId) =>
  request.get('/material-process-prices/query', { params: { customerId, materialId, processId } })

export const createPrice = (data) => request.post('/material-process-prices', data)

export const updatePrice = (id, data) => request.put(`/material-process-prices/${id}`, data)

export const deletePrice = (id) => request.delete(`/material-process-prices/${id}`)
