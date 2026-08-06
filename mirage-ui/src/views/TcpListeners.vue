<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>TCP 监听管理 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建监听器</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <template #empty>
          <el-empty description="暂无监听器">
            <el-button type="primary" size="small" @click="openCreate">新建监听器</el-button>
          </el-empty>
        </template>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column prop="connMode" label="连接" width="80" />
        <el-table-column prop="matchMode" label="匹配" width="80" />
        <el-table-column prop="messageFormat" label="报文格式" width="110" />
        <el-table-column label="帧切分" width="110">
          <template #default="{ row }">{{ frameType(row.frameConfig) }}</template>
        </el-table-column>
        <el-table-column prop="routeExtract" label="路由提取" width="120" />
        <el-table-column label="运行" width="80">
          <template #default="{ row }">
            <el-tag :type="running[row.id] ? 'success' : 'info'" size="small">{{ running[row.id] ? '运行中' : '已停止' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button v-if="!running[row.id]" size="small" type="success" :loading="opLoading[row.id]" @click="start(row)">启动</el-button>
            <el-button v-else size="small" type="warning" :loading="opLoading[row.id]" @click="stop(row)">停止</el-button>
            <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="form.id ? '编辑监听器' : '新建监听器'" width="720px">
      <el-form :model="form" label-width="120px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="连接模式">
              <el-select v-model="form.connMode"><el-option label="长连接 LONG" value="LONG" /><el-option label="短连接 SHORT" value="SHORT" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="匹配模式">
              <el-select v-model="form.matchMode"><el-option label="串行 SYNC" value="SYNC" /><el-option label="流水号 ASYNC" value="ASYNC" /></el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="报文格式">
          <el-select v-model="form.messageFormat">
            <el-option label="JSON" value="json" />
            <el-option label="键值对 key_value" value="key_value" />
            <el-option label="定长字段 fixed_fields" value="fixed_fields" />
            <el-option label="Hex 字符串 hex_string" value="hex_string" />
          </el-select>
        </el-form-item>
        <el-form-item label="帧切分配置">
          <div class="cfg-block">
            <div class="cfg-bar">
              <el-select v-model="framePresetType" placeholder="选类型加载示例" style="width: 220px" @change="applyFramePreset">
                <el-option label="长度头 length_field" value="length_field" />
                <el-option label="分隔符 delimiter" value="delimiter" />
                <el-option label="定长 fixed" value="fixed" />
                <el-option label="读到关闭 close_end" value="close_end" />
              </el-select>
              <el-button size="small" link @click="formatCfg('frameConfig')">格式化</el-button>
            </div>
            <el-input v-model="form.frameConfig" type="textarea" :rows="2" class="mono" placeholder='{"type":"length_field","lenBytes":4,"offset":0,"adjustment":0,"initialStrip":4}' />
          </div>
        </el-form-item>
        <el-form-item label="格式专属配置">
          <div class="cfg-block">
            <div class="cfg-bar"><el-button size="small" link @click="loadMsgFmtExample">加载示例（按报文格式）</el-button></div>
            <el-input v-model="form.messageFormatConfig" type="textarea" :rows="2" class="mono" placeholder='定长字段: {"fields":[{"name":"orgNo","len":10}]}   键值对: {"pairSep":"&","kvSep":"="}' />
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="路由提取"><el-input v-model="form.routeExtract" placeholder="$.transCode / field:orgNo / kv:type" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="流水号提取"><el-input v-model="form.serialExtract" placeholder="$.serialNo" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="主动推送配置">
          <div class="cfg-block">
            <div class="cfg-bar"><el-button size="small" link @click="loadPushExample">加载示例</el-button></div>
            <el-input v-model="form.pushConfig" type="textarea" :rows="3" class="mono" placeholder='{"onConnect":[{"template":{"msg":"welcome"},"delayMs":500}],"schedule":[{"template":{"msg":"hb"},"cron":"*/30 * * * * *","target":"all"}]}' />
          </div>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存并应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const running = ref({})
const opLoading = reactive({})
const saving = ref(false)
const visible = ref(false)
const form = reactive(emptyForm())

// 帧切分预设（与后端 FrameDecoderFactory 的 schema 对齐）
const framePresets = {
  length_field: '{"type":"length_field","lenBytes":4,"offset":0,"adjustment":0,"initialStrip":4}',
  delimiter: '{"type":"delimiter","value":"\\n"}',
  fixed: '{"type":"fixed","length":64}',
  close_end: '{"type":"close_end"}'
}
const framePresetType = ref('')
const cfgLabel = { frameConfig: '帧切分配置', messageFormatConfig: '格式专属配置', pushConfig: '主动推送配置' }

function applyFramePreset(t) { if (t && framePresets[t]) form.frameConfig = framePresets[t] }
function loadMsgFmtExample() {
  if (form.messageFormat === 'fixed_fields') form.messageFormatConfig = '{"fields":[{"name":"orgNo","len":10},{"name":"amount","len":12}]}'
  else if (form.messageFormat === 'key_value') form.messageFormatConfig = '{"pairSep":"&","kvSep":"="}'
  else { form.messageFormatConfig = ''; ElMessage.info('该报文格式无需专属配置') }
}
function loadPushExample() {
  form.pushConfig = '{"onConnect":[{"template":{"msg":"welcome"},"delayMs":500}],"schedule":[{"template":{"msg":"heartbeat"},"cron":"*/30 * * * * *","target":"all"}]}'
}
function formatCfg(key) {
  const s = form[key]
  if (!s || !s.trim()) return
  try { form[key] = JSON.stringify(JSON.parse(s), null, 2) } catch (e) { ElMessage.error('当前内容不是合法 JSON') }
}
function isJson(s) { if (!s || !s.trim()) return true; try { JSON.parse(s); return true } catch (e) { return false } }

function emptyForm() {
  return { id: null, name: '', port: 9001, connMode: 'LONG', matchMode: 'ASYNC', messageFormat: 'json',
    frameConfig: '{"type":"length_field","lenBytes":4,"endian":"big","offset":0,"adjustment":0,"initialStrip":4}',
    messageFormatConfig: '', routeExtract: '$.transCode', serialExtract: '$.serialNo', pushConfig: '', status: 1 }
}

function frameType(cfg) {
  try { return JSON.parse(cfg || '{}').type || '-' } catch (e) { return '-' }
}

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const res = await api.listeners.list(proj.id)
    list.value = res.data || []
    for (const l of list.value) {
      api.listeners.status(l.id).then((r) => { running.value[l.id] = r.data.running }).catch(() => {})
    }
  } finally {
    loading.value = false
  }
}

function openCreate() { Object.assign(form, emptyForm()); visible.value = true }
function openEdit(row) { Object.assign(form, row); visible.value = true }

async function onSave() {
  if (!form.name || !form.name.trim()) { ElMessage.warning('请输入监听器名称'); return }
  if (!form.port || form.port <= 0) { ElMessage.warning('请输入有效端口（1-65535）'); return }
  for (const f of ['frameConfig', 'messageFormatConfig', 'pushConfig']) {
    if (!isJson(form[f])) { ElMessage.error((cfgLabel[f] || f) + ' 不是合法 JSON，请检查'); return }
  }
  const payload = { ...form }
  saving.value = true
  try {
    if (form.id) await api.listeners.update(form.id, payload)
    else await api.listeners.create(proj.id, payload)
    ElMessage.success('已保存并应用')
    visible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除监听器「${row.name}」？端口将解绑，关联接口一并删除`, '警告', { type: 'warning' })
  await api.listeners.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function start(row) {
  try {
    await ElMessageBox.confirm(`启动监听器「${row.name}」？将绑定端口 ${row.port}。`, '启动确认', { type: 'warning' })
  } catch (e) { return }
  opLoading[row.id] = true
  try {
    await api.listeners.start(row.id)
    ElMessage.success('已启动')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    opLoading[row.id] = false
  }
}

async function stop(row) {
  try {
    await ElMessageBox.confirm(`停止监听器「${row.name}」？端口 ${row.port} 将解绑，已连接的客户端会被断开。`, '停止确认', { type: 'warning' })
  } catch (e) { return }
  opLoading[row.id] = true
  try {
    await api.listeners.stop(row.id)
    ElMessage.success('已停止')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    opLoading[row.id] = false
  }
}

watch(() => proj.id, load)
onMounted(load)
</script>

<style scoped>
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.cfg-block { width: 100%; }
.cfg-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
</style>
