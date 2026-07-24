<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>测试用例 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <div>
            <el-button @click="openVariables">变量/常量</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建用例</el-button>
          </div>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column label="请求" show-overflow-tooltip>
          <template #default="{ row }"><el-tag size="small" :type="mTag(row.method)">{{ row.method }}</el-tag> <span class="mono">{{ row.url }}</span></template>
        </el-table-column>
        <el-table-column label="模式" width="90">
          <template #default="{ row }">{{ row.mode === 'direct' ? '浏览器直发' : '后端转发' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button size="small" type="success" link @click="onRun(row)">运行</el-button>
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="info" link @click="openHistory(row)">历史</el-button>
            <el-button size="small" type="danger" link @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑/新建 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑用例' : '新建用例'" width="900px" top="3vh">
      <el-form :model="form" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="10"><el-form-item label="名称"><el-input v-model="form.name" placeholder="如：查询用户" /></el-form-item></el-col>
          <el-col :span="14">
            <el-form-item label="模式" label-width="56px">
              <el-radio-group v-model="form.mode">
                <el-radio label="proxy">后端转发（推荐，任意目标）</el-radio>
                <el-radio label="direct">浏览器直发（受 CORS）</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">请求</el-divider>
        <div class="hint">
          URL/请求头/查询/Body 支持 <span class="mono">${var.变量名}</span> 与函数（如 <span class="mono">${uuid()}</span>、<span class="mono">${int(1,100)}</span>，函数仅「后端转发」模式求值）。
          点变量名复制引用：
          <el-tag v-for="v in variables" :key="v.id" size="small" style="margin:0 4px;cursor:pointer" @click="copyVar(v)">{{ v.name }}</el-tag>
          <span v-if="!variables.length" class="muted">（先到「变量/常量」添加）</span>
        </div>
        <el-row :gutter="8" style="margin-bottom:6px">
          <el-col :span="4">
            <el-select v-model="form.method">
              <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH','HEAD','OPTIONS']" :key="m" :label="m" :value="m" />
            </el-select>
          </el-col>
          <el-col :span="20"><el-input v-model="form.url" placeholder="http://host:port/path" /></el-col>
        </el-row>
        <div class="kv-title">请求头</div>
        <div v-for="(h, i) in form.headers" :key="'h'+i" class="kv-row">
          <el-input v-model="h.k" placeholder="Header 名" style="width:38%" />
          <el-input v-model="h.v" placeholder="Header 值" style="width:52%" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="form.headers.splice(i,1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="form.headers.push({ k: '', v: '' })">加请求头</el-button>

        <div class="kv-title" style="margin-top:10px">查询参数（Query）</div>
        <div v-for="(q, i) in form.query" :key="'q'+i" class="kv-row">
          <el-input v-model="q.k" placeholder="参数名" style="width:38%" />
          <el-input v-model="q.v" placeholder="参数值" style="width:52%" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="form.query.splice(i,1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="form.query.push({ k: '', v: '' })">加参数</el-button>

        <div class="kv-title" style="margin-top:10px">
          请求体
          <el-select v-model="form.bodyType" size="small" style="width:110px; margin-left:8px">
            <el-option label="无 none" value="none" /><el-option label="JSON" value="json" />
            <el-option label="表单 form" value="form" /><el-option label="原始 raw" value="raw" />
          </el-select>
        </div>
        <el-row :gutter="8" v-if="form.bodyType !== 'none'">
          <el-col :span="17">
            <textarea ref="bodyArea" v-model="form.body" class="body-area" rows="4" spellcheck="false" placeholder='{"key":"${var.x}"}'></textarea>
          </el-col>
          <el-col :span="7">
            <div class="fn-panel"><div class="fn-title">函数市场 · 插入光标处</div><FunctionMarketSidebar :project-id="proj.id" @insert="insertBodyFn" /></div>
          </el-col>
        </el-row>

        <el-divider content-position="left">curl 导入</el-divider>
        <el-input v-model="curlText" type="textarea" :rows="2" placeholder="粘贴 curl 命令，点解析自动填充上方请求" />
        <el-button size="small" type="primary" style="margin-top:6px" @click="onImportCurl">解析 curl</el-button>

        <el-divider content-position="left">断言</el-divider>
        <div v-for="(a, i) in form.assertions" :key="'a'+i" class="kv-row">
          <el-select v-model="a.type" style="width:130px">
            <el-option label="状态码 status" value="status" />
            <el-option label="响应体包含" value="bodyContains" />
            <el-option label="响应头 header" value="header" />
            <el-option label="JSONPath" value="jsonPath" />
          </el-select>
          <el-input v-if="a.type === 'header' || a.type === 'jsonPath'" v-model="a.target" :placeholder="a.type === 'jsonPath' ? '$.data.id' : 'Header-Name'" style="width:22%" />
          <el-select v-else disabled style="width:22%" />
          <el-select v-if="a.type === 'header' || a.type === 'jsonPath'" v-model="a.op" style="width:90px">
            <el-option label="等于 eq" value="eq" /><el-option label="包含 contains" value="contains" /><el-option label="存在 exists" value="exists" v-if="a.type === 'jsonPath'" />
          </el-select>
          <el-input v-if="a.op !== 'exists'" v-model="a.expected" placeholder="期望值" style="width:30%" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="form.assertions.splice(i,1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="form.assertions.push({ type: 'status', target: '', op: 'eq', expected: '200' })">加断言</el-button>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="success" @click="onSend" :loading="sending">发送</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 运行结果 -->
    <el-dialog v-model="resultVisible" title="运行结果" width="820px" top="3vh">
      <div v-if="result">
        <div style="margin-bottom:8px">
          <el-tag :type="result.passed ? 'success' : 'danger'">{{ result.passed ? '✓ 通过' : '✗ 失败' }}</el-tag>
          <el-tag v-if="result.httpStatus != null" :type="sTag(result.httpStatus)" style="margin-left:6px">HTTP {{ result.httpStatus }}</el-tag>
          <span v-if="result.costMs != null" class="muted">耗时 {{ result.costMs }}ms</span>
          <el-tag v-if="result.mode" size="small" style="margin-left:6px">{{ result.mode === 'direct' ? '浏览器直发' : '后端转发' }}</el-tag>
        </div>
        <el-alert v-if="result.error" type="error" :closable="false" :title="result.error" style="margin-bottom:8px" />
        <div v-if="result.assertions && result.assertions.length" class="kv-title">断言</div>
        <el-table v-if="result.assertions && result.assertions.length" :data="result.assertions" size="small" border style="margin-bottom:8px">
          <el-table-column label="结果" width="64"><template #default="{ row }">{{ row.passed ? '✓' : '✗' }}</template></el-table-column>
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="target" label="目标" width="150" show-overflow-tooltip />
          <el-table-column label="期望 / 实际"><template #default="{ row }"><span class="muted">期望:</span> {{ row.expected }} <span class="muted">| 实际:</span> {{ row.actual }}</template></el-table-column>
        </el-table>
        <div v-if="respHeaders.length" class="kv-title">响应头</div>
        <pre v-if="respHeaders.length" class="resp">{{ respHeaders.map(h => h.k + ': ' + h.v).join('\n') }}</pre>
        <div v-if="result.body != null" class="kv-title">响应体</div>
        <pre v-if="result.body != null" class="resp">{{ result.body }}</pre>
      </div>
    </el-dialog>

    <!-- 运行历史 -->
    <el-dialog v-model="historyVisible" title="运行历史" width="780px">
      <el-table :data="history" size="small" border>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="结果" width="70"><template #default="{ row }">{{ row.passed === 1 ? '✓' : '✗' }}</template></el-table-column>
        <el-table-column prop="mode" label="模式" width="90" />
        <el-table-column prop="httpStatus" label="状态" width="70" />
        <el-table-column prop="costMs" label="耗时" width="80"><template #default="{ row }">{{ row.costMs }}ms</template></el-table-column>
        <el-table-column prop="error" label="错误" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- 变量 / 常量 管理 -->
    <el-dialog v-model="varVisible" title="变量 / 常量（项目级）" width="660px">
      <div style="margin-bottom:8px">
        <span class="muted">在用例里用 ${var.name} 引用；值可为常量或含 ${...} 函数（运行时后端转发模式求值）</span>
        <el-button size="small" type="primary" :icon="Plus" style="float:right" @click="openVarCreate">新增</el-button>
      </div>
      <el-table :data="variables" size="small" border>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="varValue" label="值" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openVarEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onVarRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="varFormVisible" :title="varForm.id ? '编辑变量' : '新增变量'" width="460px" append-to-body>
        <el-form :model="varForm" label-width="64px">
          <el-form-item label="名称"><el-input v-model="varForm.name" :disabled="!!varForm.id" placeholder="如 host / token" /></el-form-item>
          <el-form-item label="值"><el-input v-model="varForm.varValue" placeholder="常量 或 含 ${...} 函数" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="varForm.remark" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="varFormVisible = false">取消</el-button>
          <el-button type="primary" @click="onVarSave">保存</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import { parseCurl } from '../utils/curl'
import { copyText } from '../utils/clipboard'
import FunctionMarketSidebar from '../components/FunctionMarketSidebar.vue'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)

const formVisible = ref(false)
const sending = ref(false)
const curlText = ref('')
const form = reactive({ id: null, name: '', method: 'GET', url: '', headers: [], query: [], bodyType: 'none', body: '', assertions: [], mode: 'proxy', status: 1, remark: '' })

const resultVisible = ref(false)
const result = ref(null)
const respHeaders = computed(() => {
  if (!result.value || !result.value.headers) return []
  return Object.entries(result.value.headers).map(([k, v]) => ({ k, v }))
})

const historyVisible = ref(false)
const history = ref([])

// 变量/常量
const variables = ref([])
const varVisible = ref(false)
const varFormVisible = ref(false)
const varForm = reactive({ id: null, name: '', varValue: '', remark: '' })
const bodyArea = ref(null)

const METHODS_TAG = { GET: 'success', POST: 'warning', PUT: 'primary', DELETE: 'danger' }
function mTag(m) { return METHODS_TAG[m] || 'info' }
function sTag(s) { return s >= 200 && s < 300 ? 'success' : s >= 400 ? 'danger' : 'info' }

async function load() {
  if (!proj.id) return
  loading.value = true
  try { const res = await api.testCases.list(proj.id); list.value = res.data || [] } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', method: 'GET', url: '', headers: [], query: [], bodyType: 'none', body: '', assertions: [], mode: 'proxy', status: 1, remark: '' })
  curlText.value = ''
  formVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name; form.method = row.method || 'GET'; form.url = row.url || ''
  form.bodyType = row.bodyType || 'none'; form.body = row.body || ''
  form.mode = row.mode || 'proxy'; form.status = row.status == null ? 1 : row.status; form.remark = row.remark || ''
  form.headers = parseArr(row.headers); form.query = parseArr(row.query); form.assertions = parseArr(row.assertions)
  curlText.value = ''
  formVisible.value = true
}

function parseArr(s) { try { return JSON.parse(s || '[]') } catch (e) { return [] } }

function buildEntity() {
  return {
    id: form.id, name: form.name, method: form.method, url: form.url,
    bodyType: form.bodyType, body: form.body, mode: form.mode, status: form.status, remark: form.remark,
    headers: JSON.stringify(form.headers.filter((h) => h.k)),
    query: JSON.stringify(form.query.filter((q) => q.k)),
    assertions: JSON.stringify(form.assertions)
  }
}

async function onSave() {
  const e = buildEntity()
  if (form.id) { await api.testCases.update(form.id, e) } else { const r = await api.testCases.create(proj.id, e); form.id = r.data.id }
  ElMessage.success('已保存'); formVisible.value = false; load()
}

async function saveSilently() {
  const e = buildEntity()
  if (form.id) { await api.testCases.update(form.id, e) } else { const r = await api.testCases.create(proj.id, e); form.id = r.data.id }
}

function onImportCurl() {
  if (!curlText.value.trim()) { ElMessage.warning('请粘贴 curl 命令'); return }
  try {
    const c = parseCurl(curlText.value)
    form.method = c.method; form.url = c.url; form.headers = c.headers; form.body = c.body; form.bodyType = c.bodyType
    ElMessage.success('已解析填充')
  } catch (e) { ElMessage.error('解析失败：' + e.message) }
}

function buildUrl(f) {
  let u = f.url || ''
  const qs = (f.query || []).filter((q) => q.k)
  if (!qs.length) return u
  const sep = u.includes('?') ? '&' : '?'
  return u + sep + qs.map((q) => encodeURIComponent(q.k) + '=' + encodeURIComponent(q.v || '')).join('&')
}

async function onSend() {
  if (!form.url) { ElMessage.warning('请填写 URL'); return }
  sending.value = true
  try {
    if (form.mode === 'direct') {
      await runDirect()
    } else {
      await saveSilently()
      const res = await api.testCases.run(form.id)
      showResult({ ...res.data, mode: 'proxy' })
    }
  } catch (e) {
    ElMessage.error('发送失败：' + (e?.response?.data?.message || e.message || ''))
  } finally {
    sending.value = false
  }
}

async function onRun(row) {
  // 从列表直接运行（按用例保存的 mode）
  openEdit(row)
  formVisible.value = false
  await onSend()
}

async function runDirect() {
  // 浏览器直发：仅支持 ${var.变量名} 客户端替换；DSL 函数需后端转发(proxy)
  const rawFields = [form.url, ...(form.headers || []).map((h) => h.v), form.body]
  if (rawFields.some((f) => /\$\{(?!var\.)/.test(f || ''))) {
    showResult({ error: '浏览器直发不支持 DSL 函数（如 ${uuid()}），仅支持 ${var.变量名}。请改用「后端转发」模式。', passed: false, assertions: [], mode: 'direct' })
    return
  }
  const headers = {}
  form.headers.filter((h) => h.k).forEach((h) => { headers[substituteVars(h.k)] = substituteVars(h.v) })
  const opts = { method: form.method, headers }
  if (form.bodyType !== 'none' && form.body) opts.body = substituteVars(form.body)
  const t0 = Date.now()
  try {
    const resp = await fetch(substituteVars(buildUrl(form)), opts)
    const text = await resp.text()
    const rh = {}
    resp.headers.forEach((v, k) => { rh[k.toLowerCase()] = v })
    const ar = evalAssertionsClient(resp.status, rh, text, form.assertions)
    showResult({ httpStatus: resp.status, costMs: Date.now() - t0, headers: rh, body: text, assertions: ar, passed: ar.every((a) => a.passed), mode: 'direct' })
  } catch (e) {
    showResult({ error: '请求失败：可能是目标未允许跨域(CORS)，请改用「后端转发」模式。' + e.message, passed: false, assertions: [], mode: 'direct' })
  }
}

// ${var.name} 客户端替换（direct 模式用）
function substituteVars(text) {
  if (!text) return text
  return text.replace(/\$\{var\.([a-zA-Z_][\w]*)\}/g, (m, name) => {
    const v = variables.value.find((x) => x.name === name)
    return v ? (v.varValue || '') : m
  })
}

function insertBodyFn(text) {
  const cur = form.body || ''
  const el = bodyArea.value
  if (!el) { form.body = cur + text; return }
  const s = el.selectionStart || 0
  const e = el.selectionEnd || 0
  form.body = cur.slice(0, s) + text + cur.slice(e)
  nextTick(() => { el.focus(); el.selectionStart = el.selectionEnd = s + text.length })
}

function copyVar(v) {
  copyText('${var.' + v.name + '}')
}

// ===== 变量/常量 CRUD =====
async function loadVariables() {
  if (!proj.id) return
  try { const res = await api.testVariables.list(proj.id); variables.value = res.data || [] } catch (e) { variables.value = [] }
}

async function openVariables() {
  await loadVariables()
  varVisible.value = true
}

function openVarCreate() {
  Object.assign(varForm, { id: null, name: '', varValue: '', remark: '' })
  varFormVisible.value = true
}

function openVarEdit(row) {
  Object.assign(varForm, { id: row.id, name: row.name, varValue: row.varValue || '', remark: row.remark || '' })
  varFormVisible.value = true
}

async function onVarSave() {
  if (!varForm.name.trim()) { ElMessage.warning('请填变量名'); return }
  if (varForm.id) { await api.testVariables.update(varForm.id, { varValue: varForm.varValue, remark: varForm.remark }) }
  else { await api.testVariables.create(proj.id, { ...varForm }) }
  ElMessage.success('已保存')
  varFormVisible.value = false
  loadVariables()
}

async function onVarRemove(row) {
  await ElMessageBox.confirm(`删除变量「${row.name}」？`, '警告', { type: 'warning' })
  await api.testVariables.remove(row.id)
  ElMessage.success('已删除')
  loadVariables()
}

function evalAssertionsClient(status, headers, body, assertions) {
  return (assertions || []).map((a) => {
    const type = a.type, target = (a.target || ''), op = a.op || 'eq', expected = a.expected || ''
    let actual = '', passed = false
    try {
      if (type === 'status') { actual = String(status); passed = actual === expected }
      else if (type === 'bodyContains') { actual = body || ''; passed = actual.includes(expected) }
      else if (type === 'header') { const hv = headers[(target || '').toLowerCase()] || ''; actual = hv; passed = op === 'contains' ? hv.includes(expected) : hv === expected }
      else if (type === 'jsonPath') { const v = getJsonPath(body, target); if (op === 'exists') { actual = v === undefined ? '(无)' : String(v); passed = v !== undefined } else { actual = v == null ? '' : String(v); passed = op === 'contains' ? actual.includes(expected) : actual === expected } }
    } catch (e) { actual = '解析失败' }
    return { type, target, op, expected, actual, passed }
  })
}

function getJsonPath(body, path) {
  if (!body || !path) return undefined
  let o = typeof body === 'string' ? JSON.parse(body) : body
  const parts = path.replace(/^\$\.?/, '').split(/\.|\[(\d+)\]/).filter((p) => p !== '' && p !== undefined)
  for (const p of parts) { if (o == null) return undefined; o = o[p] }
  return o
}

function showResult(r) { result.value = r; resultVisible.value = true }

async function openHistory(row) {
  const res = await api.testCases.runs(row.id)
  history.value = res.data || []
  historyVisible.value = true
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除用例「${row.name || '未命名'}」及其历史？`, '警告', { type: 'warning' })
  await api.testCases.remove(row.id)
  ElMessage.success('已删除'); load()
}

watch(() => proj.id, () => { load(); loadVariables() })
onMounted(() => { load(); loadVariables() })
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.muted { color: #909399; font-size: 12px; }
.kv-title { font-size: 13px; color: #606266; margin: 8px 0 4px; display: flex; align-items: center; }
.kv-row { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
.hint { font-size: 12px; color: #909399; margin: 4px 0 8px; line-height: 1.7; }
.fn-panel { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; }
.fn-title { font-size: 12px; color: #606266; margin-bottom: 6px; }
.body-area { width: 100%; font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 13px; padding: 8px;
  border: 1px solid #dcdfe6; border-radius: 4px; resize: vertical; box-sizing: border-box; }
.resp { background: #f5f7fa; padding: 10px; border-radius: 4px; max-height: 240px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
</style>
