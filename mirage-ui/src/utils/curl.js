/**
 * 轻量 curl 命令解析：支持引号转义、-X/-H/-d/--data/--data-raw/--url 与裸 URL。
 * 覆盖 Postman/浏览器复制的常见格式；不依赖第三方库（避免解析 bug）。
 * @returns {{method, url, headers:[{k,v}], body, bodyType}}
 */
export function parseCurl(text) {
  const tokens = tokenize((text || '').trim())
  let method = 'GET'
  let url = ''
  const headers = []
  let body = ''
  let hasBody = false
  const valueFlags = ['-a', '--user-agent', '-e', '--referer', '-o', '--output', '-u', '--user', '-b', '--cookie', '--connect-timeout', '-m', '--max-time', '--proxy']

  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i]
    const low = t.toLowerCase()
    if (t === 'curl') continue
    if (low === '-x' || low === '--request') { method = (tokens[++i] || '').toUpperCase(); continue }
    if (low === '-h' || low === '--header') {
      const hv = tokens[++i] || ''
      const idx = hv.indexOf(':')
      if (idx >= 0) headers.push({ k: hv.slice(0, idx).trim(), v: hv.slice(idx + 1).trim() })
      continue
    }
    if (low === '-d' || low === '--data' || low === '--data-raw' || low === '--data-binary' || low === '--data-ascii') {
      body = tokens[++i] || ''
      hasBody = true
      if (method === 'GET') method = 'POST'
      continue
    }
    if (low === '--url') { url = tokens[++i] || ''; continue }
    if (/^(-|--)[a-z]/.test(low)) {
      if (valueFlags.includes(low)) i++
      continue
    }
    if (!url) url = t
  }

  const ct = headers.find((h) => h.k.toLowerCase() === 'content-type')
  let bodyType = 'none'
  if (hasBody) {
    if (ct && ct.v.includes('json')) bodyType = 'json'
    else if (ct && ct.v.includes('x-www-form-urlencoded')) bodyType = 'form'
    else bodyType = 'raw'
  }
  return { method, url, headers, body: hasBody ? body : '', bodyType }
}

function tokenize(s) {
  const out = []
  let cur = ''
  let quote = null
  for (let i = 0; i < s.length; i++) {
    const c = s[i]
    if (quote) {
      if (c === quote) { quote = null; continue }
      if (quote === '"' && c === '\\') { cur += s[i + 1] || ''; i++; continue }
      cur += c
    } else {
      if (c === "'" || c === '"') { quote = c; continue }
      if (c === '\\') { cur += s[i + 1] || ''; i++; continue }
      if (/\s/.test(c)) { if (cur) { out.push(cur); cur = '' } } else cur += c
    }
  }
  if (cur) out.push(cur)
  return out
}
