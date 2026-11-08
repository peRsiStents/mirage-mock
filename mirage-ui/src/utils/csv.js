/**
 * CSV ⇄ JSON 转换（纯前端）。
 *
 * 测试场景常见：造一批测试数据（CSV 表格 → JSON 数组）、
 * 把接口返回的数组导成 CSV 贴到 Excel 里。
 *
 * 解析按 RFC 4180 处理：双引号包裹的字段里可以有逗号、换行，
 * 双引号用两个双引号转义（""）。
 */

/** 解析 CSV 文本为二维数组（每行一个数组）。 */
export function parseCsv(text, delimiter = ',') {
  const src = String(text ?? '')
  const d = delimiter || ','
  const rows = []
  let row = []
  let field = ''
  let inQuotes = false
  let i = 0
  while (i < src.length) {
    const c = src[i]
    if (inQuotes) {
      if (c === '"') {
        if (src[i + 1] === '"') {
          field += '"'
          i += 2
          continue
        }
        inQuotes = false
        i++
        continue
      }
      field += c
      i++
      continue
    }
    if (c === '"' && field === '') {
      inQuotes = true
      i++
      continue
    }
    if (c === d) {
      row.push(field)
      field = ''
      i++
      continue
    }
    if (c === '\r') {
      i++
      continue
    }
    if (c === '\n') {
      row.push(field)
      rows.push(row)
      row = []
      field = ''
      i++
      continue
    }
    field += c
    i++
  }
  // 结尾没有换行时补上最后一行（末尾多一个分隔符也要保留空字段）
  if (field !== '' || row.length > 0) {
    row.push(field)
    rows.push(row)
  }
  return rows
}

/**
 * CSV → JSON 数组。
 *
 * @param {string} text
 * @param {object} opts {delimiter, hasHeader, typed} typed=true 时把纯数字/布尔转成对应类型
 */
export function csvToJson(text, opts = {}) {
  const { delimiter = ',', hasHeader = true, typed = false } = opts
  const rows = parseCsv(text, delimiter)
  if (!rows.length) return []
  // 丢掉完全空白的行
  const clean = rows.filter((r) => r.some((c) => String(c).trim() !== ''))
  if (!clean.length) return []
  let header
  let body
  if (hasHeader) {
    header = clean[0].map((h) => String(h).trim())
    body = clean.slice(1)
  } else {
    const width = clean.reduce((m, r) => Math.max(m, r.length), 0)
    header = Array.from({ length: width }, (_, i) => 'col' + (i + 1))
    body = clean
  }
  return body.map((r) => {
    const o = {}
    header.forEach((h, i) => {
      const raw = r[i] === undefined ? '' : r[i]
      o[h] = typed ? autoType(raw) : raw
    })
    return o
  })
}

/** 纯数字 → Number，true/false → Boolean，其余原样。 */
function autoType(v) {
  const s = String(v).trim()
  if (s === '') return ''
  if (/^-?\d+$/.test(s)) {
    const n = Number(s)
    // 保留前导零（如 007）当字符串，避免把编号变成数字
    return /^-?0\d/.test(s) ? s : n
  }
  if (/^-?\d+\.\d+$/.test(s)) return Number(s)
  if (s === 'true') return true
  if (s === 'false') return false
  if (s === 'null') return null
  return v
}

/**
 * JSON → CSV 文本。
 *
 * 列名取所有对象键的并集（按首次出现顺序）。数组/对象类型的值序列化成 JSON 字符串。
 */
export function jsonToCsv(value, opts = {}) {
  const { delimiter = ',' } = opts
  let arr = value
  if (!Array.isArray(arr)) {
    if (arr && typeof arr === 'object') arr = [arr]
    else throw new Error('待转换的 JSON 必须是对象或数组')
  }
  const cols = []
  for (const o of arr) {
    if (o && typeof o === 'object' && !Array.isArray(o)) {
      Object.keys(o).forEach((k) => {
        if (!cols.includes(k)) cols.push(k)
      })
    }
  }
  if (!cols.length) return ''
  const esc = (v) => {
    let s
    if (v === null || v === undefined) s = ''
    else if (typeof v === 'object') s = JSON.stringify(v)
    else s = String(v)
    if (s.includes('"') || s.includes(delimiter) || s.includes('\n') || s.includes('\r')) {
      s = '"' + s.replace(/"/g, '""') + '"'
    }
    return s
  }
  const lines = [cols.map(esc).join(delimiter)]
  for (const o of arr) {
    const obj = o && typeof o === 'object' && !Array.isArray(o) ? o : {}
    lines.push(cols.map((c) => esc(obj[c])).join(delimiter))
  }
  return lines.join('\n')
}
