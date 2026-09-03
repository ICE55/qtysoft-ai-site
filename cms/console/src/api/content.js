import http from './http'

export function getSchema(key) {
  return http.get('/content/schema', { params: { key } })
}

export function getSummary() {
  return http.get('/content/summary')
}

export function getDraft(key) {
  return http.get(`/content/${key}`)
}

export function saveDraft(key, data) {
  return http.put(`/content/${key}`, data)
}

export function publish(key, note) {
  return http.post(`/content/${key}/publish`, null, { params: { note } })
}

export function getHistory(key) {
  return http.get(`/content/${key}/history`)
}

export function restore(key, revId) {
  return http.post(`/content/${key}/restore/${revId}`)
}
