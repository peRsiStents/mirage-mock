<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <template v-else>
      <el-card style="margin-bottom: 12px">
        <template #header>
          <div class="card-header">
            <span>接口管理 <el-tag size="small">{{ proj.name }}</el-tag></span>
            <el-input v-model="keyword" placeholder="搜索 名称/方法/路径" clearable size="small" style="width:200px;margin-right:8px" />
            <el-button type="primary" :icon="Plus" @click="openCreateInterface">新建接口</el-button>
          </div>
        </template>
        <el-table :data="filtered" v-loading="loading" border stripe highlight-current-row @current-change="onSelectInterface" size="small">
          <template #empty>
            <el-empty description="暂无接口">
              <el-button type="primary" size="small" @click="openCreateInterface">新建接口</el-button>
            </el-empty>
          </template>
          <el-table-column prop="name" label="名称" />
          <el-table-column label="协议" width="80">
            <template #default="{ row }"><el-tag :type="row.protocol === 'HTTP' ? 'primary' : 'warning'" size="small">{{ row.protocol }}</el-tag></template>
          </el-table-column>
          <el-table-column label="路由">
            <template #default="{ row }">
              <span v-if="row.protocol === 'HTTP'" class="mono">{{ row.httpMethod }} {{ row.httpPath }}</span>
              <span v-else class="mono">交易码 {{ row.tcpRouteExpr }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Mock 地址" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <div v-if="row.protocol === 'HTTP' && mockUrl(row)" class="mock-addr">
                <span class="mono url-text">{{ mockUrl(row) }}</span>
                <el-button size="small" link :icon="CopyDocument" @click.stop="copy(mockUrl(row))" title="复制访问链接" />
              </div>
              <span v-else class="mono" :title="row.protocol === 'TCP' ? 'TCP 接口请通过监听器端口 + 交易码访问' : ''">
                {{ row.protocol === 'TCP' ? `监听器 #${row.tcpListenerId} · 交易码 ${row.tcpRouteExpr}` : '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="330">
            <template #default="{ row }">
              <el-button v-if="row.protocol === 'HTTP'" size="small" type="success" link :loading="trying[row.id]" @click.stop="onTryMock(row)">试跑</el-button>
              <el-button size="small" type="primary" link @click.stop="openEditInterface(row)">编辑</el-button>
              <el-button size="small" link @click.stop="onCloneInterface(row)" title="克隆接口及其全部规则">复制</el-button>
              <el-button size="small" type="danger" link @click.stop="onRemoveInterface(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="currentInterface">
        <template #header>
          <div class="card-header">
            <span>规则 <el-tag size="small">{{ currentInterface.name }}</el-tag></span>
            <div>
              <el-button size="small" :disabled="!selectedRules.length" @click="onBatchStatus(1)">批量启用</el-button>
              <el-button size="small" :disabled="!selectedRules.length" @click="onBatchStatus(0)">批量停用</el-button>
              <el-button type="danger" size="small" :icon="Delete" :disabled="!selectedRules.length" @click="onBatchRemoveRules">批量删除<span v-if="selectedRules.length">（{{ selectedRules.length }}）</span></el-button>
              <el-button type="primary" :icon="Plus" size="small" @click="openCreateRule">新建规则</el-button>
            </div>
          </div>
        </template>
        <el-table :data="rules" border size="small" @selection-change="onRuleSelectionChange">
          <el-table-column type="selection" width="42" />
          <template #empty>
            <el-empty description="暂无规则" :image-size="60">
              <el-button type="primary" size="small" @click="openCreateRule">新建规则</el-button>
            </el-empty>
          </template>
          <el-table-column prop="priority" label="优先级" width="80" />
          <el-table-column prop="name" label="规则名" />
          <el-table-column label="匹配条件" show-overflow-tooltip>
            <template #default="{ row }"><span class="mono">{{ row.matchCondition || '[]（兜底）' }}</span></template>
          </el-table-column>
          <el-table-column label="延迟/故障" width="140">
            <template #default="{ row }">{{ row.delayType }} / {{ row.faultType }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" @change="toggleRule(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="310">
            <template #default="{ row, $index }">
              <el-button size="small" link :icon="Top" :disabled="$index === 0" title="上移（更优先）" @click="moveRule($index, -1)" />
              <el-button size="small" link :icon="Bottom" :disabled="$index === rules.length - 1" title="下移" @click="moveRule($index, 1)" />
              <el-button size="small" type="primary" link @click="openEditRule(row)">编辑</el-button>
              <el-button size="small" link @click="onCloneRule(row)" title="基于此规则新建一条">复制</el-button>
              <el-button size="small" type="danger" link @click="onRemoveRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-dialog v-model="ifaceVisible" :title="ifaceForm.id ? '编辑接口' : '新建接口'" width="560px">
      <el-form :model="ifaceForm" label-width="100px">
        <el-form-item label="名称"><el-input v-model="ifaceForm.name" /></el-form-item>
        <el-form-item label="协议">
          <el-radio-group v-model="ifaceForm.protocol">
            <el-radio label="HTTP">HTTP</el-radio>
            <el-radio label="TCP">TCP</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="ifaceForm.protocol === 'HTTP'">
          <el-form-item label="方法">
            <el-select v-model="ifaceForm.httpMethod">
              <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH','ANY']" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="路径"><el-input v-model="ifaceForm.httpPath" placeholder="/api/user/{userId}" @input="pathManuallyEdited = true" /></el-form-item>
          <el-form-item label="访问地址">
            <div class="preview-url">
              <span class="mono">{{ previewUrl || '（填写路径后生成）' }}</span>
              <el-button size="small" link type="primary" :icon="CopyDocument" :disabled="!previewUrl" @click="copy(previewUrl)">复制</el-button>
            </div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="TCP 监听">
            <el-select v-model="ifaceForm.tcpListenerId" placeholder="选择监听器">
              <el-option v-for="l in listeners" :key="l.id" :label="l.name + ' :' + l.port" :value="l.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="交易码"><el-input v-model="ifaceForm.tcpRouteExpr" placeholder="0200" /></el-form-item>
        </template>
        <el-form-item label="状态"><el-switch v-model="ifaceForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="ifaceForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ifaceVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveInterface">保存</el-button>
      </template>
    </el-dialog>

    <RuleEditor v-model="ruleVisible" :rule="currentRule" :interface-id="currentInterface ? currentInterface.id : 0" :project-id="proj.id" @saved="loadRules" />

    <el-dialog v-model="tryVisible" title="Mock 试跑" width="720px">
      <div v-if="tryResult" style="margin-bottom:8px">
        <el-tag :type="sTag(tryResult.status)">HTTP {{ tryResult.status }}</el-tag>
        <span class="mono" style="margin-left:8px;color:#909399;font-size:12px">{{ tryResult.url }}</span>
      </div>
      <ResponseBody v-if="tryResult" :body="tryResult.body" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, CopyDocument, Delete, Top, Bottom } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import { copyText as copy } from '../utils/clipboard'
import { pinyin } from 'pinyin-pro'
import RuleEditor from '../components/RuleEditor.vue'
import ResponseBody from '../components/ResponseBody.vue'

const proj = useProjectStore()
const interfaces = ref([])
const loading = ref(false)
const keyword = ref('')
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return interfaces.value
  return interfaces.value.filter((it) => ((it.name || '') + ' ' + (it.httpMethod || '') + ' ' + (it.httpPath || '')).toLowerCase().includes(k))
})
const currentInterface = ref(null)
const rules = ref([])
const listeners = ref([])

const ifaceVisible = ref(false)
const ifaceForm = reactive({ id: null, name: '', protocol: 'HTTP', httpMethod: 'GET', httpPath: '', tcpListenerId: null, tcpRouteExpr: '', status: 1, remark: '' })

const ruleVisible = ref(false)
const currentRule = ref(null)
const saving = ref(false)
const selectedRules = ref([])

// Mock 访问端口（来自后端 /system/info），用于拼装可复制的访问链接
const mockPort = ref(0)
const previewUrl = computed(() => mockUrl({ protocol: ifaceForm.protocol, httpPath: ifaceForm.httpPath }))

function mockUrl(row) {
  if (!row || row.protocol !== 'HTTP' || !mockPort.value || !row.httpPath) return ''
  return `${location.protocol}//${location.hostname}:${mockPort.value}${row.httpPath}`
}

// 路径是否被用户手动改过（未改时随接口名自动生成）
const pathManuallyEdited = ref(false)

function genInterfacePath(name) {
  const code = (proj.code || 'app').trim().toLowerCase().replace(/[^a-z0-9_-]/g, '') || 'app'
  const seg = nameToSlug(name)
  return '/api/' + code + (seg ? '/' + seg : '')
}

// 中文名转拼音 slug（英文/数字保留，其余丢弃），如「获取用户」-> huo-qu-yong-hu
function nameToSlug(name) {
  if (!name) return ''
  const py = pinyin(name, { toneType: 'none', nonZh: 'consecutive' })
  return py.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
}

// 新建时接口名变化 → 自动生成路径（用户手改过则不覆盖）
watch(() => ifaceForm.name, () => {
  if (ifaceForm.protocol === 'HTTP' && !pathManuallyEdited.value) {
    ifaceForm.httpPath = genInterfacePath(ifaceForm.name)
  }
})

async function loadSystemInfo() {
  try {
    const res = await api.system.info()
    if (res.data && res.data.httpEnabled) mockPort.value = res.data.httpPort
  } catch {
    // 忽略：拿不到端口则不显示链接
  }
}

async function loadInterfaces() {
  if (!proj.id) return
  loading.value = true
  try {
    const res = await api.interfaces.list(proj.id)
    interfaces.value = res.data || []
    if (!currentInterface.value && interfaces.value.length) {
      onSelectInterface(interfaces.value[0])
    }
  } finally {
    loading.value = false
  }
}

async function loadListeners() {
  if (!proj.id) return
  const res = await api.listeners.list(proj.id)
  listeners.value = res.data || []
}

async function loadRules() {
  if (!currentInterface.value) return
  const res = await api.rules.list(currentInterface.value.id)
  rules.value = res.data || []
}

function onSelectInterface(row) {
  if (!row) return
  currentInterface.value = row
  loadRules()
}

function openCreateInterface() {
  Object.assign(ifaceForm, { id: null, name: '', protocol: 'HTTP', httpMethod: 'GET', httpPath: genInterfacePath(''), tcpListenerId: null, tcpRouteExpr: '', status: 1, remark: '' })
  pathManuallyEdited.value = false
  loadListeners()
  ifaceVisible.value = true
}

function openEditInterface(row) {
  pathManuallyEdited.value = true
  Object.assign(ifaceForm, row)
  loadListeners()
  ifaceVisible.value = true
}

async function saveInterface() {
  if (!ifaceForm.name || !ifaceForm.name.trim()) { ElMessage.warning('请输入接口名称'); return }
  if (ifaceForm.protocol !== 'TCP' && (!ifaceForm.httpPath || !ifaceForm.httpPath.trim())) { ElMessage.warning('请输入接口路径'); return }
  saving.value = true
  try {
    if (ifaceForm.id) {
      await api.interfaces.update(ifaceForm.id, { ...ifaceForm })
    } else {
      await api.interfaces.create(proj.id, { ...ifaceForm })
    }
    ElMessage.success('已保存并即时生效')
    ifaceVisible.value = false
    loadInterfaces()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onRemoveInterface(row) {
  await ElMessageBox.confirm(`删除接口「${row.name}」及其全部规则？`, '警告', { type: 'warning' })
  await api.interfaces.remove(row.id)
  ElMessage.success('已删除')
  if (currentInterface.value && currentInterface.value.id === row.id) currentInterface.value = null
  loadInterfaces()
}

function openCreateRule() {
  currentRule.value = null
  ruleVisible.value = true
}

function openEditRule(row) {
  currentRule.value = { ...row }
  ruleVisible.value = true
}

async function onRemoveRule(row) {
  await ElMessageBox.confirm(`删除规则「${row.name}」？`, '警告', { type: 'warning' })
  await api.rules.remove(row.id)
  ElMessage.success('已删除')
  loadRules()
}

function onRuleSelectionChange(rows) { selectedRules.value = rows }
async function onBatchRemoveRules() {
  if (!selectedRules.value.length) return
  await ElMessageBox.confirm(`确认删除选中的 ${selectedRules.value.length} 条规则？`, '批量删除', { type: 'warning' })
  try {
    await api.rules.removeBatch(selectedRules.value.map((r) => r.id))
    ElMessage.success('已删除 ' + selectedRules.value.length + ' 条')
    selectedRules.value = []
    loadRules()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function onBatchStatus(status) {
  if (!selectedRules.value.length) return
  try {
    await api.rules.setBatchStatus(selectedRules.value.map((r) => r.id), status)
    ElMessage.success((status === 1 ? '已启用 ' : '已停用 ') + selectedRules.value.length + ' 条')
    selectedRules.value = []
    loadRules()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function moveRule(index, delta) {
  const j = index + delta
  if (j < 0 || j >= rules.value.length) return
  const a = rules.value[index], b = rules.value[j]
  const pa = a.priority ?? 100, pb = b.priority ?? 100
  try {
    if (pa === pb) {
      // 优先级相同：直接给移动方更小(上)/更大(下)的值打破平局
      await api.rules.setPriority(a.id, pa + delta)
    } else {
      await api.rules.setPriority(a.id, pb)
      await api.rules.setPriority(b.id, pa)
    }
    ElMessage.success('已调整顺序')
    loadRules()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function toggleRule(row) {
  await api.rules.toggle(row.id)
  loadRules()
}

// ===== Mock 页内试跑（HTTP 接口直接 fetch 自家 mockUrl，看命中规则与返回）=====
const trying = reactive({})
const tryVisible = ref(false)
const tryResult = ref(null)
function sTag(s) { return s >= 200 && s < 300 ? 'success' : (s >= 400 ? 'danger' : 'info') }
async function onTryMock(row) {
  const url = mockUrl(row)
  if (!url) { ElMessage.warning('无法生成 Mock 访问地址（HTTP 端口未开启？）'); return }
  trying[row.id] = true
  try {
    const resp = await fetch(url, { method: 'GET' })
    const text = await resp.text()
    tryResult.value = { status: resp.status, body: text, url }
    tryVisible.value = true
  } catch (e) {
    ElMessage.error('试跑失败：' + (e?.message || '网络错误'))
  } finally {
    trying[row.id] = false
  }
}

// ===== 一键克隆 =====
function onCloneRule(row) {
  currentRule.value = { ...row, id: null, name: (row.name || '') + '(副本)' }
  ruleVisible.value = true
}

async function onCloneInterface(row) {
  try {
    const { id, createTime, updateTime, ...rest } = row
    const res = await api.interfaces.create(proj.id, { ...rest, name: (row.name || '') + '(副本)' })
    const newId = res.data.id
    const rr = await api.rules.list(row.id)
    for (const r of (rr.data || [])) {
      const { id: _id, interfaceId: _iid, createTime: _c, updateTime: _u, ...rrest } = r
      await api.rules.create(newId, { ...rrest, name: (r.name || '') + '(副本)' })
    }
    ElMessage.success('已克隆接口及 ' + (rr.data?.length || 0) + ' 条规则')
    loadInterfaces()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

watch(() => proj.id, () => { currentInterface.value = null; loadInterfaces() })
onMounted(() => { loadSystemInfo(); loadInterfaces(); loadListeners() })
</script>

<style scoped>
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.mock-addr { display: flex; align-items: center; gap: 4px; }
.mock-addr .url-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 240px; }
.preview-url { display: flex; align-items: center; gap: 8px; width: 100%; }
.preview-url .mono { color: #409eff; word-break: break-all; }
</style>
