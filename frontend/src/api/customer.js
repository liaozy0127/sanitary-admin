import request from '@/utils/request'

export const getCustomerList = (params) => request.get('/customers', { params })
export const getCustomerAll = (params) => request.get('/customers/all', { params })
export const createCustomer = (data) => request.post('/customers', data)
export const updateCustomer = (id, data) => request.put(`/customers/${id}`, data)
export const deleteCustomer = (id) => request.delete(`/customers/${id}`)
export const updateCustomerStatus = (id, status) => request.put(`/customers/${id}/status`, null, { params: { status } })
