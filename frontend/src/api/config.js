import request from '@/utils/request'

export const getPrintConfig = () => request.get('/config/print')
export const updatePrintConfig = (data) => request.put('/config/print', data)
