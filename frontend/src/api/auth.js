import request from '@/utils/request'

export const login = (data) => {
  return request.post('/auth/login', data)
}

export const logout = () => {
  return request.post('/auth/logout')
}

export const getProfile = () => {
  return request.get('/auth/profile')
}
