import request from '@/utils/request'

export const getInventoryList = (params) => request.get('/inventory', { params })
