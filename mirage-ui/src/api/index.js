import http from './client'

export const api = {
  auth: {
    // silent：登录失败只走 Login.vue 的内联 alert，避免拦截器再弹一个 toast（双提示）
    login: (username, password) => http.post('/auth/login', { username, password }, { silent: true })
  },
  projects: {
    list: () => http.get('/projects'),
    create: (p) => http.post('/projects', p),
    createSample: () => http.post('/projects/sample'),
    update: (id, p) => http.put(`/projects/${id}`, p),
    remove: (id) => http.delete(`/projects/${id}`),
    members: (id) => http.get(`/projects/${id}/members`),
    addMember: (id, m) => http.post(`/projects/${id}/members`, m),
    removeMember: (id, uid) => http.delete(`/projects/${id}/members/${uid}`),
    // CI Token 已从项目 JSON 移除（@JsonIgnore），改为按需获取 / 重置（仅管理员）
    ciToken: (id) => http.get(`/projects/${id}/ci-token`),
    rotateCiToken: (id) => http.post(`/projects/${id}/ci-token/rotate`)
  },
  interfaces: {
    // 接口/规则列表可能较大（单项目几百条），给更宽的超时避免默认 20s 被打断
    list: (pid) => http.get(`/projects/${pid}/interfaces`, { timeout: 60000 }),
    create: (pid, itf) => http.post(`/projects/${pid}/interfaces`, itf),
    update: (id, itf) => http.put(`/interfaces/${id}`, itf),
    remove: (id) => http.delete(`/interfaces/${id}`)
  },
  rules: {
    list: (iid) => http.get(`/interfaces/${iid}/rules`, { timeout: 60000 }),
    create: (iid, r) => http.post(`/interfaces/${iid}/rules`, r),
    update: (id, r) => http.put(`/rules/${id}`, r),
    remove: (id) => http.delete(`/rules/${id}`),
    removeBatch: (ids) => http.delete('/rules/batch', { data: ids }),
    setPriority: (id, priority) => http.put(`/rules/${id}/priority`, null, { params: { priority } }),
    setBatchStatus: (ids, status) => http.put('/rules/batch/status', { ids, status }),
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
    query: (pid, params) => http.get(`/projects/${pid}/logs`, { params }),
    get: (pid, logId) => http.get(`/projects/${pid}/logs/${logId}`),
    toTestCase: (pid, logId) => http.post(`/projects/${pid}/logs/${logId}/testcase`)
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
    // 摘要（MD5/SHA 系列）：文本或文件（contentB64）。silent：失败时由组件内联提示，避免双 toast
    digest: (p) => http.post('/tools/digest', p, { silent: true }),
    // JSONPath 求值（silent 同上）
    jsonPath: (p) => http.post('/tools/jsonpath', p, { silent: true }),
    // HMAC 签名（接口签名调试）。silent：失败由组件内联提示
    hmac: (p) => http.post('/tools/hmac', p, { silent: true }),
    // 文本 → 目标字符集字节（排查中文乱码）
    charsetEncode: (p) => http.post('/tools/charset/encode', p, { silent: true }),
    // 字节（Hex/Base64）→ 文本
    charsetDecode: (p) => http.post('/tools/charset/decode', p, { silent: true }),
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
  dashboard: {
    overview: (pid) => http.get(`/projects/${pid}/dashboard`),
    trend: (pid, days) => http.get(`/projects/${pid}/dashboard/trend`, { params: { days } })
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
    preview: (payload) => http.post('/file-templates/preview', payload, { timeout: 300000 }),
    generate: (payload) => http.post('/file-templates/generate', payload, { responseType: 'blob', timeout: 300000 })
  },
  testCases: {
    list: (pid) => http.get(`/projects/${pid}/testcases`),
    get: (id) => http.get(`/testcases/${id}`),
    create: (pid, t) => http.post(`/projects/${pid}/testcases`, t),
    update: (id, t) => http.put(`/testcases/${id}`, t),
    remove: (id) => http.delete(`/testcases/${id}`),
    removeBatch: (ids) => http.delete('/testcases/batch', { data: ids }),
    run: (id, envId) => http.post(`/testcases/${id}/run`, null, { params: { envId }, timeout: 300000 }),
    runData: (id, envId) => http.post(`/testcases/${id}/run-data`, null, { params: { envId }, timeout: 300000 }),
    runBatch: (pid, ids, envId) => http.post(`/projects/${pid}/testcases/run`, { ids, envId }, { timeout: 600000 }),
    setBatchStatus: (pid, ids, status) => http.put(`/projects/${pid}/testcases/batch-status`, { ids, status }),
    runs: (id) => http.get(`/testcases/${id}/runs`),
    // 单条运行记录详情（含请求/返回报文）
    runDetail: (id, runId) => http.get(`/testcases/${id}/runs/${runId}`)
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
    clone: (id) => http.post(`/environments/${id}/clone`),
    remove: (id) => http.delete(`/environments/${id}`)
  },
  scenarios: {
    list: (pid) => http.get(`/projects/${pid}/scenarios`),
    create: (pid, s) => http.post(`/projects/${pid}/scenarios`, s),
    update: (id, s) => http.put(`/scenarios/${id}`, s),
    remove: (id) => http.delete(`/scenarios/${id}`),
    steps: (id) => http.get(`/scenarios/${id}/steps`),
    saveSteps: (id, steps) => http.put(`/scenarios/${id}/steps`, steps),
    run: (id, envId) => http.post(`/scenarios/${id}/run`, null, { params: { envId }, timeout: 300000 })
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
    run: (id) => http.post(`/schedules/${id}/run`),
    cronPreview: (cron, count) => http.get('/schedules/cron-preview', { params: { cron, count }, silent: true })
  }
}
