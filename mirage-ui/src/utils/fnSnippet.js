/**
 * 函数片段：从函数市场条目生成可直接插入输入框的 ${...} 文本。
 *
 * 抽成独立模块，供「函数市场侧栏」与「参数快捷引用」共用，避免两处逻辑漂移。
 */

/** 加解密/签名类函数需要密钥别名，插入时用占位别名 */
export const PLACEHOLDER_ALIAS = 'key_alias'

/**
 * 从函数 example 里取首个 ${...} 片段（带示例参数）。
 * 避免插入光秃秃的 ${fn}（如 ${int}）导致运行报错。
 */
export function firstSnippet(f) {
  const alt = (f.example || '').split(' / ')[0].trim()
  const start = alt.indexOf('${')
  if (start < 0) {
    return '${' + f.name + '}'
  }
  let depth = 0
  for (let i = start + 2; i < alt.length; i++) {
    if (alt[i] === '{') {
      depth++
    } else if (alt[i] === '}') {
      if (depth === 0) {
        return alt.slice(start, i + 1)
      }
      depth--
    }
  }
  return alt
}

/** 加解密/签名函数：${fn(${field.xxx}, 'alias')}，data 占位由用户替换 */
export function snippetFor(f, alias) {
  // sm2_verify 需三参：原文、签名、公钥别名
  if (f.name === 'sm2_verify') {
    return "${sm2_verify(${field.原文}, ${field.签名}, '" + alias + "')}"
  }
  return '${' + f.name + "(${field.xxx}, '" + alias + "')}"
}

/** 通用入口：按分类给出可直接插入的片段（加解密类带占位别名） */
export function snippetForFunction(f) {
  if (f.category === '加解密签名') {
    return snippetFor(f, PLACEHOLDER_ALIAS)
  }
  return firstSnippet(f)
}
