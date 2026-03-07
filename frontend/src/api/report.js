import request from '@/utils/request'

export const getMonthlyReport = (params) => request.get('/reports/monthly', { params })
