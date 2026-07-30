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
  tools: {
    jsonFormat: (p) => http.post('/tools/json/format', p),
    jsonMinify: (p) => http.post('/tools/json/minify', p),
    jsonValidate: (p) => http.post('/tools/json/validate', p),
    jsonEscape: (p) => http.post('/tools/json/escape', p),
    jsonUnescape: (p) => http.post('/tools/json/unescape', p),
    xmlFormat: (p) => http.post('/tools/xml/format', p),
    xmlMinify: (p) => http.post('/tools/xml/minify', p),
    xmlValidate: (p) => http.post('/tools/xml/validate', p),
    xmlEscape: (p) => http.post('/tools/xml/escape', p),
    xmlUnescape: (p) => http.post('/tools/xml/unescape', p),
    sqlFormat: (p) => http.post('/tools/sql/format', p),
    b64Encode: (p) => http.post('/tools/base64/encode', p),
    b64Decode: (p) => http.post('/tools/base64/decode', p),
    hexEncode: (p) => http.post('/tools/hex/encode', p),
    hexDecode: (p) => http.post('/tools/hex/decode', p),
    urlEncode: (p) => http.post('/tools/url/encode', p),
    urlDecode: (p) => http.post('/tools/url/decode', p),
    hash: (p) => http.post('/tools/hash', p),
    jwtDecode: (p) => http.post('/tools/jwt/decode', p),
    tsNow: () => http.post('/tools/timestamp/now'),
    tsToEpoch: (p) => http.post('/tools/timestamp/to-epoch', p),
    tsFromEpoch: (p) => http.post('/tools/timestamp/from-epoch', p),
    uuid: (p) => http.post('/tools/uuid', p),
    sm3: (p) => http.post('/tools/sm3', p),
    sm4Encrypt: (p) => http.post('/tools/sm4/encrypt', p),
    sm4Decrypt: (p) => http.post('/tools/sm4/decrypt', p),
    sm4Key: () => http.post('/tools/sm4/key'),
    sm2Encrypt: (p) => http.post('/tools/sm2/encrypt', p),
    sm2Decrypt: (p) => http.post('/tools/sm2/decrypt', p),
    sm2Sign: (p) => http.post('/tools/sm2/sign', p),
    sm2Verify: (p) => http.post('/tools/sm2/verify', p),
    sm2Keypair: () => http.post('/tools/sm2/keypair'),
    rsaEncrypt: (p) => http.post('/tools/rsa/encrypt', p),
    rsaDecrypt: (p) => http.post('/tools/rsa/decrypt', p),
    rsaSign: (p) => http.post('/tools/rsa/sign', p),
    rsaVerify: (p) => http.post('/tools/rsa/verify', p),
    rsaKeypair: (p) => http.post('/tools/rsa/keypair', p)
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
  },
  testCases: {
    list: (pid) => http.get(`/projects/${pid}/testcases`),
    create: (pid, t) => http.post(`/projects/${pid}/testcases`, t),
    update: (id, t) => http.put(`/testcases/${id}`, t),
    remove: (id) => http.delete(`/testcases/${id}`),
    run: (id, envId) => http.post(`/testcases/${id}/run`, null, { params: { envId } }),
    runData: (id, envId) => http.post(`/testcases/${id}/run-data`, null, { params: { envId } }),
    runs: (id) => http.get(`/testcases/${id}/runs`)
  },
  testVariables: {
    list: (pid) => http.get(`/projects/${pid}/test-variables`),
    create: (pid, v) => http.post(`/projects/${pid}/test-variables`, v),
    update: (id, v) => http.put(`/test-variables/${id}`, v),
    remove: (id) => http.delete(`/test-variables/${id}`)
  },
  environments: {
    list: (pid) => http.get(`/projects/${pid}/environments`),
    create: (pid, e) => http.post(`/projects/${pid}/environments`, e),
    update: (id, e) => http.put(`/environments/${id}`, e),
    remove: (id) => http.delete(`/environments/${id}`)
  },
  scenarios: {
    list: (pid) => http.get(`/projects/${pid}/scenarios`),
    create: (pid, s) => http.post(`/projects/${pid}/scenarios`, s),
    update: (id, s) => http.put(`/scenarios/${id}`, s),
    remove: (id) => http.delete(`/scenarios/${id}`),
    steps: (id) => http.get(`/scenarios/${id}/steps`),
    saveSteps: (id, steps) => http.put(`/scenarios/${id}/steps`, steps),
    run: (id, envId) => http.post(`/scenarios/${id}/run`, null, { params: { envId } })
  },
  records: {
    query: (pid, params) => http.get(`/projects/${pid}/records`, { params }),
    get: (id) => http.get(`/records/${id}`)
  },
  schedules: {
    list: (pid) => http.get(`/projects/${pid}/schedules`),
    create: (pid, s) => http.post(`/projects/${pid}/schedules`, s),
    update: (id, s) => http.put(`/schedules/${id}`, s),
    remove: (id) => http.delete(`/schedules/${id}`),
    toggle: (id) => http.post(`/schedules/${id}/toggle`),
    run: (id) => http.post(`/schedules/${id}/run`)
  }
}
