/**
 * 文件 / 图片 ⇄ Base64 的纯函数工具（无浏览器依赖部分）。
 *
 * 抽出来单独放，便于用 Node 直接跑单测（.tools/verify-file-base64.js），
 * 也方便别处复用（例如把 Base64 塞进 Mock 规则的响应体）。
 */

/** 按扩展名猜 MIME（本地文件常常没有 type）。 */
export function guessMimeByName(name) {
  const m = /\.([a-z0-9]+)$/i.exec(name || '')
  const ext = m ? m[1].toLowerCase() : ''
  const map = {
    png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp',
    bmp: 'image/bmp', svg: 'image/svg+xml', ico: 'image/x-icon', tif: 'image/tiff', tiff: 'image/tiff',
    pdf: 'application/pdf', zip: 'application/zip', gz: 'application/gzip', txt: 'text/plain',
    json: 'application/json', csv: 'text/csv', xml: 'application/xml', html: 'text/html'
  }
  return map[ext] || ''
}

/** 按文件头魔数猜 MIME；识别不了返回 null。 */
export function guessMime(bytes) {
  if (!bytes || bytes.length < 4) return null
  const hex = (n) => bytes[n].toString(16).padStart(2, '0')
  const head4 = hex(0) + hex(1) + hex(2) + hex(3)
  const ascii = (n, len) => String.fromCharCode.apply(null, Array.from(bytes.slice(n, n + len)))
  if (head4 === '89504e47') return 'image/png'
  if (head4 === 'ffd8ffe0' || head4 === 'ffd8ffe1' || head4 === 'ffd8ffdb') return 'image/jpeg'
  if (ascii(0, 3) === 'GIF') return 'image/gif'
  if (ascii(0, 4) === 'RIFF' && ascii(8, 4) === 'WEBP') return 'image/webp'
  if (ascii(0, 2) === 'BM') return 'image/bmp'
  if (head4 === '49492a00' || head4 === '4d4d002a') return 'image/tiff'
  if (head4 === '00000100') return 'image/x-icon'
  if (ascii(0, 4) === '%PDF') return 'application/pdf'
  if (head4 === '504b0304' || head4 === '504b0506') return 'application/zip'
  if (head4 === '1f8b0800') return 'application/gzip'
  if (ascii(0, 6) === '377abcaf') return 'application/x-7z-compressed'
  if (ascii(0, 4) === 'Rar!') return 'application/vnd.rar'
  if (ascii(4, 4) === 'ftyp') return 'video/mp4'
  if (ascii(0, 3) === 'ID3') return 'audio/mpeg'
  if (ascii(0, 4) === 'RIFF' && ascii(8, 4) === 'WAVE') return 'audio/wav'
  if (ascii(0, 5) === '<?xml' || ascii(0, 4) === '<svg') return 'image/svg+xml'
  return null
}

/**
 * 归一化 Base64 并解码成字节：
 * 去 data URL 前缀（记下其中的 MIME）→ 去空白 → URL-safe 字母表还原 → 补齐填充。
 *
 * @returns {{bytes: Uint8Array, mime: string|null}}
 * @throws {Error} 非法输入（长度余 1、含非 Base64 字符等）
 */
export function normalizeB64(input) {
  let t = String(input == null ? '' : input).trim()
  let mime = null
  const m = /^data:([^;,]*)(;[^,]*)?,/i.exec(t)
  if (m) {
    mime = m[1] || null
    t = t.slice(m[0].length)
  }
  t = t.replace(/\s+/g, '')
  t = t.replace(/-/g, '+').replace(/_/g, '/')
  const rem = t.length % 4
  if (rem === 1) throw new Error('长度非法：Base64 长度除以 4 余 1')
  if (rem) t += '='.repeat(4 - rem)
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(t)) throw new Error('包含非 Base64 字符')
  let bin
  try {
    bin = atob(t)
  } catch (e) {
    throw new Error('字符集非法')
  }
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return { bytes, mime }
}

/** 字节数组 → Base64（浏览器与 Node 均可）。 */
export function bytesToB64(bytes) {
  let bin = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode.apply(null, Array.from(bytes.slice(i, i + chunk)))
  }
  return btoa(bin)
}

/** 按固定列宽换行。 */
export function wrap(text, cols) {
  if (!cols || cols <= 0) return text
  const lines = []
  for (let i = 0; i < text.length; i += cols) lines.push(text.slice(i, i + cols))
  return lines.join('\n')
}

/** 字节数 → 可读大小。 */
export function formatBytes(n) {
  if (n == null) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

/** 字节数组 → 小写 hex 串。 */
export function bytesToHex(bytes) {
  let hex = ''
  for (let i = 0; i < bytes.length; i++) hex += bytes[i].toString(16).padStart(2, '0')
  return hex
}
