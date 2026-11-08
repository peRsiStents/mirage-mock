#!/usr/bin/env node
/**
 * 文件/图片 ⇄ Base64 工具的纯逻辑回归测试。
 *
 * 用 Node 直接跑前端 util（mirage-ui/src/utils/filebase64.js）：
 *   node .tools/verify-file-base64.js
 *
 * 覆盖：往返一致性、data URL 前缀、URL-safe 变体、缺失填充、含换行/空白、
 *       非法输入报错、魔数识别 MIME。
 */
import { fileURLToPath, pathToFileURL } from 'url'
import path from 'path'

const here = path.dirname(fileURLToPath(import.meta.url))
const util = await import(pathToFileURL(path.join(here, '..', 'mirage-ui', 'src', 'utils', 'filebase64.js')).href)
const { normalizeB64, guessMime, guessMimeByName, bytesToB64, bytesToHex, wrap, formatBytes } = util

let pass = 0
let fail = 0

function check(name, cond, extra) {
  if (cond) {
    pass++
    console.log('  PASS  ' + name)
  } else {
    fail++
    console.log('  FAIL  ' + name + (extra !== undefined ? '  -> ' + extra : ''))
  }
}

function expectThrow(name, fn) {
  try {
    fn()
    check(name, false, '未抛异常')
  } catch (e) {
    check(name, true)
  }
}

console.log('== 往返一致性 ==')
const samples = [
  ['ASCII', new TextEncoder().encode('hello mirage')],
  ['空字节', new Uint8Array(0)],
  ['中文 UTF-8', new TextEncoder().encode('蜃楼 Mock 测试')],
  ['二进制 0..255', Uint8Array.from({ length: 256 }, (_, i) => i)],
  ['PNG 头', new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3])]
]
for (const [name, bytes] of samples) {
  const b64 = bytesToB64(bytes)
  const back = normalizeB64(b64).bytes
  const same = back.length === bytes.length && back.every((v, i) => v === bytes[i])
  check('往返一致: ' + name, same, 'len ' + back.length + ' vs ' + bytes.length)
}

console.log('== 输入形态容错 ==')
const hello = new TextEncoder().encode('hello mirage')
const b64hello = bytesToB64(hello)
check('去空白/换行', normalizeB64('  ' + b64hello.slice(0, 4) + '\n' + b64hello.slice(4) + '  ').bytes.length === hello.length)
check('data URL 前缀剥离 + 记 MIME',
  normalizeB64('data:text/plain;base64,' + b64hello).mime === 'text/plain' &&
  normalizeB64('data:text/plain;base64,' + b64hello).bytes.length === hello.length)
check('URL-safe 变体还原', normalizeB64(b64hello.replace(/\+/g, '-').replace(/\//g, '_')).bytes.length === hello.length)
{
  const noPad = b64hello.replace(/=+$/, '')
  check('缺失填充自动补齐', normalizeB64(noPad).bytes.length === hello.length)
}
check('空串解码为空', normalizeB64('').bytes.length === 0)

console.log('== 非法输入必须报错（不能静默产出垃圾） ==')
expectThrow('长度余 1', () => normalizeB64('QUJD R'))
expectThrow('含非法字符', () => normalizeB64('abc$def'))
expectThrow('含中文', () => normalizeB64('你好啊'))

console.log('== 魔数识别 MIME ==')
const cases = [
  [[0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a], 'image/png'],
  [[0xff, 0xd8, 0xff, 0xe0, 0, 0], 'image/jpeg'],
  [[0x47, 0x49, 0x46, 0x38, 0x39, 0x61], 'image/gif'],
  [[0x42, 0x4d, 0, 0], 'image/bmp'],
  [[0x25, 0x50, 0x44, 0x46, 0x2d], 'application/pdf'],
  [[0x50, 0x4b, 0x03, 0x04, 0, 0], 'application/zip'],
  [[0x1f, 0x8b, 0x08, 0x00], 'application/gzip'],
  [[0x49, 0x44, 0x33, 0x04], 'audio/mpeg']
]
for (const [bytes, mime] of cases) {
  const got = guessMime(new Uint8Array(bytes))
  check('魔数 ' + mime, got === mime, 'got ' + got)
}
check('未知类型返回 null', guessMime(new Uint8Array([1, 2, 3, 4])) === null)
check('扩展名猜类型', guessMimeByName('a/b/photo.PNG') === 'image/png')
check('扩展名猜类型(未知)', guessMimeByName('x.unknown') === '')

console.log('== 辅助函数 ==')
check('wrap 每 4 列', wrap('abcdefg', 4) === 'abcd\nefg')
check('wrap 0 列不换行', wrap('abcdefg', 0) === 'abcdefg')
check('formatBytes', formatBytes(0) === '0 B' && formatBytes(2048) === '2.0 KB' && formatBytes(1024 * 1024 * 3) === '3.00 MB')
check('bytesToHex', bytesToHex(new Uint8Array([0x00, 0x0f, 0xff])) === '000fff')

console.log('')
console.log(fail === 0 ? `全部通过：${pass} 项` : `失败 ${fail} 项 / 共 ${pass + fail} 项`)
process.exit(fail === 0 ? 0 : 1)
