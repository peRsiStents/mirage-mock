#!/usr/bin/env node
/**
 * 身份证生成（地区 / 年龄段 / 性别）的回归测试。
 *
 *   node .tools/verify-idcard.js
 */
import { fileURLToPath, pathToFileURL } from 'url'
import path from 'path'

const here = path.dirname(fileURLToPath(import.meta.url))
const load = async (p) => import(pathToFileURL(path.join(here, '..', 'mirage-ui', 'src', 'utils', p)).href)
const R = await load('random.js')

let pass = 0
let fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS  ' + name) }
  else { fail++; console.log('  FAIL  ' + name + (extra !== undefined ? '  -> ' + extra : '')) }
}

console.log('== 地区码 ==')
{
  const ids = Array.from({ length: 30 }, () => R.randomIdCard({ provinceCode: '44' }))
  check('指定省份：前 2 位都是 44', ids.every((id) => id.slice(0, 2) === '44'), ids[0])
  check('指定省份：地区码能反查到广东', ids.every((id) => R.findRegion(id.slice(0, 6)).province === '广东省'),
    R.findRegion(ids[0].slice(0, 6)).province)

  const cityIds = Array.from({ length: 30 }, () => R.randomIdCard({ provinceCode: '33', cityCode: '3301' }))
  check('指定城市：前 4 位都是 3301', cityIds.every((id) => id.slice(0, 4) === '3301'), cityIds[0])
  check('指定城市：反查到杭州市', cityIds.every((id) => R.findRegion(id.slice(0, 6)).city === '杭州市'),
    R.findRegion(cityIds[0].slice(0, 6)).city)

  const onlyCity = Array.from({ length: 20 }, () => R.randomIdCard({ cityCode: '4403' }))
  check('只给市代码：省也能对上（深圳=广东）',
    onlyCity.every((id) => id.slice(0, 2) === '44' && id.slice(2, 4) === '03'), onlyCity[0])

  const auto = Array.from({ length: 50 }, () => R.randomIdCard())
  check('不指定地区：6 位码都能反查到省份', auto.every((id) => !!R.findRegion(id.slice(0, 6))), auto[0])

  check('地区码第 5-6 位不为 00', auto.every((id) => id.slice(4, 6) !== '00'), auto[0])
}

console.log('== 年龄段 ==')
{
  const ids = Array.from({ length: 40 }, () => R.randomIdCard({ minAge: 20, maxAge: 25 }))
  const ages = ids.map((id) => R.parseIdCard(id).age)
  check('年龄都落在 20~25', ages.every((a) => a >= 20 && a <= 25), ages.join(','))

  const child = Array.from({ length: 20 }, () => R.randomIdCard({ minAge: 6, maxAge: 8 }))
  const childAges = child.map((id) => R.parseIdCard(id).age)
  check('儿童年龄段 6~8', childAges.every((a) => a >= 6 && a <= 8), childAges.join(','))

  const old = Array.from({ length: 20 }, () => R.randomIdCard({ minAge: 60, maxAge: 60 }))
  check('固定年龄 60', old.every((id) => R.parseIdCard(id).age === 60), old.map((id) => R.parseIdCard(id).age).join(','))

  // min > max 时应按 min 处理，而不是算出负区间
  const weird = Array.from({ length: 10 }, () => R.randomIdCard({ minAge: 30, maxAge: 10 }))
  check('min>max 时退化为 min', weird.every((id) => R.parseIdCard(id).age === 30),
    weird.map((id) => R.parseIdCard(id).age).join(','))
}

console.log('== 性别 ==')
{
  const male = Array.from({ length: 30 }, () => R.randomIdCard({ gender: 'male' }))
  check('男：第 17 位为奇数', male.every((id) => Number(id.charAt(16)) % 2 === 1), male[0])
  check('男：解析结果都是男', male.every((id) => R.parseIdCard(id).genderText === '男'))

  const female = Array.from({ length: 30 }, () => R.randomIdCard({ gender: 'female' }))
  check('女：第 17 位为偶数', female.every((id) => Number(id.charAt(16)) % 2 === 0), female[0])
  check('女：解析结果都是女', female.every((id) => R.parseIdCard(id).genderText === '女'))

  const rndIds = Array.from({ length: 60 }, () => R.randomIdCard({ gender: 'random' }))
  const both = new Set(rndIds.map((id) => R.parseIdCard(id).genderText))
  check('随机：男女都有', both.has('男') && both.has('女'), [...both].join(','))
}

console.log('== 解析 ==')
{
  // 110101 19800101 001 + 校验位
  const body = '11010119800101001'
  const id = body + R.idChecksum(body)
  const p = R.parseIdCard(id)
  check('地区名反查', p.areaName.includes('北京市'), p.areaName)
  check('出生日期', p.birth === '19800101', p.birth)
  check('校验位正确', p.checksumOk === true)
  check('整体有效', p.valid === true)

  const bad = R.parseIdCard('11010119800101001X')
  check('错误校验位被识别', bad.checksumOk === false && bad.valid === false)

  const short = R.parseIdCard('110101')
  check('非 18 位返回无效', short.valid === false)

  const future = R.parseIdCard('110101' + '20990101' + '001' + 'X')
  check('非法出生日期返回无效', future.valid === false)
}

console.log('== 其它生成项不受影响 ==')
{
  const id = R.randomIdCard()
  check('默认 18 位', /^\d{17}[\dX]$/.test(id), id)
  check('默认校验位正确', R.idChecksum(id.slice(0, 17)) === id.charAt(17))
  check('默认年龄在 18~60', (() => {
    const a = R.parseIdCard(id).age
    return a >= 18 && a <= 60
  })(), R.parseIdCard(id).age)

  // 种子可复现：同种子 + 同选项 → 同结果
  const a = R.randomIdCard({ provinceCode: '44', minAge: 30, maxAge: 40, gender: 'female' }, R.seededRandom('x'))
  const b = R.randomIdCard({ provinceCode: '44', minAge: 30, maxAge: 40, gender: 'female' }, R.seededRandom('x'))
  check('同种子同选项结果一致', a === b, a + ' vs ' + b)

  // generate() 统一入口也支持新参数
  const g = R.generate('idcard', { provinceCode: '11', cityCode: '1101', minAge: 50, maxAge: 50, gender: 'male' })
  check('generate 入口支持新参数', g.slice(0, 4) === '1101' && R.parseIdCard(g).age === 50 && R.parseIdCard(g).genderText === '男', g)
}

console.log('')
console.log(fail === 0 ? `全部通过：${pass} 项` : `失败 ${fail} 项 / 共 ${pass + fail} 项`)
process.exit(fail === 0 ? 0 : 1)
