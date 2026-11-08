#!/usr/bin/env node
/**
 * 测试助手工具（正则/JSONPath/Cron/随机数据/文本对比/Diff）的纯逻辑回归测试。
 *
 *   node .tools/verify-test-tools.js
 *
 * 覆盖：Diff 的增删改判定与顺序、随机数据的合法性（身份证校验位、银行卡 Luhn、
 * 手机号段、UUID 形态、种子可复现）。
 */
import { fileURLToPath, pathToFileURL } from 'url'
import path from 'path'

const here = path.dirname(fileURLToPath(import.meta.url))
const load = async (p) => import(pathToFileURL(path.join(here, '..', 'mirage-ui', 'src', 'utils', p)).href)
const { diffLines } = await load('diff.js')
const R = await load('random.js')

let pass = 0
let fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS  ' + name) }
  else { fail++; console.log('  FAIL  ' + name + (extra !== undefined ? '  -> ' + extra : '')) }
}

console.log('== 文本 Diff ==')
{
  const d = diffLines('a\nb\nc', 'a\nb\nc')
  check('完全相同：无增删', d.added === 0 && d.removed === 0 && d.same === 3, JSON.stringify(d))

  const d2 = diffLines('a\nb\nc', 'a\nX\nc')
  check('替换一行 = 1 增 1 删', d2.added === 1 && d2.removed === 1, JSON.stringify({ a: d2.added, r: d2.removed }))

  const d3 = diffLines('a\nb', 'a\nb\nc\nd')
  check('尾部新增 2 行', d3.added === 2 && d3.removed === 0, JSON.stringify({ a: d3.added, r: d3.removed }))

  const d4 = diffLines('x\ny\nz', 'a\nb\nc')
  check('整体替换 = 3 增 3 删', d4.added === 3 && d4.removed === 3, JSON.stringify({ a: d4.added, r: d4.removed }))

  const d5 = diffLines('', 'a\nb')
  check('空 vs 两行 = 2 增', d5.added === 2 && d5.removed === 0)

  // 顺序：公共前后缀应保留为 same，且行号单调递增
  const d6 = diffLines('1\n2\n3\n4', '1\n2\nX\n4')
  const sameRows = d6.rows.filter((r) => r.type === 'same')
  check('公共前后缀保留', sameRows.length === 3, 'same=' + sameRows.length)
  check('左行号单调递增', d6.rows.every((r, i) => i === 0 || r.left === null || r.left > d6.rows[i - 1].left || d6.rows[i - 1].left === null))

  // CRLF 应与 LF 等价
  const d7 = diffLines('a\r\nb', 'a\nb')
  check('CRLF/LF 等价（无差异）', d7.added === 0 && d7.removed === 0, JSON.stringify({ a: d7.added, r: d7.removed }))
}

console.log('== 随机数据合法性 ==')
{
  const phone = R.randomPhone()
  check('手机号格式 1[3-9]xxxxxxxxx', /^1[3-9]\d{9}$/.test(phone), phone)

  const id = R.randomIdCard()
  check('身份证 18 位', /^\d{17}[\dX]$/.test(id), id)
  check('身份证校验位正确', R.idChecksum(id.slice(0, 17)) === id.slice(17), id)

  const card = R.randomBankCard()
  check('银行卡 16 位数字', /^\d{16}$/.test(card), card)
  check('银行卡 Luhn 校验位正确', R.luhnChecksum(card.slice(0, 15)) === card.slice(15), card)

  const mail = R.randomEmail()
  check('邮箱格式', /^[\w.+-]+@[\w-]+\.[\w.]+$/.test(mail), mail)

  const uuid = R.randomUuid()
  check('UUID v4 形态', /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(uuid), uuid)

  const dt = R.randomDateTime(10, 0)
  check('日期时间格式', /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(dt), dt)

  const name = R.randomName()
  check('中文姓名长度 2-3', name.length >= 2 && name.length <= 3, name)
}

console.log('== 种子可复现 ==')
{
  const r1 = R.seededRandom('case-001')
  const r2 = R.seededRandom('case-001')
  const r3 = R.seededRandom('case-002')
  const a = Array.from({ length: 5 }, () => R.randomString(8, R.CHARSETS.alpha, r1))
  const b = Array.from({ length: 5 }, () => R.randomString(8, R.CHARSETS.alpha, r2))
  const c = Array.from({ length: 5 }, () => R.randomString(8, R.CHARSETS.alpha, r3))
  check('同种子结果一致', JSON.stringify(a) === JSON.stringify(b))
  check('不同种子结果不同', JSON.stringify(a) !== JSON.stringify(c))
}

console.log('== generate 统一入口 ==')
{
  const types = ['string', 'int', 'float', 'phone', 'idcard', 'email', 'bankcard', 'name', 'datetime', 'uuid']
  for (const t of types) {
    let v = null
    let err = null
    try {
      v = R.generate(t, { len: 12, charset: R.CHARSETS.digit, min: 5, max: 9, decimals: 3 })
    } catch (e) {
      err = e
    }
    check('generate ' + t, !err && v !== null && String(v).length > 0, err ? err.message : String(v))
  }
  check('字符串长度可控', R.generate('string', { len: 12, charset: R.CHARSETS.digit }).length === 12)
  check('整数在区间内', (() => {
    const n = Number(R.generate('int', { min: 5, max: 9 }))
    return n >= 5 && n <= 9
  })())
}

console.log('')
console.log(fail === 0 ? `全部通过：${pass} 项` : `失败 ${fail} 项 / 共 ${pass + fail} 项`)
process.exit(fail === 0 ? 0 : 1)
