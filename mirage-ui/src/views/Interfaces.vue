<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <template v-else>
      <el-card style="margin-bottom: 12px">
        <template #header>
          <div class="card-header">
            <span>接口管理 <el-tag size="small">{{ proj.name }}</el-tag></span>
            <el-button type="primary" :icon="Plus" @click="openCreateInterface">新建接口</el-button>
          </div>
        </template>
        <el-table :data="interfaces" v-loading="loading" border stripe highlight-current-row @current-change="onSelectInterface" size="small">
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
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click.stop="openEditInterface(row)">编辑</el-button>
              <el-button size="small" type="danger" link @click.stop="onRemoveInterface(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="currentInterface">
        <template #header>
          <div class="card-header">
            <span>规则 <el-tag size="small">{{ currentInterface.name }}</el-tag></span>
            <el-button type="primary" :icon="Plus" size="small" @click="openCreateRule">新建规则</el-button>
          </div>
        </template>
        <el-table :data="rules" border size="small">
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
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="openEditRule(row)">编辑</el-button>
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
          <el-form-item label="路径"><el-input v-model="ifaceForm.httpPath" placeholder="/api/user/{userId}" /></el-form-item>
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
        <el-button type="primary" @click="saveInterface">保存</el-button>
      </template>
    </el-dialog>

    <RuleEditor v-model="ruleVisible" :rule="currentRule" :interface-id="currentInterface ? currentInterface.id : 0" :project-id="proj.id" @saved="loadRules" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, CopyDocument } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import { copyText as copy } from '../utils/clipboard'
import RuleEditor from '../components/RuleEditor.vue'

const proj = useProjectStore()
const interfaces = ref([])
const loading = ref(false)
const currentInterface = ref(null)
const rules = ref([])
const listeners = ref([])

const ifaceVisible = ref(false)
const ifaceForm = reactive({ id: null, name: '', protocol: 'HTTP', httpMethod: 'GET', httpPath: '', tcpListenerId: null, tcpRouteExpr: '', status: 1, remark: '' })

const ruleVisible = ref(false)
const currentRule = ref(null)

// Mock 访问端口（来自后端 /system/info），用于拼装可复制的访问链接
const mockPort = ref(0)
const previewUrl = computed(() => mockUrl({ protocol: ifaceForm.protocol, httpPath: ifaceForm.httpPath }))

function mockUrl(row) {
  if (!row || row.protocol !== 'HTTP' || !mockPort.value || !row.httpPath) return ''
  return `${location.protocol}//${location.hostname}:${mockPort.value}${row.httpPath}`
}

function genDefaultPath() {
  // 新建接口时自动生成一个唯一默认路径，用户可自由修改
  return '/api/itf_' + Math.random().toString(16).slice(2, 8)
}

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
  Object.assign(ifaceForm, { id: null, name: '', protocol: 'HTTP', httpMethod: 'GET', httpPath: genDefaultPath(), tcpListenerId: null, tcpRouteExpr: '', status: 1, remark: '' })
  loadListeners()
  ifaceVisible.value = true
}

function openEditInterface(row) {
  Object.assign(ifaceForm, row)
  loadListeners()
  ifaceVisible.value = true
}

async function saveInterface() {
  if (ifaceForm.id) {
    await api.interfaces.update(ifaceForm.id, { ...ifaceForm })
  } else {
    await api.interfaces.create(proj.id, { ...ifaceForm })
  }
  ElMessage.success('已保存')
  ifaceVisible.value = false
  loadInterfaces()
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

async function toggleRule(row) {
  await api.rules.toggle(row.id)
  loadRules()
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
