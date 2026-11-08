/**
 * 随机数原语（纯函数，便于用 Node 跑单测）。
 *
 * 与 random.js / idcard.js 分开：身份证生成要用到随机数，
 * 而 random.js 又要复用身份证的校验位算法——放一起会形成循环依赖。
 */

/** 字符集预设 */
export const CHARSETS = {
  digit: '0123456789',
  lower: 'abcdefghijklmnopqrstuvwxyz',
  upper: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
  alpha: '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ',
  symbol: '!@#$%^&*()-_=+[]{}<>?/',
  hex: '0123456789abcdef'
}

/** 可复现随机数（mulberry32）：同种子出同一串，便于用例稳定复现 */
export function seededRandom(seed) {
  let a = typeof seed === 'number' ? seed >>> 0 : hashString(String(seed))
  return function () {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function hashString(s) {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** 从字符集里取随机字符 */
export function randomFrom(charset, rnd) {
  const r = rnd || Math.random
  return charset[Math.floor(r() * charset.length)]
}

/** 随机字符串 */
export function randomString(len, charset = CHARSETS.alpha, rnd) {
  const n = Math.max(0, Math.floor(len) || 0)
  let out = ''
  for (let i = 0; i < n; i++) out += randomFrom(charset, rnd)
  return out
}

/** 随机整数 [min, max]（闭区间） */
export function randomInt(min, max, rnd) {
  const r = rnd || Math.random
  let lo = Math.ceil(Math.min(min, max))
  let hi = Math.floor(Math.max(min, max))
  if (hi < lo) hi = lo
  return lo + Math.floor(r() * (hi - lo + 1))
}

/** 随机小数，保留 decimals 位 */
export function randomFloat(min, max, decimals = 2, rnd) {
  const p = Math.pow(10, Math.max(0, Math.min(10, decimals)))
  return Math.round(randomInt(Math.round(min * p), Math.round(max * p), rnd)) / p
}
