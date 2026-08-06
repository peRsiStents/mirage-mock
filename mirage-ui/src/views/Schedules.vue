<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>定时任务 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <div style="display:flex;align-items:center;gap:12px">
            <el-tooltip content="只列出最近一次失败的任务" placement="top">
              <span style="display:inline-flex;align-items:center;gap:4px;color:#909399;font-size:12px">只看失败<el-switch v-model="onlyFail" /></span>
            </el-tooltip>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建定时</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filtered" v-loading="loading" border stripe size="small" :row-class-name="rowClass">
        <template #empty>
          <el-empty description="暂无定时任务">
            <el-button type="primary" size="small" @click="openCreate">新建定时</el-button>
          </el-empty>
        </template>
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column label="场景" width="140"><template #default="{ row }">{{ scenarioName(row.scenarioId) }}</template></el-table-column>
        <el-table-column prop="cron" label="Cron" width="160" />
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch :model-value="row.enabled === 1" @change="onToggle(row)" /></template></el-table-column>
        <el-table-column label="下次运行" width="170"><template #default="{ row }"><span :class="{ muted: !nextRun[row.id] }">{{ nextRun[row.id] || (row.enabled === 1 ? '计算中…' : '—') }}</span></template></el-table-column>
        <el-table-column prop="lastRunTime" label="最近运行" width="170" />
        <el-table-column label="结果" width="70"><template #default="{ row }"><el-tag v-if="row.lastPassed != null" :type="row.lastPassed === 1 ? 'success' : 'danger'" size="small">{{ row.lastPassed === 1 ? '✓' : '✗' }}</el-tag><span v-else>—</span></template></el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="success" link :loading="runLoading[row.id]" @click="onRun(row)">立即运行</el-button>
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑定时' : '新建定时'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="场景">
          <el-select v-model="form.scenarioId" filterable placeholder="选择场景">
            <el-option v-for="s in scenarios" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron">
          <el-input v-model="form.cron" placeholder="秒 分 时 日 月 周，如 0 */5 * * * ?（每5分钟）" @input="previewCron" />
          <div v-if="cronNexts.length" class="hint" style="margin-top:4px">下次运行：{{ cronNexts.join('、') }}</div>
          <div v-if="cronError" class="err" style="margin-top:4px">{{ cronError }}</div>
          <div class="hint" style="margin-top:4px">Quartz 6 段：秒 分 时 日 月 周。<span class="mono">*</span>=任意，<span class="mono">*/n</span>=每 n 单位，<span class="mono">?</span>=不关心（日和周只能一个用 *，另一个用 ?）。</div>
        </el-form-item>
        <el-form-item label="环境">
          <el-select v-model="form.envId" clearable placeholder="可选">
            <el-option v-for="e in envs" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="onSave">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="resultVisible" title="运行结果" width="860px" top="3vh">
      <div v-if="result">
        <div style="margin-bottom:8px">
          <el-tag :type="result.passed ? 'success' : 'danger'">{{ result.passed ? '✓ 通过' : '✗ 失败' }}</el-tag>
          <span class="muted"> 通过 {{ result.passedSteps }}/{{ result.totalSteps }}，耗时 {{ result.costMs }}ms</span>
        </div>
        <div v-for="(s, i) in result.steps" :key="i" class="step-result">
          <div>
            <el-tag size="small" :type="s.skipped ? 'info' : (s.passed ? 'success' : 'danger')">{{ s.skipped ? '跳过' : (s.passed ? '✓' : '✗') }}</el-tag>
            <b style="margin:0 6px">{{ i + 1 }}. {{ s.caseName }}</b>
            <span class="muted">HTTP {{ s.httpStatus }} · {{ s.costMs }}ms</span>
            <span v-if="s.error" class="err">{{ s.error }}</span>
          </div>
          <el-collapse v-if="!s.skipped">
            <el-collapse-item title="断言 / 提取 / 响应">
              <div v-if="s.assertions && s.assertions.length" class="sub">断言：</div>
              <div v-for="(a, k) in s.assertions" :key="k" class="line">{{ a.passed ? '✓' : '✗' }} {{ a.type }} {{ a.target }} 期望 {{ a.expected }} | 实际 {{ a.actual }}</div>
              <div v-if="s.extracts && Object.keys(s.extracts).length" class="sub">提取：</div>
              <div v-for="(v, key) in s.extracts" :key="key" class="line">{{ key }} = {{ short(v) }}</div>
              <div v-if="s.body" class="sub">响应体：</div>
              <ResponseBody v-if="s.body" :body="s.body" />
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import ResponseBody from '../components/ResponseBody.vue'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const scenarios = ref([])
const envs = ref([])
const formVisible = ref(false)
const form = reactive({ id: null, name: '', scenarioId: null, cron: '0 */5 * * * ?', envId: null, enabled: 1, remark: '' })
const resultVisible = ref(false)
const result = ref(null)
const onlyFail = ref(false)
const nextRun = reactive({})
const filtered = computed(() => (onlyFail.value ? list.value.filter((r) => r.lastPassed === 0) : list.value))
function rowClass({ row }) { return row.lastPassed === 0 ? 'row-fail' : '' }

const cronNexts = ref([])
const cronError = ref('')
const saving = ref(false)
const runLoading = reactive({})
let cronTimer = null
async function previewCron() {
  if (cronTimer) { clearTimeout(cronTimer); cronTimer = null }
  cronNexts.value = []
  const c = (form.cron || '').trim()
  if (!c) { cronError.value = ''; return }
  cronTimer = setTimeout(async () => {
    try {
      const res = await api.schedules.cronPreview(c, 3)
      cronNexts.value = res.data || []
      cronError.value = ''
    } catch (e) {
      cronNexts.value = []
      cronError.value = (e && e.message) ? e.message : 'cron 非法'
    }
  }, 350)
}

function short(v) { const s = String(v); return s.length > 80 ? s.slice(0, 80) + '…' : s }

function scenarioName(id) { const s = scenarios.value.find(x => x.id === id); return s ? s.name : '—' }

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const [s, sc, e] = await Promise.all([api.schedules.list(proj.id), api.scenarios.list(proj.id), api.environments.list(proj.id)])
    list.value = s.data || []; scenarios.value = sc.data || []; envs.value = e.data || []
    refreshNextRuns()
  } finally { loading.value = false }
}

// 列表行展示「下次运行」：对启用的任务批量取 cron 下一次触发时间（任务通常不多）
function refreshNextRuns() {
  for (const r of list.value) {
    if (r.enabled === 1 && r.cron) {
      api.schedules.cronPreview(r.cron, 1)
        .then((res) => { nextRun[r.id] = (res.data && res.data[0]) || '' })
        .catch(() => { nextRun[r.id] = '' })
    } else {
      nextRun[r.id] = ''
    }
  }
}

function openCreate() { Object.assign(form, { id: null, name: '', scenarioId: null, cron: '0 */5 * * * ?', envId: null, enabled: 1, remark: '' }); cronNexts.value = []; cronError.value = ''; previewCron(); formVisible.value = true }
function openEdit(row) { Object.assign(form, { id: row.id, name: row.name, scenarioId: row.scenarioId, cron: row.cron, envId: row.envId, enabled: row.enabled, remark: row.remark || '' }); cronNexts.value = []; cronError.value = ''; previewCron(); formVisible.value = true }

async function onSave() {
  if (!form.name || !form.name.trim()) { ElMessage.warning('请输入名称'); return }
  if (!form.scenarioId) { ElMessage.warning('请选择场景'); return }
  if (!form.cron || !form.cron.trim()) { ElMessage.warning('请输入 Cron 表达式'); return }
  const p = { name: form.name, scenarioId: form.scenarioId, cron: form.cron, envId: form.envId, enabled: form.enabled, remark: form.remark }
  saving.value = true
  try {
    if (form.id) { await api.schedules.update(form.id, p) } else { await api.schedules.create(proj.id, p) }
    ElMessage.success('已保存'); formVisible.value = false; load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function onToggle(row) { await api.schedules.toggle(row.id); load() }
async function onRun(row) {
  runLoading[row.id] = true
  try {
    const r = await api.schedules.run(row.id)
    result.value = r.data
    resultVisible.value = true
    ElMessage.success(r.data.passed ? '✓ 通过' : '✗ 存在失败')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    runLoading[row.id] = false
  }
  load()
}
async function onRemove(row) { await ElMessageBox.confirm(`删除定时「${row.name}」？`, '警告', { type: 'warning' }); await api.schedules.remove(row.id); ElMessage.success('已删除'); load() }

watch(() => proj.id, load)
onMounted(load)
</script>
<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #909399; font-size: 12px; }
.err { color: #f56c6c; margin-left: 8px; font-size: 12px; }
.step-result { border-bottom: 1px solid #f0f0f0; padding: 8px 0; }
.line { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; margin: 2px 0; }
.sub { font-size: 12px; color: #606266; margin-top: 6px; }
</style>
<style>
.el-table .row-fail { background: #fef0f0 !important; }
</style>
