/**
 * 随机测试数据生成（纯函数，便于用 Node 跑单测）。
 *
 * 全部走 Math.random + 可选种子，用于造测试参数：手机号、身份证、银行卡等
 * 必须**带校验位合法**（很多接口会校验），所以这里不是纯随机串。
 *
 * 随机数原语在 random-core.js，身份证相关（地区/年龄/性别/校验位）在 idcard.js。
 * 这里只做聚合与统一入口。
 */
import { CHARSETS, seededRandom, randomFrom, randomString, randomInt, randomFloat } from './random-core.js'
import { randomIdCard, idChecksum, parseIdCard, REGIONS, GENDERS, findRegion } from './idcard.js'

export { CHARSETS, seededRandom, randomFrom, randomString, randomInt, randomFloat }
export { randomIdCard, idChecksum, parseIdCard, REGIONS, GENDERS, findRegion }

/** 随机手机号（中国大陆，1[3-9] 开头） */
export function randomPhone(rnd) {
  const prefixes = ['133', '135', '136', '137', '138', '139', '150', '151', '152', '155', '156',
    '158', '159', '166', '173', '175', '176', '177', '178', '180', '181', '182', '183', '185',
    '186', '187', '188', '189', '191', '193', '198', '199']
  const p = prefixes[randomInt(0, prefixes.length - 1, rnd)]
  return p + randomString(8, CHARSETS.digit, rnd)
}

/** 随机邮箱 */
export function randomEmail(rnd) {
  const domains = ['example.com', 'test.com', 'mock.dev', 'qa.cn', 'demo.org']
  const name = randomString(randomInt(6, 12, rnd), CHARSETS.lower, rnd)
  const d = domains[randomInt(0, domains.length - 1, rnd)]
  return name + '@' + d
}

/** 随机银行卡号（16 位，含 Luhn 校验位） */
export function randomBankCard(rnd) {
  let body = ''
  for (let i = 0; i < 15; i++) body += randomFrom(CHARSETS.digit, rnd)
  return body + luhnChecksum(body)
}

/** Luhn 校验位 */
export function luhnChecksum(body) {
  let sum = 0
  let double = true
  for (let i = body.length - 1; i >= 0; i--) {
    let d = Number(body.charAt(i))
    if (double) {
      d *= 2
      if (d > 9) d -= 9
    }
    double = !double
    sum += d
  }
  return String((10 - (sum % 10)) % 10)
}

/** 随机姓名（中文） */
export function randomName(rnd) {
  const surnames = '王李张刘陈杨黄赵周吴徐孙马朱胡林郭何高罗郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘蒋蔡余杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤'
  const given = '伟芳娜秀英敏静丽强磊军洋勇艳杰娟涛明超秀霞平刚桂英建华文博宇轩浩然子涵梓萱欣怡雨泽思远志强晓东红梅'
  const s = surnames[randomInt(0, surnames.length - 1, rnd)]
  const g1 = given[randomInt(0, given.length - 1, rnd)]
  const g2 = given[randomInt(0, given.length - 1, rnd)]
  return s + g1 + g2
}

/** 随机日期时间字符串（yyyy-MM-dd HH:mm:ss），落在 [startDaysAgo, endDaysAgo] 天前 */
export function randomDateTime(startDaysAgo = 30, endDaysAgo = 0, rnd) {
  const r = rnd || Math.random
  const now = Date.now()
  const dayMs = 24 * 3600 * 1000
  const lo = now - Math.max(startDaysAgo, endDaysAgo) * dayMs
  const hi = now - Math.min(startDaysAgo, endDaysAgo) * dayMs
  const t = lo + Math.floor(r() * Math.max(1, hi - lo))
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' +
    p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
}

/** 随机 UUID v4 */
export function randomUuid(rnd) {
  const r = rnd || Math.random
  const hex = '0123456789abcdef'
  let s = ''
  for (let i = 0; i < 36; i++) {
    if (i === 8 || i === 13 || i === 18 || i === 23) s += '-'
    else if (i === 14) s += '4'
    else if (i === 19) s += hex[(Math.floor(r() * 16) & 0x3) | 0x8]
    else s += hex[Math.floor(r() * 16)]
  }
  return s
}

/**
 * 统一入口：按类型生成一条数据。
 *
 * @param {string} type  类型（见 RandomTool.vue 的 TYPE 列表）
 * @param {object} opts  参数（长度、字符集、数值范围、日期范围、身份证的地区/年龄/性别、种子等）
 * @param {function} rnd 随机源（可选，用于种子复现）
 */
export function generate(type, opts = {}, rnd) {
  const o = opts || {}
  switch (type) {
    case 'string':
      return randomString(o.len || 10, o.charset || CHARSETS.alpha, rnd)
    case 'int':
      return String(randomInt(o.min == null ? 1 : o.min, o.max == null ? 1000 : o.max, rnd))
    case 'float':
      return String(randomFloat(o.min == null ? 0 : o.min, o.max == null ? 100 : o.max, o.decimals == null ? 2 : o.decimals, rnd))
    case 'phone':
      return randomPhone(rnd)
    case 'idcard':
      return randomIdCard({
        provinceCode: o.provinceCode,
        cityCode: o.cityCode,
        minAge: o.minAge,
        maxAge: o.maxAge,
        gender: o.gender
      }, rnd)
    case 'email':
      return randomEmail(rnd)
    case 'bankcard':
      return randomBankCard(rnd)
    case 'name':
      return randomName(rnd)
    case 'datetime':
      return randomDateTime(o.startDaysAgo == null ? 30 : o.startDaysAgo, o.endDaysAgo == null ? 0 : o.endDaysAgo, rnd)
    case 'uuid':
      return randomUuid(rnd)
    default:
      throw new Error('未知的生成类型：' + type)
  }
}
