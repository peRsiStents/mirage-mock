<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>测试场景 <el-tag size="small">{{ proj.name }}</el-tag></span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建场景</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="名称" width="180" />
        <el-table-column label="默认环境" width="140">
          <template #default="{ row }">{{ envName(row.envId) }}</template>
        </el-table-column>
        <el-table-column label="失败策略" width="100">
          <template #default="{ row }">{{ row.onFail === 'CONTINUE' ? '失败继续' : '失败即停' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openSteps(row)">步骤</el-button>
            <el-button size="small" type="success" link @click="onRun(row)">运行</el-button>
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 场景基本信息 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑场景' : '新建场景'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="默认环境">
          <el-select v-model="form.envId" clearable placeholder="可选">
            <el-option v-for="e in envs" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="失败策略">
          <el-radio-group v-model="form.onFail">
            <el-radio label="STOP">失败即停</el-radio>
            <el-radio label="CONTINUE">失败继续</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="form.id" type="primary" @click="formVisible = false; openStepsById(form.id)">下一步：编排步骤</el-button>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">{{ form.id ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 步骤编排 -->
    <el-dialog v-model="stepsVisible" :title="'步骤编排：' + (curScenario?.name || '')" width="940px" top="3vh">
      <el-alert type="info" :closable="false" style="margin-bottom:10px"
        title="步骤按顺序运行；提取器把上一步响应字段存为 ${var.x}，后续步骤可用。失败即停时可单步勾'失败继续'。" />
      <div v-for="(st, i) in steps" :key="i" class="step-card">
        <div class="step-head">
          <el-tag size="small">{{ i + 1 }}</el-tag>
          <el-select v-model="st.caseId" placeholder="选择测试用例" filterable style="width:240px; margin-left:6px">
            <el-option v-for="c in cases" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-input v-model="st.name" placeholder="步骤名(可选)" style="width:160px; margin-left:6px" />
          <span class="grow"></span>
          <el-checkbox v-model="st.continueOnFail">失败继续</el-checkbox>
          <el-checkbox v-model="st.enabled">启用</el-checkbox>
          <el-button :icon="Top" circle size="small" :disabled="i === 0" @click="move(i, -1)" />
          <el-button :icon="Bottom" circle size="small" :disabled="i === steps.length - 1" @click="move(i, 1)" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="steps.splice(i, 1)" />
        </div>
        <div class="ext-title">提取器（source: status/header(头名)/body/jsonPath($.x) → 变量名）</div>
        <div v-for="(ex, j) in st.extract" :key="j" class="kv-row">
          <el-input v-model="ex.var" placeholder="变量名" style="width:130px" />
          <el-select v-model="ex.source" style="width:110px">
            <el-option label="jsonPath" value="jsonPath" /><el-option label="header" value="header" />
            <el-option label="status" value="status" /><el-option label="body" value="body" />
          </el-select>
          <el-input v-model="ex.expr" :placeholder="ex.source === 'jsonPath' ? '$.data.token' : (ex.source === 'header' ? 'Content-Type' : '(无需)')" style="width:240px" />
          <el-button :icon="Delete" circle size="small" type="danger" @click="st.extract.splice(j, 1)" />
        </div>
        <el-button size="small" :icon="Plus" @click="st.extract.push({ var: '', source: 'jsonPath', expr: '' })">加提取器</el-button>
      </div>
      <el-button size="small" type="primary" :icon="Plus" style="margin-top:8px" @click="steps.push({ caseId: null, name: '', extract: [], continueOnFail: false, enabled: true })">添加步骤</el-button>
      <template #footer>
        <el-button @click="stepsVisible = false">关闭</el-button>
        <el-button type="primary" @click="saveSteps">保存步骤</el-button>
      </template>
    </el-dialog>

    <!-- 运行结果 -->
    <el-dialog v-model="resultVisible" title="场景运行结果" width="880px" top="3vh">
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
              <pre v-if="s.body" class="resp">{{ s.body }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Top, Bottom } from '@element-plus/icons-vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const envs = ref([])
const cases = ref([])

const formVisible = ref(false)
const form = reactive({ id: null, name: '', envId: null, onFail: 'STOP', remark: '' })

const stepsVisible = ref(false)
const curScenario = ref(null)
const steps = ref([])

const resultVisible = ref(false)
const result = ref(null)

function envName(id) { const e = envs.value.find((x) => x.id === id); return e ? e.name : '—' }

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const [s, e, c] = await Promise.all([api.scenarios.list(proj.id), api.environments.list(proj.id), api.testCases.list(proj.id)])
    list.value = s.data || []
    envs.value = e.data || []
    cases.value = c.data || []
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', envId: null, onFail: 'STOP', remark: '' })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, name: row.name, envId: row.envId, onFail: row.onFail || 'STOP', remark: row.remark || '' })
  formVisible.value = true
}

async function onSave() {
  const payload = { name: form.name, envId: form.envId, onFail: form.onFail, remark: form.remark }
  if (form.id) { await api.scenarios.update(form.id, payload) } else { const r = await api.scenarios.create(proj.id, payload); form.id = r.data.id }
  ElMessage.success('已保存'); formVisible.value = false; load()
}

async function openSteps(row) {
  curScenario.value = row
  const res = await api.scenarios.steps(row.id)
  steps.value = (res.data || []).map((s) => ({
    caseId: s.caseId, name: s.name || '', extract: parseArr(s.extract),
    continueOnFail: s.continueOnFail === 1, enabled: s.enabled !== 0
  }))
  stepsVisible.value = true
}
function openStepsById(id) { const row = list.value.find((x) => x.id === id); if (row) openSteps(row) }

function move(i, delta) {
  const j = i + delta; const arr = steps.value
  const tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
}

async function saveSteps() {
  if (!curScenario.value) return
  const payload = steps.value.map((s, i) => ({
    seq: i + 1, caseId: s.caseId, name: s.name,
    extract: JSON.stringify(s.extract || []),
    continueOnFail: s.continueOnFail ? 1 : 0, enabled: s.enabled ? 1 : 0
  }))
  await api.scenarios.saveSteps(curScenario.value.id, payload)
  ElMessage.success('步骤已保存')
}

async function onRun(row) {
  try {
    const res = await api.scenarios.run(row.id, row.envId)
    result.value = res.data
    resultVisible.value = true
  } catch (e) {
    ElMessage.error('运行失败：' + (e?.response?.data?.message || e.message || ''))
  }
}

async function onRemove(row) {
  await ElMessageBox.confirm(`删除场景「${row.name}」及其步骤？`, '警告', { type: 'warning' })
  await api.scenarios.remove(row.id); ElMessage.success('已删除'); load()
}

function parseArr(s) { try { return JSON.parse(s || '[]') } catch (e) { return [] } }
function short(v) { const s = String(v); return s.length > 80 ? s.slice(0, 80) + '…' : s }

watch(() => proj.id, load)
onMounted(load)
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #909399; font-size: 12px; }
.err { color: #f56c6c; margin-left: 8px; font-size: 12px; }
.step-card { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; margin-bottom: 10px; }
.step-head { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.grow { flex: 1; }
.ext-title { font-size: 12px; color: #909399; margin: 4px 0; }
.kv-row { display: flex; gap: 6px; align-items: center; margin-bottom: 6px; }
.step-result { border-bottom: 1px solid #f0f0f0; padding: 8px 0; }
.line { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; margin: 2px 0; }
.sub { font-size: 12px; color: #606266; margin-top: 6px; }
.resp { background: #f5f7fa; padding: 8px; border-radius: 4px; max-height: 200px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
</style>
