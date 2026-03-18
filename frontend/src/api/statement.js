import request from '@/utils/request'

export const getStatementList = (params) => request.get('/statements', { params })
export const getStatementById = (id) => request.get(`/statements/${id}`)
export const generateStatement = (data) => request.post('/statements/generate', data)
export const deleteStatement = (id) => request.delete(`/statements/${id}`)
export const exportStatements = (params) =>
  request.get('/statements/export', { params, responseType: 'blob' })
