<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>测试用例 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <div>
            <el-input v-model="keyword" placeholder="搜索 名称/方法/URL" clearable size="small" style="width:220px;margin-right:8px" />
            <el-button @click="openVariables">变量/常量</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建用例</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filtered" v-loading="loading" border stripe size="small">
        <template #empty>
          <el-empty description="暂无用例">
            <el-button type="primary" size="small" @click="openCreate">新建用例</el-button>
          </el-empty>
        </template>
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column label="请求" show-overflow-tooltip>
          <template #default="{ row }"><el-tag size="small" :type="row.protocol === 'TCP' ? 'warning' : mTag(row.method)">{{ row.protocol === 'TCP' ? 'TCP' : row.method }}</el-tag> <span class="mono">{{ row.url }}</span></template>
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
        <el-form-item label="协议" label-width="56px">
          <el-radio-group v-model="form.protocol">
            <el-radio label="HTTP">HTTP</el-radio>
            <el-radio label="TCP">TCP</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- ===== HTTP ===== -->
        <template v-if="form.protocol === 'HTTP'">
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
          <el-radio-group v-model="form.bodyType" size="small" style="margin-left:10px">
            <el-radio-button label="none">none</el-radio-button>
            <el-radio-button label="form-data">form-data</el-radio-button>
            <el-radio-button label="x-www-form-urlencoded">x-www-form-urlencoded</el-radio-button>
            <el-radio-button label="raw">raw</el-radio-button>
            <el-radio-button label="binary">binary</el-radio-button>
          </el-radio-group>
        </div>

        <!-- form-data / x-www-form-urlencoded：键值表 -->
        <template v-if="form.bodyType === 'form-data' || form.bodyType === 'x-www-form-urlencoded'">
          <div v-for="(r, i) in form.formRows" :key="'br'+i" class="kv-row">
            <el-input v-model="r.k" placeholder="参数名" style="width:24%" />
            <el-select v-if="form.bodyType === 'form-data'" v-model="r.type" style="width:90px">
              <el-option label="文本" value="text" /><el-option label="文件" value="file" />
            </el-select>
            <el-input v-if="form.bodyType !== 'form-data' || r.type !== 'file'" v-model="r.v" placeholder="参数值（支持 ${var.x}）" style="flex:1" />
            <template v-else>
              <input type="file" class="file-input" @change="(e) => onPickFile(e, r)" />
              <span v-if="r.fileName" class="muted file-name">{{ r.fileName }}</span>
            </template>
            <el-button :icon="Delete" circle size="small" type="danger" @click="form.formRows.splice(i,1)" />
          </div>
          <el-button size="small" :icon="Plus" @click="addFormRow">加参数</el-button>
        </template>

        <!-- raw：Content-Type + 文本 + 函数市场 -->
        <template v-else-if="form.bodyType === 'raw'">
          <div class="kv-row" style="margin-bottom:6px">
            <span class="muted" style="margin-right:6px">Content-Type</span>
            <el-select v-model="form.bodyContentType" placeholder="选择或输入" filterable allow-create style="width:260px">
              <el-option label="application/json" value="application/json" />
              <el-option label="text/xml" value="text/xml" />
              <el-option label="text/plain" value="text/plain" />
              <el-option label="text/html" value="text/html" />
              <el-option label="application/javascript" value="application/javascript" />
            </el-select>
          </div>
          <el-row :gutter="8">
            <el-col :span="17">
              <textarea ref="bodyArea" v-model="form.body" class="body-area" rows="4" spellcheck="false" placeholder='{"key":"${var.x}"}'></textarea>
            </el-col>
            <el-col :span="7">
              <div class="fn-panel"><div class="fn-title">函数市场 · 插入光标处</div><FunctionMarketSidebar :project-id="proj.id" @insert="insertBodyFn" /></div>
            </el-col>
          </el-row>
        </template>

        <!-- binary：单文件原始字节 -->
        <template v-else-if="form.bodyType === 'binary'">
          <div class="kv-row" style="margin-bottom:6px">
            <input type="file" class="file-input" style="flex:1" @change="onPickBinary" />
            <el-button v-if="form.binaryFile.fileName" :icon="Delete" circle size="small" type="danger" @click="form.binaryFile = {}" />
          </div>
          <div v-if="form.binaryFile.fileName" class="muted">{{ form.binaryFile.fileName }} · {{ form.binaryFile.contentType || 'application/octet-stream' }} · {{ fileKb(form.binaryFile.dataB64) }}KB</div>
          <div v-else class="hint">选择文件作为原始字节请求体（Content-Type 默认 application/octet-stream；限 2MB）。</div>
        </template>

        <el-divider content-position="left">curl 导入</el-divider>
        <el-input v-model="curlText" type="textarea" :rows="2" placeholder="粘贴 curl 命令，点解析自动填充上方请求" />
        <el-button size="small" type="primary" style="margin-top:6px" @click="onImportCurl">解析 curl</el-button>

        </template>

        <!-- ===== TCP ===== -->
        <template v-else>
          <el-divider content-position="left">TCP 请求</el-divider>
          <div class="hint">
            目标 host:port；请求字段为 JSON 对象（值支持 <span class="mono">${var.x}</span>）。可从本项目 TCP 监听器一键填充帧/格式配置。
            响应按报文格式解析为字段，用 JSONPath 断言（如 <span class="mono">$.respCode</span>）。
          </div>
          <el-row :gutter="8" style="margin-bottom:6px">
            <el-col :span="12"><el-input v-model="form.url" placeholder="localhost:9001" /></el-col>
            <el-col :span="12">
              <el-select v-model="listenerPick" placeholder="从 TCP 监听器填充配置" clearable filterable style="width:100%" @change="fillFromListener">
                <el-option v-for="l in tcpListeners" :key="l.id" :label="l.name + ' (' + l.port + ')'" :value="l.id" />
              </el-select>
            </el-col>
          </el-row>
          <div class="kv-title">请求字段（JSON 对象）</div>
          <textarea v-model="form.body" class="body-area" rows="4" spellcheck="false" placeholder='{"transCode":"0200","serialNo":"${var.sn}","amount":"100.00"}'></textarea>
          <div class="kv-title" style="margin-top:10px">帧 / 格式配置（tcp_config，JSON）</div>
          <textarea v-model="form.tcpConfig" class="body-area" rows="3" spellcheck="false" placeholder='{"frameConfig":{"type":"length_field","lenBytes":4,"initialStrip":4},"messageFormat":"json"}'></textarea>
          <div class="hint" style="margin-top:4px">短连接一问一答；length_field 自动前置大端长度头，delimiter 追加分隔符，fixed 定长，close_end 原样。</div>
        </template>

        <el-divider content-position="left">断言</el-divider>
        <div v-for="(a, i) in form.assertions" :key="'a'+i" class="kv-row">
          <el-select v-model="a.type" style="width:150px">
            <el-option label="状态码 status" value="status" />
            <el-option label="响应体包含" value="bodyContains" />
            <el-option label="响应头 header" value="header" />
            <el-option label="JSONPath" value="jsonPath" />
            <el-option label="耗时<ms latencyLt" value="latencyLt" />
            <el-option label="响应大小>bytes" value="sizeGt" />
            <el-option label="响应头存在" value="headerExists" />
            <el-option label="JSON Schema" value="jsonSchema" />
          </el-select>
          <el-input v-if="['header','jsonPath','headerExists'].includes(a.type)" v-model="a.target" :placeholder="a.type === 'jsonPath' ? '$.data.id' : 'Header-Name'" style="width:22%" />
          <el-select v-else disabled style="width:22%" />
          <el-select v-if="['header','jsonPath'].includes(a.type)" v-model="a.op" style="width:90px">
            <el-option label="等于 =" value="eq" />
            <el-option label="包含 contains" value="contains" />
            <template v-if="a.type === 'jsonPath'">
              <el-option label="不等于 ≠" value="ne" />
              <el-option label="大于 >" value="gt" />
              <el-option label="小于 <" value="lt" />
              <el-option label="大于等于 ≥" value="ge" />
              <el-option label="小于等于 ≤" value="le" />
              <el-option label="存在 exists" value="exists" />
            </template>
          </el-select>
          <el-input v-if="a.op !== 'exists' && a.type !== 'headerExists'" v-model="a.expected" :placeholder="a.type === 'jsonSchema' ? 'JSON Schema' : (a.type === 'latencyLt' ? 'ms 阈值' : a.type === 'sizeGt' ? 'bytes 阈值' : '期望值')" style="width:30%" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="form.assertions.splice(i,1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="form.assertions.push({ type: 'status', target: '', op: 'eq', expected: '200' })">加断言</el-button>

        <el-divider content-position="left">数据驱动（可选）</el-divider>
        <div class="hint">
          填写数据行（JSON 数组），每行键值注入 <span class="mono">${var.键}</span>，逐行运行用例（仅「后端转发」）。留空=普通单跑。
          如 <span class="mono">[{"uid":10086},{"uid":10087}]</span>，URL 写 <span class="mono">/api/user/${var.uid}</span>。
        </div>
        <textarea v-model="form.dataSet" class="body-area" rows="4" spellcheck="false" placeholder='[{"uid":10086},{"uid":10087}]'></textarea>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="success" @click="onSend" :loading="sending">发送</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
        <el-button type="warning" @click="onRunData" :loading="dataSending" :disabled="form.mode !== 'proxy'">数据驱动运行</el-button>
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
        <ResponseBody v-if="result.body != null" :body="result.body" />
      </div>
    </el-dialog>

    <!-- 数据驱动运行结果 -->
    <el-dialog v-model="dataResultVisible" title="数据驱动运行结果" width="900px" top="3vh">
      <div v-if="dataResult">
        <div style="margin-bottom:8px">
          <el-tag :type="dataResult.passed ? 'success' : 'danger'">{{ dataResult.passed ? '✓ 全部通过' : '✗ 存在失败' }}</el-tag>
          <span class="muted" style="margin-left:8px">通过 {{ dataResult.passedCount }}/{{ dataResult.total }} 行</span>
        </div>
        <el-table :data="dataResultRows" size="small" border max-height="440">
          <el-table-column prop="_row" label="行" width="56" />
          <el-table-column label="数据行变量" width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row._vars ? JSON.stringify(row._vars) : '' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="56"><template #default="{ row }">{{ row.passed ? '✓' : '✗' }}</template></el-table-column>
          <el-table-column prop="httpStatus" label="状态" width="56" />
          <el-table-column label="耗时" width="76"><template #default="{ row }">{{ row.costMs }}ms</template></el-table-column>
          <el-table-column prop="error" label="错误" width="120" show-overflow-tooltip />
          <el-table-column label="响应体" show-overflow-tooltip><template #default="{ row }">{{ row.body }}</template></el-table-column>
        </el-table>
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
import { useTestCaseDraftStore } from '../store/testcaseDraft'
import { parseCurl } from '../utils/curl'
import { copyText } from '../utils/clipboard'
import FunctionMarketSidebar from '../components/FunctionMarketSidebar.vue'
import ResponseBody from '../components/ResponseBody.vue'

const proj = useProjectStore()
const draftStore = useTestCaseDraftStore()
const list = ref([])
const loading = ref(false)
const keyword = ref('')
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return list.value
  return list.value.filter((t) => ((t.name || '') + ' ' + (t.method || '') + ' ' + (t.url || '')).toLowerCase().includes(k))
})

const formVisible = ref(false)
const sending = ref(false)
const curlText = ref('')
const form = reactive({ id: null, name: '', protocol: 'HTTP', method: 'GET', url: '', headers: [], query: [], bodyType: 'none', body: '', bodyContentType: '', tcpConfig: '', formRows: [], binaryFile: {}, assertions: [], mode: 'proxy', status: 1, remark: '', dataSet: '' })

const resultVisible = ref(false)
const result = ref(null)
const respHeaders = computed(() => {
  if (!result.value || !result.value.headers) return []
  return Object.entries(result.value.headers).map(([k, v]) => ({ k, v }))
})

const historyVisible = ref(false)
const history = ref([])

// 数据驱动运行
const dataResultVisible = ref(false)
const dataResult = ref(null)
const dataSending = ref(false)
const dataRows = computed(() => { try { return JSON.parse(form.dataSet || '[]') } catch (e) { return [] } })
const dataResultRows = computed(() => {
  if (!dataResult.value || !dataResult.value.results) return []
  return dataResult.value.results.map((r, i) => ({ ...r, _vars: dataRows.value[i], _row: i + 1 }))
})

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
  Object.assign(form, { id: null, name: '', protocol: 'HTTP', method: 'GET', url: '', headers: [], query: [], bodyType: 'none', body: '', bodyContentType: '', tcpConfig: '', formRows: [], binaryFile: {}, assertions: [], mode: 'proxy', status: 1, remark: '', dataSet: '' })
  listenerPick.value = ''
  curlText.value = ''
  formVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.name = row.name; form.protocol = row.protocol || 'HTTP'; form.method = row.method || 'GET'; form.url = row.url || ''
  form.tcpConfig = row.tcpConfig || ''
  loadBody(row)
  form.mode = row.mode || 'proxy'; form.status = row.status == null ? 1 : row.status; form.remark = row.remark || ''
  form.headers = parseArr(row.headers); form.query = parseArr(row.query); form.assertions = parseArr(row.assertions)
  form.dataSet = row.dataSet || ''
  listenerPick.value = ''
  curlText.value = ''
  formVisible.value = true
}

function parseArr(s) { try { return JSON.parse(s || '[]') } catch (e) { return [] } }
function parseObj(s) { try { return JSON.parse(s || '{}') } catch (e) { return {} } }

const MAX_FILE = 2 * 1024 * 1024

// 按 bodyType 把后端存储反序列化到编辑态（legacy json/form 归一化为 raw）
function loadBody(row) {
  const bt = row.bodyType || 'none'
  form.body = row.body || ''
  form.bodyContentType = row.bodyContentType || ''
  form.formRows = []
  form.binaryFile = {}
  if (bt === 'json') { form.bodyType = 'raw'; form.bodyContentType = 'application/json' }
  else if (bt === 'form') { form.bodyType = 'raw'; form.bodyContentType = 'application/x-www-form-urlencoded' }
  else { form.bodyType = bt }
  if (form.bodyType === 'form-data' || form.bodyType === 'x-www-form-urlencoded') {
    form.formRows = parseArr(row.body)
  } else if (form.bodyType === 'binary') {
    form.binaryFile = parseObj(row.body)
  }
}

// 按编辑态序列化为后端 body 字段
function serializeBody() {
  if (form.bodyType === 'form-data' || form.bodyType === 'x-www-form-urlencoded') {
    return JSON.stringify((form.formRows || []).filter((r) => r.k))
  }
  if (form.bodyType === 'binary') {
    return form.binaryFile && form.binaryFile.dataB64 ? JSON.stringify(form.binaryFile) : ''
  }
  return form.body || ''
}
function serializeContentType() {
  if (form.bodyType === 'raw') return form.bodyContentType || ''
  if (form.bodyType === 'binary') return (form.binaryFile && form.binaryFile.contentType) || ''
  return ''
}

function addFormRow() {
  if (form.bodyType === 'form-data') {
    form.formRows.push({ k: '', v: '', type: 'text', fileName: '', contentType: '', dataB64: '' })
  } else {
    form.formRows.push({ k: '', v: '' })
  }
}

function readB64(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => { const d = String(r.result || ''); const i = d.indexOf(','); resolve(i >= 0 ? d.slice(i + 1) : d) }
    r.onerror = () => reject(r.error)
    r.readAsDataURL(file)
  })
}
async function onPickFile(e, row) {
  const f = e.target.files && e.target.files[0]
  if (!f) return
  if (f.size > MAX_FILE) { ElMessage.warning('文件不能超过 2MB'); e.target.value = ''; return }
  const b64 = await readB64(f)
  row.type = 'file'; row.fileName = f.name; row.contentType = f.type || 'application/octet-stream'; row.dataB64 = b64; row.v = ''
  e.target.value = ''
}
async function onPickBinary(e) {
  const f = e.target.files && e.target.files[0]
  if (!f) return
  if (f.size > MAX_FILE) { ElMessage.warning('文件不能超过 2MB'); e.target.value = ''; return }
  const b64 = await readB64(f)
  form.binaryFile = { fileName: f.name, contentType: f.type || 'application/octet-stream', dataB64: b64 }
  e.target.value = ''
}
function fileKb(b64) { if (!b64) return '0'; return String(Math.round(b64.length * 0.75 / 1024 * 10) / 10) }

// 浏览器直发(direct) 按类型构造 fetch 体
function buildDirectBody() {
  const t = form.bodyType
  if (t === 'none') return {}
  if (t === 'x-www-form-urlencoded') {
    const p = new URLSearchParams()
    ;(form.formRows || []).forEach((r) => { if (r.k) p.append(substituteVars(r.k), substituteVars(r.v || '')) })
    return { body: p }
  }
  if (t === 'form-data') {
    const fd = new FormData()
    ;(form.formRows || []).forEach((r) => {
      if (!r.k) return
      if (r.type === 'file' && r.dataB64) {
        const bytes = Uint8Array.from(atob(r.dataB64), (c) => c.charCodeAt(0))
        fd.append(substituteVars(r.k), new Blob([bytes], { type: r.contentType || 'application/octet-stream' }), substituteVars(r.fileName || 'file'))
      } else {
        fd.append(substituteVars(r.k), substituteVars(r.v || ''))
      }
    })
    return { body: fd }
  }
  if (t === 'binary') {
    if (form.binaryFile && form.binaryFile.dataB64) {
      const bytes = Uint8Array.from(atob(form.binaryFile.dataB64), (c) => c.charCodeAt(0))
      return { body: bytes, contentType: form.binaryFile.contentType || 'application/octet-stream' }
    }
    return {}
  }
  if (form.body) return { body: substituteVars(form.body), contentType: form.bodyContentType || undefined }
  return {}
}

function buildEntity() {
  return {
    id: form.id, name: form.name, protocol: form.protocol, method: form.method, url: form.url,
    bodyType: form.bodyType, body: serializeBody(), bodyContentType: serializeContentType(),
    tcpConfig: form.protocol === 'TCP' ? form.tcpConfig : '',
    mode: form.mode, status: form.status, remark: form.remark,
    headers: JSON.stringify(form.headers.filter((h) => h.k)),
    query: JSON.stringify(form.query.filter((q) => q.k)),
    assertions: JSON.stringify(form.assertions),
    dataSet: (form.dataSet || '').trim()
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
    form.method = c.method; form.url = c.url; form.headers = c.headers
    form.bodyType = c.bodyType; form.body = c.body || ''; form.bodyContentType = c.bodyContentType || ''
    form.formRows = (c.bodyType === 'form-data' || c.bodyType === 'x-www-form-urlencoded') ? parseArr(c.body) : []
    form.binaryFile = {}
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
  // 从列表直接运行：有数据行 → 数据驱动，否则普通运行
  openEdit(row)
  formVisible.value = false
  if ((row.dataSet || '').trim()) {
    await onRunData()
  } else {
    await onSend()
  }
}

async function onRunData() {
  if (!form.url) { ElMessage.warning('请填写 URL'); return }
  let rows = []
  if ((form.dataSet || '').trim()) {
    try { rows = JSON.parse(form.dataSet) } catch (e) { ElMessage.error('数据行 JSON 解析失败：' + e.message); return }
    if (!Array.isArray(rows)) { ElMessage.error('数据行必须是 JSON 数组'); return }
  }
  dataSending.value = true
  try {
    await saveSilently()
    const res = await api.testCases.runData(form.id)
    dataResult.value = res.data
    dataResultVisible.value = true
    formVisible.value = false
  } catch (e) {
    ElMessage.error('数据驱动运行失败：' + (e?.response?.data?.message || e.message || ''))
  } finally {
    dataSending.value = false
  }
}

async function runDirect() {
  // 浏览器直发：仅支持 ${var.变量名} 客户端替换；DSL 函数需后端转发(proxy)
  const textFields = [form.url, ...(form.headers || []).map((h) => h.v), form.body, ...(form.formRows || []).map((r) => r.k + (r.v || ''))]
  if (textFields.some((f) => /\$\{(?!var\.)/.test(f || ''))) {
    showResult({ error: '浏览器直发不支持 DSL 函数（如 ${uuid()}），仅支持 ${var.变量名}。请改用「后端转发」模式。', passed: false, assertions: [], mode: 'direct' })
    return
  }
  const headers = {}
  form.headers.filter((h) => h.k).forEach((h) => { headers[substituteVars(h.k)] = substituteVars(h.v) })
  const opts = { method: form.method, headers }
  const bd = buildDirectBody()
  if (bd.body !== undefined && bd.body !== null) {
    opts.body = bd.body
    if (bd.contentType && !headers['Content-Type'] && !headers['content-type']) headers['Content-Type'] = bd.contentType
  }
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
      else if (type === 'jsonPath') {
        const v = getJsonPath(body, target)
        const disp = (v === undefined || v === null) ? '' : (typeof v === 'object' ? JSON.stringify(v) : String(v))
        actual = disp
        if (op === 'exists') { actual = (v === undefined || v === null) ? '(无)' : disp; passed = v !== undefined && v !== null }
        else if (op === 'contains') { passed = disp.includes(expected) }
        else if (op === 'ne') { passed = !smartEq(v, disp, expected) }
        else if (['gt', 'lt', 'ge', 'le'].includes(op)) { passed = cmpNum(v, expected, op) }
        else { passed = smartEq(v, disp, expected) } // eq
      }
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

// 数值感知比较（与后端 evalAssertion 对齐）：两边均可解析为数字时按数值比
function toNum(v) {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  const s = String(v == null ? '' : v).trim()
  if (s === '') return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}
function smartEq(v, disp, expected) {
  const a = toNum(v), b = toNum(expected)
  if (a !== null && b !== null) return Math.abs(a - b) < 1e-9
  return disp === expected
}
function cmpNum(v, expected, op) {
  const a = toNum(v), b = toNum(expected)
  if (a === null || b === null) return false
  if (op === 'gt') return a > b
  if (op === 'lt') return a < b
  if (op === 'ge') return a >= b
  if (op === 'le') return a <= b
  return false
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
// TCP 监听器：填充帧/格式配置
const tcpListeners = ref([])
const listenerPick = ref('')
async function loadListeners() {
  if (!proj.id) { tcpListeners.value = []; return }
  try { const r = await api.listeners.list(proj.id); tcpListeners.value = r.data || [] } catch (e) { tcpListeners.value = [] }
}
function fillFromListener(id) {
  const l = tcpListeners.value.find((x) => x.id === id)
  if (!l) return
  form.url = 'localhost:' + l.port
  let formatConfig = null
  try { formatConfig = l.messageFormatConfig ? JSON.parse(l.messageFormatConfig) : null } catch (e) { /* 留空 */ }
  form.tcpConfig = JSON.stringify({
    frameConfig: l.frameConfig || '',
    messageFormat: l.messageFormat || 'json',
    formatConfig
  })
}

onMounted(() => {
  load()
  loadVariables()
  loadListeners()
  // 来自「请求日志 → 生成用例」的草稿：取出并打开新建弹窗（id 为空即新建）
  const draft = draftStore.take()
  if (draft) {
    openEdit(draft)
  }
})
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.muted { color: #909399; font-size: 12px; }
.kv-title { font-size: 13px; color: #606266; margin: 8px 0 4px; display: flex; align-items: center; }
.kv-row { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
.file-input { font-size: 12px; flex: 1; }
.file-name { margin-left: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 160px; }
.hint { font-size: 12px; color: #909399; margin: 4px 0 8px; line-height: 1.7; }
.fn-panel { border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; }
.fn-title { font-size: 12px; color: #606266; margin-bottom: 6px; }
.body-area { width: 100%; font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 13px; padding: 8px;
  border: 1px solid #dcdfe6; border-radius: 4px; resize: vertical; box-sizing: border-box; }
.resp { background: #f5f7fa; padding: 10px; border-radius: 4px; max-height: 240px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
</style>
