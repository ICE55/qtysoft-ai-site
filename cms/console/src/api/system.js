import http from './http'

export function listUsers() {
  return http.get('/system/users')
}

export function createUser(payload) {
  return http.post('/system/users', payload)
}

export function updateUser(id, payload) {
  return http.put(`/system/users/${id}`, payload)
}

export function deleteUser(id) {
  return http.delete(`/system/users/${id}`)
}

export function triggerDeploy() {
  return http.post('/system/deploy')
}
