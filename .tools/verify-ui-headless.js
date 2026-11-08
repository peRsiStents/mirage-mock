#!/usr/bin/env node
/**
 * 用无头 Edge（CDP）验证前端页面真能渲染，不只是「编译通过」。
 *
 *   node .tools/verify-ui-headless.js
 *
 * 流程：启动 headless Edge → 连 CDP → 登录塞 token → 打开 #/tools →
 *       逐个点开子标签 → 真实操作每个工具 → 收集 console 报错 → 断言页面结果。
 *
 * 依赖：本机 Edge（Windows 默认路径）；Node 18+（内置 fetch / WebSocket）。
 */
import { spawn } from 'child_process'
import { mkdtempSync, rmSync } from 'fs'
import { tmpdir } from 'os'
import path from 'path'
import crypto from 'crypto'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const APP = process.env.APP_URL || 'http://localhost:9080'
// 用随机端口：避免上一轮残留的 Edge 实例占着固定端口，导致 CDP 连到旧浏览器
const PORT = 9300 + Math.floor(Math.random() * 400)

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function fail(msg) {
  console.error('FAIL: ' + msg)
  process.exitCode = 1
}

/** 各工具组件的根 class，用来把查询限定在「当前工具」内（页面上有多个同名按钮）。 */
const ROOT = {
  digest: '.dg', regex: '.rx', jsonpath: '.jp', cron: '.cr', random: '.rd',
  diff: '.df', hmac: '.hm', charset: '.cs', csv: '.cv', url: '.ul', case: '.nc'
}

async function waitForCdp() {
  for (let i = 0; i < 60; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/version`)
      if (r.ok) return await r.json()
    } catch (e) { /* 还没起来 */ }
    await sleep(500)
  }
  throw new Error('CDP 端口未就绪')
}

/** 极简 CDP 客户端 */
class Cdp {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.events = []
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data)
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id)
        this.pending.delete(msg.id)
        if (msg.error) reject(new Error(JSON.stringify(msg.error)))
        else resolve(msg.result)
      } else if (msg.method) {
        this.events.push(msg)
      }
    })
  }
  send(method, params = {}) {
    const id = ++this.id
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error('CDP 超时: ' + method))
        }
      }, 30000)
    })
  }
  async eval(expr) {
    const r = await this.send('Runtime.evaluate', {
      expression: expr,
      awaitPromise: true,
      returnByValue: true
    })
    if (r.exceptionDetails) {
      throw new Error('页面执行异常: ' + JSON.stringify(r.exceptionDetails.exception || r.exceptionDetails))
    }
    return r.result.value
  }
}

/** 点开某个标签页（按文本精确匹配 .el-tabs__item） */
async function openTab(cdp, label) {
  return cdp.eval(`(() => {
    const el = [...document.querySelectorAll('.el-tabs__item')].find(e => e.textContent.trim() === ${JSON.stringify(label)})
    if (el) { el.click(); return true }
    return false
  })()`)
}

/** 在指定工具组件内点按钮（按文本精确匹配） */
async function clickIn(cdp, root, label) {
  return cdp.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(root)})
    if (!r) return 'no-root'
    const btn = [...r.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(label)})
    if (btn) { btn.click(); return 'ok' }
    return 'not-found'
  })()`)
}

/** 在指定工具组件内点单选按钮（el-radio-button 渲染成 label，不是 button） */
async function clickRadioIn(cdp, root, label) {
  return cdp.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(root)})
    if (!r) return 'no-root'
    const lb = [...r.querySelectorAll('.el-radio-button')].find(e => e.textContent.trim() === ${JSON.stringify(label)})
    if (!lb) return 'not-found'
    const inner = lb.querySelector('.el-radio-button__inner') || lb
    inner.click()
    return 'ok'
  })()`)
}

/**
 * 给输入框/文本域赋值并触发 input 事件（Vue 的 v-model 只认 input 事件；
 * 直接改 .value 不会更新组件状态）。
 */
async function setValueIn(cdp, root, selector, value, index = 0) {
  return cdp.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(root)})
    if (!r) return 'no-root'
    const roots = r.querySelectorAll(${JSON.stringify(selector)})
    const root2 = roots[${index}]
    if (!root2) return 'not-found:' + ${JSON.stringify(selector)}
    // el-input 的 class 落在根 div 上，真正的 input 在里面
    let el = root2
    if (root2.tagName !== 'INPUT' && root2.tagName !== 'TEXTAREA') {
      el = root2.querySelector('input, textarea')
      if (!el) return 'no-input:' + ${JSON.stringify(selector)}
    }
    const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype
    const setter = Object.getOwnPropertyDescriptor(proto, 'value').set
    setter.call(el, ${JSON.stringify(value)})
    el.dispatchEvent(new Event('input', { bubbles: true }))
    return 'ok'
  })()`)
}

/** 读取指定工具组件内某个输入框/文本域的当前值（textarea 的内容不在 innerText 里，只能读 .value） */
async function getValueIn(cdp, root, selector, index = 0) {
  return cdp.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(root)})
    if (!r) return ''
    const els = r.querySelectorAll(${JSON.stringify(selector)})
    const el = els[${index}]
    return el ? (el.value === undefined ? el.textContent : el.value) : ''
  })()`)
}

/** 读取指定工具组件内的可见文本 */
async function textIn(cdp, root) {
  return cdp.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(root)})
    return r ? r.innerText : ''
  })()`)
}

/**
 * 选 Element Plus 下拉框的某一项：先点开下拉，再点可见的选项。
 * @param {string} root 组件根选择器
 * @param {number} selectIndex 组件内第几个 .el-select（从 0 开始）
 * @param {string} optionText 选项文本（精确匹配）
 */
async function selectOption(cdp, root, selectIndex, optionText) {
  const opened = await cdp.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(root)})
    if (!r) return 'no-root'
    const sel = r.querySelectorAll('.el-select')[${selectIndex}]
    if (!sel) return 'no-select'
    const trigger = sel.querySelector('.el-select__wrapper') || sel.querySelector('.el-input__inner') || sel
    trigger.click()
    return 'ok'
  })()`)
  if (opened !== 'ok') return opened
  await sleep(700)
  const clicked = await cdp.eval(`(() => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')]
      .filter((el) => el.offsetParent !== null && el.textContent.trim() === ${JSON.stringify(optionText)})
    if (!items.length) return 'not-found'
    items[items.length - 1].click()
    return 'ok'
  })()`)
  await sleep(700)
  return clicked
}

/** 读表格某一行的某一格文本 */
async function tableCell(cdp, root, rowIndex, colIndex) {
  return cdp.eval(`(() => {
    const r = document.querySelector(${JSON.stringify(root)})
    if (!r) return ''
    const rows = r.querySelectorAll('.el-table__body-wrapper tbody tr')
    const tr = rows[${rowIndex}]
    if (!tr) return ''
    const td = tr.querySelectorAll('td')[${colIndex}]
    return td ? td.innerText.trim() : ''
  })()`)
}

async function main() {
  const profile = mkdtempSync(path.join(tmpdir(), 'mirage-edge-'))
  const edge = spawn(EDGE, [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${profile}`,
    'about:blank'
  ], { stdio: 'ignore' })

  let code = 0
  const ok = (name) => console.log('  PASS  ' + name)
  const bad = (name, extra) => { fail(name + (extra ? '  -> ' + extra : '')); code = 1 }

  try {
    await waitForCdp()
    // 新建标签页（新版 Chrome 的 /json/new 需要 PUT）
    const created = await fetch(`http://127.0.0.1:${PORT}/json/new?${encodeURIComponent(APP + '/#/tools')}`, { method: 'PUT' })
    const target = await created.json()
    const ws = new WebSocket(target.webSocketDebuggerUrl)
    await new Promise((res, rej) => {
      ws.addEventListener('open', res)
      ws.addEventListener('error', rej)
    })
    const cdp = new Cdp(ws)
    await cdp.send('Runtime.enable')
    await cdp.send('Log.enable')
    await cdp.send('Page.enable')

    // 先显式导航到应用页面：/json/new 建的标签页可能还停在 about:blank，
    // 那样相对 URL 的 fetch 会因为没有 base 而抛 "Failed to parse URL"
    await cdp.send('Page.navigate', { url: APP + '/#/tools' })
    await sleep(3000)
    const origin = await cdp.eval('location.origin')
    if (origin !== APP) {
      throw new Error('页面未就绪，origin=' + origin + '（期望 ' + APP + '）')
    }

    // 登录并写入 token
    const login = await cdp.eval(`(async () => {
      const r = await fetch(${JSON.stringify(APP + '/api/v1/auth/login')}, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'admin123' })
      })
      const b = await r.json()
      if (!b.data || !b.data.token) return 'no-token:' + JSON.stringify(b)
      localStorage.setItem('mirage_token', b.data.token)
      return 'ok'
    })()`)
    if (login !== 'ok') throw new Error('登录失败: ' + login)

    // 重新加载（token 是登录后才写进 localStorage 的，不 reload 的话路由守卫仍按未登录处理）
    await cdp.send('Page.reload', { ignoreCache: true })
    await sleep(4000)
    await cdp.eval(`(() => { location.hash = '#/tools'; return true })()`)
    await sleep(3000)

    const bodyText = await cdp.eval('document.body.innerText')
    const expectTabs = ['JSON', 'XML', 'SQL', '编码/转换', '测试助手', '文件/图片⇄Base64', 'SM3 摘要']
    for (const t of expectTabs) {
      if (bodyText.includes(t)) ok('标签可见: ' + t)
      else bad('标签缺失: ' + t)
    }

    // 切到「测试助手」，逐个点开子标签
    await openTab(cdp, '测试助手')
    await sleep(1500)

    const subTabs = ['MD5 摘要', '正则', 'JSONPath', 'Cron', '随机数据', '文本对比',
      'HMAC 签名', '字符集', 'CSV⇄JSON', 'URL', '字段风格']
    for (const t of subTabs) {
      const clicked = await openTab(cdp, t)
      await sleep(1000)
      if (clicked) ok('子标签可打开: ' + t)
      else bad('子标签未找到: ' + t)
    }

    // ---- MD5 摘要 ----
    await openTab(cdp, 'MD5 摘要')
    await sleep(1000)
    await setValueIn(cdp, ROOT.digest, '.dg-io', 'abc')
    await sleep(400)
    const clickedCalc = await clickIn(cdp, ROOT.digest, '计算摘要')
    await sleep(2500)
    const dgText = await textIn(cdp, ROOT.digest)
    // MD5("abc") 的已知向量
    if (dgText.includes('900150983cd24fb0d6963f7d28e17f72')) ok('MD5(abc) 结果正确（已知向量）')
    else bad('MD5 结果未出现', clickedCalc === 'ok' ? '按钮点了但没结果' : clickedCalc)

    // ---- 正则 ----
    await openTab(cdp, '正则')
    await sleep(800)
    await setValueIn(cdp, ROOT.regex, '.rx-pattern', '\\d{3}-\\d{4}')
    await setValueIn(cdp, ROOT.regex, '.rx-io', 'tel 138-1234 and 010-8888')
    await sleep(800)
    const rxText = await textIn(cdp, ROOT.regex)
    if (rxText.includes('2 处匹配') || rxText.includes('2 处')) ok('正则匹配到 2 处')
    else bad('正则匹配数不对', rxText.slice(0, 120))

    // ---- JSONPath ----
    await openTab(cdp, 'JSONPath')
    await sleep(800)
    await setValueIn(cdp, ROOT.jsonpath, '.jp-io', '{"code":"0000","data":{"items":[{"id":11},{"id":22}]}}')
    await sleep(400)
    await setValueIn(cdp, ROOT.jsonpath, '.jp-path', '$.data.items[*].id')
    await sleep(300)
    await clickIn(cdp, ROOT.jsonpath, '求值')
    await sleep(2000)
    const jpText = await textIn(cdp, ROOT.jsonpath)
    if (jpText.includes('命中') && jpText.includes('2 处匹配')) ok('JSONPath 求值命中 2 处')
    else bad('JSONPath 求值未命中', jpText.slice(0, 160))

    // ---- Cron ----
    await openTab(cdp, 'Cron')
    await sleep(800)
    await clickIn(cdp, ROOT.cron, '预览触发时间')
    await sleep(2000)
    const crText = await textIn(cdp, ROOT.cron)
    if (/\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(crText)) ok('Cron 预览出触发时间')
    else bad('Cron 预览没有时间', crText.slice(0, 120))

    // ---- 随机数据 ----
    await openTab(cdp, '随机数据')
    await sleep(800)
    await clickIn(cdp, ROOT.random, '生成')
    await sleep(1200)
    const rdText = await textIn(cdp, ROOT.random)
    if (/1[3-9]\d{9}/.test(rdText)) ok('随机数据生成出手机号')
    else bad('随机数据没有生成手机号', rdText.slice(0, 120))

    // ---- 随机数据：身份证的地区 / 年龄段 / 性别 ----
    const typeSel = await selectOption(cdp, ROOT.random, 0, 'idcard')
    await sleep(600)
    if (typeSel !== 'ok') bad('切换类型到 idcard 失败', typeSel)
    const provSel = await selectOption(cdp, ROOT.random, 1, '浙江省')
    await sleep(400)
    const citySel = await selectOption(cdp, ROOT.random, 2, '杭州市')
    await sleep(400)
    await setValueIn(cdp, ROOT.random, '.rd-age input', '22', 0)
    await setValueIn(cdp, ROOT.random, '.rd-age input', '24', 1)
    await sleep(400)
    await clickRadioIn(cdp, ROOT.random, '女')
    await sleep(400)
    await clickIn(cdp, ROOT.random, '生成')
    await sleep(1200)
    const idVal = await tableCell(cdp, ROOT.random, 0, 1)
    const idDetail = await tableCell(cdp, ROOT.random, 0, 2)
    const idOk = /^\d{17}[\dX]$/.test(idVal) && idVal.slice(0, 4) === '3301'
    const detailOk = idDetail.includes('杭州市') && idDetail.includes('女') && /2[2-4]岁/.test(idDetail)
    if (idOk) ok('身份证地区码 = 3301（浙江杭州）: ' + idVal)
    else bad('身份证地区码不对', idVal + ' / ' + provSel + ' ' + citySel)
    if (detailOk) ok('身份证解析：年龄在 22~24 且性别为女')
    else bad('身份证解析不对', idDetail)

    // ---- 文本对比 ----
    await openTab(cdp, '文本对比')
    await sleep(800)
    await setValueIn(cdp, ROOT.diff, '.df-io', 'a\nb\nc', 0)
    await setValueIn(cdp, ROOT.diff, '.df-io', 'a\nX\nc', 1)
    await sleep(300)
    await clickIn(cdp, ROOT.diff, '对比')
    await sleep(1200)
    const dfText = await textIn(cdp, ROOT.diff)
    if (dfText.includes('新增 1') && dfText.includes('删除 1')) ok('文本对比统计正确（新增 1 / 删除 1）')
    else bad('文本对比统计不对', dfText.slice(0, 160))

    // ---- HMAC 签名 ----
    await openTab(cdp, 'HMAC 签名')
    await sleep(800)
    await clickIn(cdp, ROOT.hmac, '示例')
    await sleep(500)
    const hmacClicked = await clickIn(cdp, ROOT.hmac, '计算签名')
    await sleep(2000)
    const hmText = await textIn(cdp, ROOT.hmac)
    // 与 node:crypto 算出的期望值比对（示例里的密钥/内容是固定的）
    const expectHmac = crypto.createHmac('sha256', 'test-secret')
      .update('appId=1001&nonce=abc123&timestamp=1753905600', 'utf8').digest('hex')
    if (hmText.includes(expectHmac)) ok('HMAC-SHA256 与 node:crypto 一致')
    else bad('HMAC 结果不对', hmacClicked + ' expect=' + expectHmac)

    // ---- 字符集 ----
    await openTab(cdp, '字符集')
    await sleep(800)
    await clickIn(cdp, ROOT.charset, '示例')   // 示例 = 文本→字节 / GBK
    await sleep(400)
    await setValueIn(cdp, ROOT.charset, '.cs-io', '中文')  // 「中文」的 GBK 字节 = d6d0cec4
    await sleep(400)
    await clickIn(cdp, ROOT.charset, '转换')
    await sleep(2000)
    const csText = await textIn(cdp, ROOT.charset)
    if (csText.toLowerCase().includes('d6d0cec4')) ok('中文的 GBK 字节正确（d6d0cec4）')
    else bad('字符集转换结果不对', csText.slice(0, 160))

    // ---- CSV ⇄ JSON ----
    await openTab(cdp, 'CSV⇄JSON')
    await sleep(800)
    await clickIn(cdp, ROOT.csv, '示例')
    await sleep(400)
    await clickIn(cdp, ROOT.csv, '转换')
    await sleep(1200)
    const csvOut = await getValueIn(cdp, ROOT.csv, '.cv-out')
    if (csvOut.includes('"蜃楼"') && csvOut.includes('含,逗号')) ok('CSV → JSON 解析正确（含引号内逗号）')
    else bad('CSV → JSON 结果不对', csvOut.slice(0, 200))

    // ---- URL ----
    await openTab(cdp, 'URL')
    await sleep(800)
    await clickIn(cdp, ROOT.url, '示例')
    await sleep(1200)
    const ulText = await textIn(cdp, ROOT.url)
    if (ulText.includes('api.example.com') && ulText.includes('蜃楼')) ok('URL 解析正确（主机 + 解码后的参数值）')
    else bad('URL 解析结果不对', ulText.slice(0, 200))

    // ---- 字段风格 ----
    await openTab(cdp, '字段风格')
    await sleep(800)
    await clickIn(cdp, ROOT.case, '示例')
    await sleep(400)
    await clickIn(cdp, ROOT.case, '转换')
    await sleep(1200)
    const ncOut = await getValueIn(cdp, ROOT.case, '.nc-out')
    if (ncOut.includes('"userName"') && ncOut.includes('"itemId"')) ok('字段风格转换正确（snake → camel）')
    else bad('字段风格转换结果不对', ncOut.slice(0, 200))

    // 收集控制台错误
    const errs = cdp.events.filter((e) => {
      if (e.method === 'Runtime.exceptionThrown') return true
      if (e.method === 'Runtime.consoleAPICalled' && e.params.type === 'error') return true
      if (e.method === 'Log.entryAdded' && e.params.entry.level === 'error') return true
      return false
    })
    const texts = errs.map((e) => {
      if (e.method === 'Runtime.exceptionThrown') return e.params.exceptionDetails?.exception?.description || 'exception'
      if (e.method === 'Runtime.consoleAPICalled') return (e.params.args || []).map((a) => a.value || a.description).join(' ')
      return e.params.entry.text
    })
    const real = texts.filter((t) => t && !/favicon|404 \(Not Found\)/i.test(t))
    if (real.length) {
      console.log('  控制台错误:')
      real.slice(0, 10).forEach((t) => console.log('    - ' + t))
      fail('页面存在控制台错误 ' + real.length + ' 条')
      code = 1
    } else {
      ok('无控制台错误')
    }

    ws.close()
  } catch (e) {
    fail('执行异常: ' + e.message)
    code = 1
  } finally {
    edge.kill()
    try { rmSync(profile, { recursive: true, force: true }) } catch (e) { /* ignore */ }
  }
  console.log(code === 0 ? 'UI 校验通过' : 'UI 校验失败')
  process.exit(code)
}

main()
