#!/usr/bin/env node
/**
 * 验证「测试用例分组折叠展示」与「请求参数快捷引用」。
 *
 *   node .tools/verify-testcase-group.js
 *
 * 流程：无头 Edge(CDP) → 登录 → 造数据（分组用的用例 + 一个项目变量）
 *       → 打开 #/testcases → 断言分组/折叠/整组勾选
 *       → 打开新建用例弹窗 → 断言参数行「引用」菜单能插入 ${var.x} / ${...} / 数据行键。
 */
import { spawn } from 'child_process'
import { mkdtempSync, rmSync } from 'fs'
import { tmpdir } from 'os'
import path from 'path'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const APP = process.env.APP_URL || 'http://localhost:9080'
const PORT = 9400 + Math.floor(Math.random() * 400)

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function fail(msg) {
  console.error('FAIL: ' + msg)
  process.exitCode = 1
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

/** 取当前可见的弹窗根节点 */
const DLG = `(() => {
  const ds = [...document.querySelectorAll('.el-dialog')].filter(d => d.getBoundingClientRect().height > 0)
  return ds.length ? ds[ds.length - 1] : null
})()`

/** 在可见弹窗内按选择器取值（index 定位第几个） */
async function dlgValue(cdp, selector, index = 0) {
  return cdp.eval(`(() => {
    const d = ${DLG}
    if (!d) return ''
    const els = d.querySelectorAll(${JSON.stringify(selector)})
    const el = els[${index}]
    if (!el) return ''
    return el.value === undefined ? el.textContent : el.value
  })()`)
}

/** 给可见弹窗内的输入框/文本域赋值并触发 input */
async function dlgSet(cdp, selector, value, index = 0) {
  return cdp.eval(`(() => {
    const d = ${DLG}
    if (!d) return 'no-dialog'
    const roots = d.querySelectorAll(${JSON.stringify(selector)})
    const root = roots[${index}]
    if (!root) return 'not-found:' + ${JSON.stringify(selector)}
    let el = root
    if (root.tagName !== 'INPUT' && root.tagName !== 'TEXTAREA') {
      el = root.querySelector('input, textarea')
      if (!el) return 'no-input'
    }
    const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, ${JSON.stringify(value)})
    el.dispatchEvent(new Event('input', { bubbles: true }))
    return 'ok'
  })()`)
}

/** 在可见弹窗内按文本点按钮 */
async function dlgClick(cdp, label) {
  return cdp.eval(`(() => {
    const d = ${DLG}
    if (!d) return 'no-dialog'
    const btn = [...d.querySelectorAll('button')].find(b => b.textContent.trim() === ${JSON.stringify(label)})
    if (btn) { btn.click(); return 'ok' }
    return 'not-found'
  })()`)
}

/** 表格行统计：总数 / 分组标题行数 / 首行文本 */
async function tableInfo(cdp) {
  return cdp.eval(`(() => {
    const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
    const groups = rows.filter(r => r.classList.contains('group-row'))
    const counts = groups.map(r => { const t = r.querySelector('.el-tag'); return t ? t.innerText : '' })
    const sum = counts.reduce((a, s) => a + (parseInt((s || '').replace(/[^0-9]/g, ''), 10) || 0), 0)
    return {
      total: rows.length,
      groups: groups.length,
      groupNames: groups.map(r => (r.querySelector('.grp-name') || {}).innerText || ''),
      groupCounts: counts,
      sum,
      firstCol: rows.length ? (rows[0].querySelectorAll('td')[1] || {}).innerText || '' : ''
    }
  })()`)
}

/** 切下拉框：按根选择器定位 .el-select，点开再点可见选项 */
async function pickSelect(cdp, rootSel, optionText, index = 0) {
  const opened = await cdp.eval(`(() => {
    const sels = document.querySelectorAll(${JSON.stringify(rootSel)})
    const sel = sels[${index}]
    if (!sel) return 'no-select'
    const trigger = sel.querySelector('.el-select__wrapper') || sel.querySelector('.el-input__inner') || sel
    trigger.click()
    return 'ok'
  })()`)
  if (opened !== 'ok') return opened
  await sleep(700)
  const clicked = await cdp.eval(`(() => {
    const items = [...document.querySelectorAll('.el-select-dropdown__item')]
      .filter(el => el.offsetParent !== null && el.textContent.trim() === ${JSON.stringify(optionText)})
    if (!items.length) return 'not-found'
    items[items.length - 1].click()
    return 'ok'
  })()`)
  await sleep(700)
  return clicked
}

/** 点第 n 个「引用」按钮（弹窗内），返回是否点到 */
async function openRef(cdp, n = 0) {
  return cdp.eval(`(() => {
    const d = ${DLG}
    if (!d) return 'no-dialog'
    const btns = [...d.querySelectorAll('.prm-trigger')]
    const b = btns[${n}]
    if (!b) return 'not-found'
    b.click()
    return 'ok'
  })()`)
}

/** 在当前可见的引用菜单里点某个条目（按文本包含匹配） */
async function clickRefItem(cdp, text) {
  await sleep(500)
  return cdp.eval(`(() => {
    const pops = [...document.querySelectorAll('.prm-popper')].filter(p => p.getBoundingClientRect().height > 0)
    if (!pops.length) return 'no-popover'
    const pop = pops[pops.length - 1]
    const item = [...pop.querySelectorAll('.prm-item')].find(i => i.innerText.includes(${JSON.stringify(text)}))
    if (!item) return 'not-found'
    item.click()
    return 'ok'
  })()`)
}

/** 在当前可见的引用菜单里切标签页 */
async function refTab(cdp, label) {
  return cdp.eval(`(() => {
    const pops = [...document.querySelectorAll('.prm-popper')].filter(p => p.getBoundingClientRect().height > 0)
    if (!pops.length) return 'no-popover'
    const pop = pops[pops.length - 1]
    const t = [...pop.querySelectorAll('.el-tabs__item')].find(e => e.textContent.trim().startsWith(${JSON.stringify(label)}))
    if (!t) return 'not-found'
    t.click()
    return 'ok'
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
    const created = await fetch(`http://127.0.0.1:${PORT}/json/new?${encodeURIComponent(APP + '/#/testcases')}`, { method: 'PUT' })
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

    await cdp.send('Page.navigate', { url: APP + '/#/testcases' })
    await sleep(3000)
    const origin = await cdp.eval('location.origin')
    if (origin !== APP) throw new Error('页面未就绪，origin=' + origin)

    // 登录 + 选项目 + 造数据
    const setup = await cdp.eval(`(async () => {
      const r = await fetch(${JSON.stringify(APP + '/api/v1/auth/login')}, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'admin123' })
      })
      const b = await r.json()
      if (!b.data || !b.data.token) return 'no-token'
      const token = b.data.token
      localStorage.setItem('mirage_token', token)
      const H = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }
      const pr = await fetch(${JSON.stringify(APP + '/api/v1/projects')}, { headers: H })
      const pb = await pr.json()
      const projects = pb.data || []
      if (!projects.length) return 'no-project'
      const p = projects[0]
      localStorage.setItem('mirage_pid', String(p.id))
      localStorage.setItem('mirage_pcode', p.code)
      localStorage.setItem('mirage_pname', p.name || '')

      // 清掉上一轮遗留的验证数据，保证每次跑的用例集合一致
      const lr0 = await fetch(${JSON.stringify(APP + '/api/v1/projects/')} + p.id + '/testcases', { headers: H })
      const lb0 = await lr0.json()
      for (const c of (lb0.data || [])) {
        if (c.name && c.name.startsWith('grp-')) {
          await fetch(${JSON.stringify(APP + '/api/v1/testcases/')} + c.id, { method: 'DELETE', headers: H })
        }
      }
      const vr0 = await fetch(${JSON.stringify(APP + '/api/v1/projects/')} + p.id + '/test-variables', { headers: H })
      const vb0 = await vr0.json()
      for (const v of (vb0.data || [])) {
        if (v.name && v.name.startsWith('vt_')) {
          await fetch(${JSON.stringify(APP + '/api/v1/test-variables/')} + v.id, { method: 'DELETE', headers: H })
        }
      }

      // 项目变量（供「引用」菜单插入）
      const vname = 'vt_' + Math.random().toString(36).slice(2, 8)
      const vr = await fetch(${JSON.stringify(APP + '/api/v1/projects/')} + p.id + '/test-variables', {
        method: 'POST', headers: H, body: JSON.stringify({ name: vname, varValue: 'abc123', remark: '验证用' })
      })
      const vb = await vr.json()
      if (vb.code !== 0) return 'no-var:' + JSON.stringify(vb)

      // 分组用的用例
      const mk = async (obj) => {
        const res = await fetch(${JSON.stringify(APP + '/api/v1/projects/')} + p.id + '/testcases', {
          method: 'POST', headers: H, body: JSON.stringify(obj)
        })
        const jb = await res.json()
        return jb.code === 0 ? 'ok' : 'fail:' + JSON.stringify(jb)
      }
      const mkCase = (name, tags, extra) => mk(Object.assign({
        name: name, protocol: 'HTTP', method: 'GET', url: 'http://example.com/' + name,
        headers: '[]', query: '[]', assertions: '[]', bodyType: 'none', body: '', mode: 'proxy', status: 1
      }, { tags: JSON.stringify(tags) }, extra || {}))

      const res = []
      res.push(await mkCase('grp-smoke-1', ['smoke']))
      res.push(await mkCase('grp-smoke-2', ['smoke']))
      res.push(await mkCase('grp-slow-1', ['slow']))
      res.push(await mkCase('grp-none-1', []))
      res.push(await mkCase('grp-tcp-1', [], {
        protocol: 'TCP', method: '', url: 'localhost:9001', bodyType: 'raw',
        body: '{"transCode":"0200"}',
        tcpConfig: '{"frameConfig":{"type":"length_field","lenBytes":4},"messageFormat":"json"}'
      }))
      if (res.some(x => x !== 'ok')) return 'mk-fail:' + res.join('|')
      return 'ok:' + p.id + ':' + vname
    })()`)
    if (!String(setup).startsWith('ok')) throw new Error('准备数据失败: ' + setup)
    const vname = String(setup).split(':')[2]
    ok('准备数据完成（项目变量 ' + vname + ' + 5 条用例）')

    // 重新加载，进入测试用例页
    await cdp.send('Page.reload', { ignoreCache: true })
    await sleep(4000)
    await cdp.eval(`(() => { location.hash = '#/testcases'; return true })()`)
    await sleep(2500)

    // 等待表格出现
    let rows = 0
    for (let i = 0; i < 20; i++) {
      rows = await cdp.eval(`document.querySelectorAll('.el-table__body-wrapper tbody tr').length`)
      if (rows > 0) break
      await sleep(500)
    }
    if (!rows) throw new Error('用例列表没有渲染')
    ok('用例列表已渲染（' + rows + ' 行）')

    // ---- 按标签分组 ----
    let sel = await pickSelect(cdp, '.grp-select', '按标签分组')
    if (sel !== 'ok') bad('选择「按标签分组」失败', sel)
    await sleep(800)
    let info = await tableInfo(cdp)
    const expectGroups = ['smoke', 'slow', '未分组']
    const namesOk = expectGroups.every((n) => info.groupNames.includes(n))
    if (info.groups === 3 && namesOk) ok('按标签分组：3 个分组（' + info.groupNames.join(' / ') + '）')
    else bad('按标签分组的分组不对', JSON.stringify(info))
    if (info.total === info.groups + info.sum) ok('分组标题行 + ' + info.sum + ' 条用例都在表里（' + info.total + ' 行）')
    else bad('分组后总行数不对', JSON.stringify(info))

    // ---- 折叠 / 展开某个分组 ----
    const beforeCollapse = info.total
    // 首个分组里的用例数（从标题行的「N 条」标签读）
    const firstGroupCount = parseInt((info.groupCounts[0] || '').replace(/[^\d]/g, ''), 10) || 0
    await cdp.eval(`(() => {
      const g = document.querySelector('.el-table__row.group-row .grp-caret')
      if (g) g.click()
      return !!g
    })()`)
    await sleep(700)
    info = await tableInfo(cdp)
    if (info.total === beforeCollapse - firstGroupCount) {
      ok('折叠首个分组后隐藏 ' + firstGroupCount + ' 条用例（' + info.total + ' 行）')
    } else bad('折叠分组后行数不对', JSON.stringify(info) + ' 期望 ' + (beforeCollapse - firstGroupCount))

    await cdp.eval(`(() => {
      const g = document.querySelector('.el-table__row.group-row .grp-caret')
      if (g) g.click()
      return !!g
    })()`)
    await sleep(700)
    info = await tableInfo(cdp)
    if (info.total === beforeCollapse) ok('再次点击可展开分组')
    else bad('展开分组后行数不对', JSON.stringify(info))

    // ---- 折叠全部 / 展开全部 ----
    await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.textContent.trim() === '折叠全部')
      if (b) b.click()
      return !!b
    })()`)
    await sleep(700)
    info = await tableInfo(cdp)
    if (info.total === info.groups && info.groups === 3) ok('折叠全部：只剩 3 个分组标题行')
    else bad('折叠全部不对', JSON.stringify(info))

    await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.textContent.trim() === '展开全部')
      if (b) b.click()
      return !!b
    })()`)
    await sleep(700)
    info = await tableInfo(cdp)
    if (info.total === info.groups + info.sum) ok('展开全部：分组 + 用例全部回来')
    else bad('展开全部不对', JSON.stringify(info))

    // ---- 分组整组勾选 ----
    await cdp.eval(`(() => {
      const g = document.querySelector('.el-table__row.group-row .grp-head .el-checkbox')
      if (g) g.click()
      return !!g
    })()`)
    await sleep(700)
    const batchLabel = await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('批量运行'))
      return b ? b.textContent.trim() : ''
    })()`)
    if (/（\d+）/.test(batchLabel)) {
      const n = parseInt(batchLabel.match(/（(\d+)）/)[1], 10)
      if (n === firstGroupCount) ok('分组标题行的「全选」可整组勾选：' + batchLabel)
      else bad('整组勾选数量不对', batchLabel + ' 期望 ' + firstGroupCount)
    } else bad('整组勾选无效', batchLabel)

    // ---- 按协议分组 ----
    sel = await pickSelect(cdp, '.grp-select', '按协议分组')
    await sleep(800)
    info = await tableInfo(cdp)
    if (info.groups === 2 && info.groupNames.includes('TCP 用例') && info.groupNames.includes('HTTP 用例')) {
      ok('按协议分组：HTTP / TCP 两组')
    } else bad('按协议分组不对', JSON.stringify(info))

    // 恢复不分组
    await pickSelect(cdp, '.grp-select', '不分组')
    await sleep(600)

    // ---- 参数快捷引用 ----
    await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.textContent.includes('新建用例'))
      if (b) b.click()
      return !!b
    })()`)
    await sleep(1500)

    // URL 输入框 + 一个查询参数行
    await dlgSet(cdp, 'input[placeholder="http://host:port/path"]', 'http://example.com/api')
    await sleep(300)
    // 「加参数」在查询参数区（第 2 个同名按钮：请求头一个、查询一个）
    const addParam = await cdp.eval(`(() => {
      const d = ${DLG}
      if (!d) return 'no-dialog'
      const bs = [...d.querySelectorAll('button')].filter(b => b.textContent.trim() === '加参数')
      if (!bs.length) return 'not-found'
      bs[bs.length - 1].click()
      return 'ok'
    })()`)
    if (addParam !== 'ok') bad('加查询参数失败', addParam)
    await sleep(600)

    // 引用菜单按钮：URL 一个 + 查询参数行一个
    const refCount = await cdp.eval(`(() => {
      const d = ${DLG}
      if (!d) return -1
      return d.querySelectorAll('.prm-trigger').length
    })()`)
    if (refCount >= 2) ok('参数行上有「引用」按钮（' + refCount + ' 个）')
    else bad('没有渲染「引用」按钮', String(refCount))

    // 先在查询参数值里输入 abc，再点该行的「引用」
    const qValIdx = await cdp.eval(`(() => {
      const d = ${DLG}
      if (!d) return -1
      const ins = [...d.querySelectorAll('input')]
      // 查询参数的值框：占位符为「参数值」
      const i = ins.findIndex(x => x.placeholder === '参数值')
      return i
    })()`)
    if (qValIdx < 0) bad('找不到查询参数值输入框')
    await dlgSet(cdp, 'input[placeholder="参数值"]', 'abc')
    await sleep(400)

    // 第 2 个「引用」按钮 = 查询参数行
    await openRef(cdp, 1)
    await sleep(600)
    let pick = await clickRefItem(cdp, vname)
    if (pick !== 'ok') bad('引用菜单里没找到项目变量', pick + ' ' + vname)
    await sleep(600)
    let qVal = await dlgValue(cdp, 'input[placeholder="参数值"]')
    if (qVal === 'abc${var.' + vname + '}') ok('插入项目变量到光标处：' + qVal)
    else bad('插入变量结果不对', qVal)

    // URL 字段也能插入（第 1 个「引用」按钮）
    await dlgSet(cdp, 'input[placeholder="http://host:port/path"]', 'http://example.com/')
    await sleep(300)
    await openRef(cdp, 0)
    await sleep(600)
    pick = await clickRefItem(cdp, vname)
    if (pick !== 'ok') bad('URL 的引用菜单没找到项目变量', pick)
    await sleep(600)
    const urlVal = await dlgValue(cdp, 'input[placeholder="http://host:port/path"]')
    if (urlVal === 'http://example.com/${var.' + vname + '}') ok('URL 也能插入引用：' + urlVal)
    else bad('URL 插入引用结果不对', urlVal)

    // 再插入一个函数（函数页）
    await openRef(cdp, 1)
    await sleep(500)
    const tabRes = await refTab(cdp, '函数')
    if (tabRes !== 'ok') bad('切到函数页失败', tabRes)
    await sleep(500)
    pick = await clickRefItem(cdp, 'uuid')
    if (pick !== 'ok') bad('引用菜单里没找到 uuid 函数', pick)
    await sleep(600)
    qVal = await dlgValue(cdp, 'input[placeholder="参数值"]')
    if (qVal.includes('${uuid')) ok('插入函数片段：' + qVal)
    else bad('插入函数结果不对', qVal)

    // 数据行键
    await dlgSet(cdp, 'textarea[placeholder*="uid"]', '[{"uid":10086},{"uid":10087}]')
    await sleep(600)
    await openRef(cdp, 1)
    await sleep(500)
    const tabRes2 = await refTab(cdp, '数据行')
    if (tabRes2 !== 'ok') bad('切到数据行页失败', tabRes2)
    await sleep(500)
    pick = await clickRefItem(cdp, 'uid')
    if (pick !== 'ok') bad('引用菜单里没找到数据行键 uid', pick)
    await sleep(600)
    qVal = await dlgValue(cdp, 'input[placeholder="参数值"]')
    if (qVal.includes('${var.uid}')) ok('插入数据行键：' + qVal)
    else bad('插入数据行键结果不对', qVal)

    // 控制台错误
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
  console.log(code === 0 ? '分组/引用校验通过' : '分组/引用校验失败')
  process.exit(code)
}

main()
