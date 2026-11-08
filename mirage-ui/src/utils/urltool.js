/**
 * URL 解析 / 拼装（纯前端）。
 *
 * 测试时经常要拆开一条回调 URL 看参数，或者把参数拼成 query 串，
 * 再和 JSON 请求体互转。
 */

/**
 * 解析 URL。
 * @returns {{ok: boolean, error?: string, protocol, host, hostname, port, pathname, search, hash, origin, params: Array<{key,value}>}}
 */
export function parseUrl(raw) {
  const s = String(raw ?? '').trim()
  if (!s) return { ok: false, error: 'URL 不能为空', params: [] }
  let u
  try {
    u = new URL(s)
  } catch (e) {
    // 相对路径（/a/b?x=1）按 localhost 补全再解析
    try {
      u = new URL(s, 'http://localhost')
    } catch (e2) {
      return { ok: false, error: 'URL 格式无法解析', params: [] }
    }
  }
  const params = []
  u.searchParams.forEach((v, k) => params.push({ key: k, value: v }))
  return {
    ok: true,
    protocol: u.protocol.replace(':', ''),
    host: u.host,
    hostname: u.hostname,
    port: u.port,
    pathname: u.pathname,
    search: u.search,
    hash: u.hash,
    origin: u.origin,
    params
  }
}

/** query 串 → 对象（同名 key 收敛成数组）。 */
export function queryToJson(qs) {
  const s = String(qs ?? '').replace(/^[?#]/, '')
  const out = {}
  if (!s) return out
  const sp = new URLSearchParams(s)
  sp.forEach((v, k) => {
    if (Object.prototype.hasOwnProperty.call(out, k)) {
      if (Array.isArray(out[k])) out[k].push(v)
      else out[k] = [out[k], v]
    } else {
      out[k] = v
    }
  })
  return out
}

/** 对象 → query 串（数组展开成重复 key）。 */
export function jsonToQuery(obj) {
  const parts = []
  const push = (k, v) => {
    if (v === null || v === undefined) return
    if (Array.isArray(v)) {
      v.forEach((x) => push(k, x))
      return
    }
    if (typeof v === 'object') {
      parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(JSON.stringify(v)))
      return
    }
    parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(String(v)))
  }
  for (const k of Object.keys(obj || {})) push(k, obj[k])
  return parts.join('&')
}

/**
 * 拼装 URL：base（含 scheme/host/port，可带 path）+ path + 参数对象。
 */
export function buildUrl(base, path, params) {
  let s = String(base ?? '').trim()
  if (s && !/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(s) && !s.startsWith('/')) {
    s = 'http://' + s
  }
  const p = String(path ?? '').trim()
  if (p) {
    if (s.endsWith('/') && p.startsWith('/')) s = s.slice(0, -1)
    else if (!s.endsWith('/') && !p.startsWith('/')) s += '/'
    s += p
  }
  const q = jsonToQuery(params)
  if (q) s += (s.includes('?') ? '&' : '?') + q
  return s
}
