import http from './http'

export function login(username, password) {
  return http.post('/auth/login', { username, password })
}

export function me() {
  return http.get('/auth/me')
}

export function changePassword(oldPassword, newPassword) {
  return http.post('/auth/change-password', { oldPassword, newPassword })
}
