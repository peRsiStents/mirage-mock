/**
 * 轻量 curl 命令解析：支持引号转义、-X/-H/-d/--data/--data-raw/--url 与裸 URL。
 * 覆盖 Postman/浏览器复制的常见格式；不依赖第三方库（避免解析 bug）。
 * @returns {{method, url, headers:[{k,v}], body, bodyType, bodyContentType}}
 */
export function parseCurl(text) {
  const tokens = tokenize((text || '').trim())
  let method = ''
  let url = ''
  const headers = []
  let body = ''
  let hasBody = false
  const valueFlags = ['-a', '--user-agent', '-e', '--referer', '-o', '--output', '-u', '--user', '-b', '--cookie', '--connect-timeout', '-m', '--max-time', '--proxy']
  // 这些 flag 支持「紧贴值」写法：-H"X: 1" / --header=X:1 / -XPOST
  const attachedValueFlags = ['-x', '--request', '-h', '--header', '-d', '--data', '--data-raw', '--data-binary', '--data-ascii', '--url']

  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i]
    const low = t.toLowerCase()
    if (t === 'curl') continue

    // 1) 紧贴值形式：--header=X:1 / -XPOST
    const eq = t.indexOf('=')
    if (eq > 0 && t.startsWith('-')) {
      const name = t.slice(0, eq).toLowerCase()
      const val = t.slice(eq + 1)
      if (attachedValueFlags.includes(name)) {
        if (name === '-x' || name === '--request') method = val.toUpperCase()
        else if (name === '--url') url = val
        else if (name === '-h' || name === '--header') pushHeader(headers, val)
        else { body = val; hasBody = true }
        continue
      }
    }
    // 2) 紧贴无等号形式：-XPOST / -HX-A:1 / -d{"a":1}
    //    用小写比较（flag 名大小写不敏感），但取「剩余部分」时按原串同长度截取
    const flag = attachedValueFlags.find((f) => low.startsWith(f) && t.length > f.length)
    if (flag) {
      const rest = t.slice(flag.length)
      // -XPOST：rest 全是字母（方法名）；注意 --request 也归此类
      if ((flag === '-x' || flag === '--request') && /^[A-Za-z]+$/.test(rest)) { method = rest.toUpperCase(); continue }
      if (flag === '--url') { url = rest; continue }
      if (flag === '-h' || flag === '--header') { pushHeader(headers, rest); continue }
      // -d{...} / -d[...] / -d"..." 形式的数据体
      if (rest.startsWith('{') || rest.startsWith('[') || rest.startsWith('"') || rest.startsWith("'")) {
        body = rest; hasBody = true; continue
      }
    }

    if (low === '-x' || low === '--request') { method = (tokens[++i] || '').toUpperCase(); continue }
    if (low === '-h' || low === '--header') { pushHeader(headers, tokens[++i] || ''); continue }
    if (low === '-d' || low === '--data' || low === '--data-raw' || low === '--data-binary' || low === '--data-ascii') {
      body = tokens[++i] || ''
      hasBody = true
      continue
    }
    if (low === '--url') { url = tokens[++i] || ''; continue }
    if (/^(-|--)[a-z]/.test(low)) {
      if (valueFlags.includes(low)) i++
      continue
    }
    if (!url) url = t
  }
  // 有请求体但未显式指定方法时按 curl 语义默认为 POST
  if (!method) method = hasBody ? 'POST' : 'GET'
  method = method.toUpperCase()

  const ct = headers.find((h) => h.k.toLowerCase() === 'content-type')
  let bodyType = 'none'
  let bodyContentType = ''
  let bodyOut = hasBody ? body : ''
  if (hasBody) {
    if (ct && ct.v.includes('json')) {
      bodyType = 'raw'; bodyContentType = 'application/json'
    } else if (ct && ct.v.includes('x-www-form-urlencoded')) {
      bodyType = 'x-www-form-urlencoded'
      bodyOut = JSON.stringify(parseUrlencoded(body))
    } else {
      bodyType = 'raw'
    }
  }
  return { method, url, headers, body: bodyOut, bodyType, bodyContentType }
}

/** 解析 "Name: Value" 形式的 header，非法则忽略 */
function pushHeader(headers, hv) {
  if (!hv) return
  const idx = hv.indexOf(':')
  if (idx > 0) headers.push({ k: hv.slice(0, idx).trim(), v: hv.slice(idx + 1).trim() })
}

function parseUrlencoded(s) {
  const rows = []
  for (const pair of String(s || '').split('&')) {
    if (!pair) continue
    const idx = pair.indexOf('=')
    const k = idx >= 0 ? pair.slice(0, idx) : pair
    const v = idx >= 0 ? pair.slice(idx + 1) : ''
    try {
      rows.push({ k: decodeURIComponent(k.replace(/\+/g, ' ')), v: decodeURIComponent(v.replace(/\+/g, ' ')) })
    } catch (e) {
      rows.push({ k, v })
    }
  }
  return rows
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
      if (c === '\\') {
        // 行连接符：反斜杠紧跟换行 → 整对字符丢弃（等价于 shell 的续行），
        // 否则把下一个字符按转义取字面量
        const nxt = s[i + 1]
        if (nxt === '\n') { i++; continue }
        if (nxt === '\r') { i++; if (s[i + 1] === '\n') i++; continue }
        cur += nxt || ''
        i++
        continue
      }
      if (/\s/.test(c)) { if (cur) { out.push(cur); cur = '' } } else cur += c
    }
  }
  if (cur) out.push(cur)
  return out
}
