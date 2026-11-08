#!/usr/bin/env node
/**
 * 第二批测试工具（CSV⇄JSON / 字段风格 / URL）的纯逻辑回归测试。
 *
 *   node .tools/verify-more-tools.js
 */
import { fileURLToPath, pathToFileURL } from 'url'
import path from 'path'

const here = path.dirname(fileURLToPath(import.meta.url))
const load = async (p) => import(pathToFileURL(path.join(here, '..', 'mirage-ui', 'src', 'utils', p)).href)
const CSV = await load('csv.js')
const NC = await load('namecase.js')
const U = await load('urltool.js')

let pass = 0
let fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS  ' + name) }
  else { fail++; console.log('  FAIL  ' + name + (extra !== undefined ? '  -> ' + extra : '')) }
}

console.log('== CSV 解析 ==')
{
  const rows = CSV.parseCsv('a,b,c\n1,2,3')
  check('普通两行', rows.length === 2 && rows[0].join(',') === 'a,b,c' && rows[1].join(',') === '1,2,3',
    JSON.stringify(rows))

  const q = CSV.parseCsv('x,"含,逗号",z')
  check('引号内逗号不被拆列', q[0].length === 3 && q[0][1] === '含,逗号', JSON.stringify(q))

  const e = CSV.parseCsv('x,"含""引号",z')
  check('双引号转义', e[0][1] === '含"引号', JSON.stringify(e))

  const nl = CSV.parseCsv('x,"第一行\n第二行",z')
  check('引号内换行不拆行', nl.length === 1 && nl[0].length === 3 && nl[0][1] === '第一行\n第二行', JSON.stringify(nl))

  const t = CSV.parseCsv('a,')
  check('结尾分隔符保留空字段', t[0].length === 2 && t[0][1] === '', JSON.stringify(t))

  const crlf = CSV.parseCsv('a,b\r\n1,2\r\n')
  check('CRLF 行尾', crlf.length === 2 && crlf[1][0] === '1', JSON.stringify(crlf))

  const tab = CSV.parseCsv('a\tb\n1\t2', '\t')
  check('自定义分隔符', tab[0].length === 2 && tab[0][1] === 'b', JSON.stringify(tab))
}

console.log('== CSV → JSON ==')
{
  const arr = CSV.csvToJson('id,name\n1,蜃楼\n2,Mock')
  check('表头做键', arr.length === 2 && arr[0].id === '1' && arr[1].name === 'Mock', JSON.stringify(arr))

  const noHeader = CSV.csvToJson('1,2\n3,4', { hasHeader: false })
  check('无表头自动列名', noHeader[0].col1 === '1' && noHeader[1].col2 === '4', JSON.stringify(noHeader))

  const typed = CSV.csvToJson('a,b,c,d\n007,10,true,null', { typed: true })
  check('类型识别：数字/布尔/null', typed[0].a === '007' && typed[0].b === 10 && typed[0].c === true && typed[0].d === null,
    JSON.stringify(typed))

  const untyped = CSV.csvToJson('a\n10', { typed: false })
  check('不自动识别时保持字符串', untyped[0].a === '10', JSON.stringify(untyped))

  const blank = CSV.csvToJson('a,b\n\n1,2')
  check('跳过空行', blank.length === 1, JSON.stringify(blank))

  const ragged = CSV.csvToJson('a,b,c\n1,2')
  check('列数不足补空串', ragged[0].c === '', JSON.stringify(ragged))
}

console.log('== JSON → CSV ==')
{
  const csv = CSV.jsonToCsv([{ id: 1, name: 'a,b' }, { id: 2, name: 'q"z' }])
  check('含逗号/引号的字段被引号包裹', csv.split('\n')[1] === '1,"a,b"' && csv.split('\n')[2] === '2,"q""z"',
    JSON.stringify(csv))

  const header = csv.split('\n')[0]
  check('表头取所有键', header === 'id,name', header)

  const nested = CSV.jsonToCsv([{ a: { b: 1 } }])
  check('对象值序列化成 JSON', nested.split('\n')[1] === '"{""b"":1}"', JSON.stringify(nested))

  const single = CSV.jsonToCsv({ a: 1 })
  check('单个对象也当一行', single.split('\n').length === 2, JSON.stringify(single))

  // 往返：CSV → JSON → CSV 应当还原
  const src = 'id,name\n1,"a,b"\n2,c'
  const back = CSV.jsonToCsv(CSV.csvToJson(src))
  check('往返一致', back === src, JSON.stringify(back))
}

console.log('== 字段风格转换 ==')
{
  check('snake 拆词', NC.splitWords('user_name').join(',') === 'user,name', NC.splitWords('user_name').join(','))
  check('camel 拆词', NC.splitWords('userName').join(',') === 'user,Name', NC.splitWords('userName').join(','))
  check('缩写拆词', NC.splitWords('XMLHttpRequest').join(',') === 'XML,Http,Request', NC.splitWords('XMLHttpRequest').join(','))
  check('混合分隔符', NC.splitWords('user-name_list').join(',') === 'user,name,list', NC.splitWords('user-name_list').join(','))

  check('snake → camel', NC.convertCase('user_name', 'camel') === 'userName', NC.convertCase('user_name', 'camel'))
  check('camel → snake', NC.convertCase('userName', 'snake') === 'user_name', NC.convertCase('userName', 'snake'))
  check('camel → kebab', NC.convertCase('userName', 'kebab') === 'user-name', NC.convertCase('userName', 'kebab'))
  check('camel → pascal', NC.convertCase('userName', 'pascal') === 'UserName', NC.convertCase('userName', 'pascal'))
  check('camel → constant', NC.convertCase('userName', 'constant') === 'USER_NAME', NC.convertCase('userName', 'constant'))
  check('camel → dot', NC.convertCase('userName', 'dot') === 'user.name', NC.convertCase('userName', 'dot'))
  check('大写词转小驼峰', NC.convertCase('USER_NAME', 'camel') === 'userName', NC.convertCase('USER_NAME', 'camel'))

  const conv = NC.convertKeys({ user_name: 'x', list: [{ item_id: 1 }] }, 'camel')
  check('递归转换键', conv.userName === 'x' && conv.list[0].itemId === 1, JSON.stringify(conv))

  check('数组根节点', Array.isArray(NC.convertKeys([{ a_b: 1 }], 'camel')) && NC.convertKeys([{ a_b: 1 }], 'camel')[0].aB === 1)

  check('文本批量转换', NC.convertText('user_name\norderList', 'camel', '\n') === 'userName\norderList',
    JSON.stringify(NC.convertText('user_name\norderList', 'camel', '\n')))
}

console.log('== URL 解析 / 拼装 ==')
{
  const u = U.parseUrl('https://api.example.com:8443/v1/order?id=1&name=a%20b#frag')
  check('协议/主机/端口', u.protocol === 'https' && u.hostname === 'api.example.com' && u.port === '8443',
    JSON.stringify(u))
  check('路径/参数/锚点', u.pathname === '/v1/order' && u.params.length === 2 && u.hash === '#frag', JSON.stringify(u))
  check('参数值已解码', u.params[1].value === 'a b', JSON.stringify(u.params))

  const rel = U.parseUrl('/api/v1/x?a=1')
  check('相对路径也能解析', rel.ok && rel.pathname === '/api/v1/x' && rel.params[0].value === '1', JSON.stringify(rel))

  check('空 URL 报错', U.parseUrl('').ok === false)

  const q = U.queryToJson('a=1&a=2&b=3')
  check('同名参数收敛成数组', Array.isArray(q.a) && q.a.length === 2 && q.b === '3', JSON.stringify(q))

  const qs = U.jsonToQuery({ a: ['1', '2'], b: 3 })
  check('数组展开成重复 key', qs === 'a=1&a=2&b=3', qs)

  const built = U.buildUrl('https://api.example.com', '/v1/order', { id: 1, name: 'a b' })
  check('拼装 URL', built === 'https://api.example.com/v1/order?id=1&name=a%20b', built)

  const built2 = U.buildUrl('https://api.example.com/', 'v1/order', {})
  check('斜杠不重复', built2 === 'https://api.example.com/v1/order', built2)
}

console.log('')
console.log(fail === 0 ? `全部通过：${pass} 项` : `失败 ${fail} 项 / 共 ${pass + fail} 项`)
process.exit(fail === 0 ? 0 : 1)
