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
      <el-table :data="list" v-loading="loading" border stripe>
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
            <el-button v-if="!running[row.id]" size="small" type="success" @click="start(row)">启动</el-button>
            <el-button v-else size="small" type="warning" @click="stop(row)">停止</el-button>
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
            <el-option v-for="f in ['json','key_value','fixed_fields','hex_string']" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="帧切分配置">
          <el-input v-model="form.frameConfig" type="textarea" :rows="2" class="mono" placeholder='{"type":"length_field","lenBytes":4,"endian":"big","initialStrip":4}' />
        </el-form-item>
        <el-form-item label="格式专属配置">
          <el-input v-model="form.messageFormatConfig" type="textarea" :rows="2" class="mono" placeholder='fixed_fields: {"fields":[{"name":"orgNo","len":10}]}  /  key_value: {"pairSep":"&","kvSep":"="}' />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="路由提取"><el-input v-model="form.routeExtract" placeholder="$.transCode / field:orgNo / kv:type" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="流水号提取"><el-input v-model="form.serialExtract" placeholder="$.serialNo" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="主动推送配置">
          <el-input v-model="form.pushConfig" type="textarea" :rows="3" class="mono" placeholder='{"onConnect":[{"template":{"msg":"welcome"},"delayMs":500}],"schedule":[{"template":{"msg":"hb"},"cron":"*/30 * * * * *","target":"all"}]}' />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存并应用</el-button>
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
const visible = ref(false)
const form = reactive(emptyForm())

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
  const payload = { ...form }
  if (form.id) await api.listeners.update(form.id, payload)
  else await api.listeners.create(proj.id, payload)
  ElMessage.success('已保存并应用')
  visible.value = false
  load()
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除监听器「${row.name}」？端口将解绑，关联接口一并删除`, '警告', { type: 'warning' })
  await api.listeners.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function start(row) { await api.listeners.start(row.id); ElMessage.success('已启动'); load() }
async function stop(row) { await api.listeners.stop(row.id); ElMessage.success('已停止'); load() }

watch(() => proj.id, load)
onMounted(load)
</script>

<style scoped>
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
</style>
