import request from '@/utils/request'

export const getProcessList = (params) => request.get('/processes', { params })
export const getProcessAll = () => request.get('/processes/all')
export const createProcess = (data) => request.post('/processes', data)
export const updateProcess = (id, data) => request.put(`/processes/${id}`, data)
export const deleteProcess = (id) => request.delete(`/processes/${id}`)
export const updateProcessStatus = (id, status) => request.put(`/processes/${id}/status`, null, { params: { status } })
