/**
 * 字段命名风格转换（纯前端）。
 *
 * 接口入参/出参的字段命名风格经常不一致（后端 snake_case、前端 camelCase、
 * 配置项 kebab-case），造请求体、做数据映射时经常要整体换风格。
 */

/** 支持的风格。 */
export const STYLES = [
  { key: 'camel', name: 'camelCase（驼峰）', sample: 'userName' },
  { key: 'pascal', name: 'PascalCase（帕斯卡）', sample: 'UserName' },
  { key: 'snake', name: 'snake_case（下划线）', sample: 'user_name' },
  { key: 'kebab', name: 'kebab-case（中划线）', sample: 'user-name' },
  { key: 'constant', name: 'CONSTANT_CASE（全大写）', sample: 'USER_NAME' },
  { key: 'dot', name: 'dot.case（点分）', sample: 'user.name' }
]

/**
 * 把一个标识符拆成单词。
 * 支持分隔符（_ - 空格 . /）与驼峰边界；连续大写按「首字母缩写 + 单词」拆。
 */
export function splitWords(name) {
  const src = String(name ?? '')
  const parts = []
  let cur = ''
  for (const ch of src) {
    if (/[_\-\s./\\]/.test(ch)) {
      if (cur) { parts.push(cur); cur = '' }
    } else {
      cur += ch
    }
  }
  if (cur) parts.push(cur)
  const words = []
  for (const part of parts) {
    // 先匹配缩写（XMLHttpRequest → XML + Http + Request）
    const re = /[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|[0-9]+/g
    let m
    while ((m = re.exec(part)) !== null) words.push(m[0])
  }
  return words
}

function upperFirst(w) {
  return w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()
}

/**
 * 把标识符转成指定风格。
 *
 * 各风格都以「单词小写化」为基准再重新拼接，
 * 这样 USER_NAME / userName / UserName 转到 camel 都是 userName。
 */
export function convertCase(name, style) {
  const words = splitWords(name)
  if (!words.length) return ''
  const norm = words.map((w) => w.toLowerCase())
  switch (style) {
    case 'camel':
      return norm[0] + norm.slice(1).map(upperFirst).join('')
    case 'pascal':
      return norm.map(upperFirst).join('')
    case 'snake':
      return norm.join('_')
    case 'kebab':
      return norm.join('-')
    case 'constant':
      return norm.join('_').toUpperCase()
    case 'dot':
      return norm.join('.')
    default:
      return String(name ?? '')
  }
}

/**
 * 递归转换对象/数组的键名（值不变）。
 * 非对象原样返回。
 */
export function convertKeys(value, style) {
  if (Array.isArray(value)) return value.map((v) => convertKeys(v, style))
  if (value && typeof value === 'object') {
    const out = {}
    for (const k of Object.keys(value)) {
      out[convertCase(k, style)] = convertKeys(value[k], style)
    }
    return out
  }
  return value
}

/**
 * 批量转换纯文本（每行一个标识符，或用分隔符隔开的列表）。
 * @param {string} text
 * @param {string} style
 * @param {string} sep 行内分隔符（',' 或空格等）
 */
export function convertText(text, style, sep = '\n') {
  const lines = String(text ?? '')
    .split(sep)
    .map((s) => s.trim())
    .filter((s) => s !== '')
  return lines.map((l) => convertCase(l, style)).join(sep)
}
