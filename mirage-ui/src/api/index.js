import http from './client'

export const api = {
  auth: {
    login: (username, password) => http.post('/auth/login', { username, password })
  },
  projects: {
    list: () => http.get('/projects'),
    create: (p) => http.post('/projects', p),
    update: (id, p) => http.put(`/projects/${id}`, p),
    remove: (id) => http.delete(`/projects/${id}`),
    members: (id) => http.get(`/projects/${id}/members`),
    addMember: (id, m) => http.post(`/projects/${id}/members`, m),
    removeMember: (id, uid) => http.delete(`/projects/${id}/members/${uid}`)
  },
  interfaces: {
    list: (pid) => http.get(`/projects/${pid}/interfaces`),
    create: (pid, itf) => http.post(`/projects/${pid}/interfaces`, itf),
    update: (id, itf) => http.put(`/interfaces/${id}`, itf),
    remove: (id) => http.delete(`/interfaces/${id}`)
  },
  rules: {
    list: (iid) => http.get(`/interfaces/${iid}/rules`),
    create: (iid, r) => http.post(`/interfaces/${iid}/rules`, r),
    update: (id, r) => http.put(`/rules/${id}`, r),
    remove: (id) => http.delete(`/rules/${id}`),
    toggle: (id) => http.post(`/rules/${id}/toggle`)
  },
  listeners: {
    list: (pid) => http.get(`/projects/${pid}/listeners`),
    create: (pid, l) => http.post(`/projects/${pid}/listeners`, l),
    update: (id, l) => http.put(`/listeners/${id}`, l),
    remove: (id) => http.delete(`/listeners/${id}`),
    start: (id) => http.post(`/listeners/${id}/start`),
    stop: (id) => http.post(`/listeners/${id}/stop`),
    status: (id) => http.get(`/listeners/${id}/status`)
  },
  keys: {
    list: (pid) => http.get(`/projects/${pid}/keys`),
    create: (pid, k) => http.post(`/projects/${pid}/keys`, k),
    remove: (id) => http.delete(`/keys/${id}`),
    generateSm2: (pid, alias) => http.post(`/projects/${pid}/keys/sm2/generate`, null, { params: { alias } })
  },
  logs: {
    query: (pid, params) => http.get(`/projects/${pid}/logs`, { params })
  },
  template: {
    evaluate: (payload) => http.post('/template/evaluate', payload)
  },
  functions: {
    list: () => http.get('/functions')
  },
  system: {
    info: () => http.get('/system/info')
  },
  users: {
    list: () => http.get('/users'),
    create: (u) => http.post('/users', u),
    update: (id, u) => http.put(`/users/${id}`, u),
    remove: (id) => http.delete(`/users/${id}`)
  },
  fileTemplates: {
    list: (pid) => http.get(`/projects/${pid}/file-templates`),
    create: (pid, t) => http.post(`/projects/${pid}/file-templates`, t),
    update: (id, t) => http.put(`/file-templates/${id}`, t),
    remove: (id) => http.delete(`/file-templates/${id}`),
    preview: (payload) => http.post('/file-templates/preview', payload),
    generate: (payload) => http.post('/file-templates/generate', payload, { responseType: 'blob' })
  }
}
