import request from '@/utils/request'

export const getInventoryList = (params) => request.get('/inventory', { params })
export const getInventoryLog = (params) => request.get('/inventory/log', { params })
export const exportInventory = (params) =>
  request.get('/inventory/export', { params, responseType: 'blob' })
