<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header"><span>测试报告 <el-tag size="small">{{ proj.name }}</el-tag></span></div>
      </template>
      <div class="filters">
        <el-select v-model="filters.type" placeholder="类型" clearable style="width:120px" @change="resetAndLoad">
          <el-option label="场景" value="scenario" /><el-option label="用例" value="case" />
        </el-select>
        <el-select v-model="filters.passed" placeholder="结果" clearable style="width:120px" @change="resetAndLoad">
          <el-option label="通过" :value="1" /><el-option label="失败" :value="0" />
        </el-select>
        <el-date-picker v-model="filters.from" type="datetime" placeholder="开始时间" value-format="x" format="YYYY-MM-DD HH:mm" style="width:200px" />
        <el-date-picker v-model="filters.to" type="datetime" placeholder="结束时间" value-format="x" format="YYYY-MM-DD HH:mm" style="width:200px" />
        <el-button type="primary" @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border stripe :row-class-name="rowClass">
        <el-table-column prop="createTime" label="时间" width="170" sortable />
        <el-table-column label="名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetName || (row.targetId ? '#' + row.targetId : '—') }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.targetType === 'scenario' ? '场景' : '用例' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80" prop="passed" sortable>
          <template #default="{ row }"><el-tag :type="row.passed === 1 ? 'success' : 'danger'" size="small">{{ row.passed === 1 ? '通过' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="通过/总" width="100">
          <template #default="{ row }">{{ row.passedSteps }}/{{ row.totalSteps }}</template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" sortable />
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button size="small" type="primary" link @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px; justify-content:flex-end"
        v-model:current-page="page.current" v-model:page-size="page.size" :total="page.total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        @current-change="load" @size-change="load" />
    </el-card>

    <el-dialog v-model="detailVisible" title="报告详情" width="880px" top="3vh">
      <div v-if="detail">
        <div class="detail-bar">
          <div>
            <el-tag :type="detail.passed === 1 ? 'success' : 'danger'">{{ detail.passed === 1 ? '✓ 通过' : '✗ 失败' }}</el-tag>
            <span class="muted"> 通过 {{ detail.passedSteps }}/{{ detail.totalSteps }}，耗时 {{ detail.costMs }}ms · {{ detail.createTime }}</span>
          </div>
          <div class="detail-actions">
            <el-button size="small" @click="copyShare">复制分享链接</el-button>
            <el-button size="small" @click="exportJson">导出 JSON</el-button>
            <el-button size="small" @click="exportHtml">导出 HTML</el-button>
          </div>
        </div>
        <div class="step-ctrl">
          <el-radio-group v-model="stepView" size="small" @change="applyStepView">
            <el-radio-button label="fail">仅看失败</el-radio-button>
            <el-radio-button label="all">展开全部</el-radio-button>
            <el-radio-button label="none">全部收起</el-radio-button>
          </el-radio-group>
        </div>
        <el-collapse v-model="openSteps">
          <template v-for="(s, i) in st" :key="i">
            <el-collapse-item v-if="stepView !== 'fail' || isFail(s)" :name="i">
              <template #title>
                <el-tag size="small" :type="s.skipped ? 'info' : (s.passed ? 'success' : 'danger')">{{ s.skipped ? '跳过' : (s.passed ? '✓' : '✗') }}</el-tag>
                <b style="margin:0 6px">{{ i + 1 }}. {{ s.caseName }}</b>
                <span class="muted">HTTP {{ s.httpStatus }} · {{ s.costMs }}ms</span>
                <span v-if="s.error" class="err">{{ s.error }}</span>
              </template>
              <div v-if="s.assertions && s.assertions.length" class="sub">断言</div>
              <div v-for="(a, k) in s.assertions" :key="k" class="line">{{ a.passed ? '✓' : '✗' }} {{ a.type }} {{ a.target }} 期望 {{ a.expected }} | 实际 {{ a.actual }}</div>
              <div v-if="s.extracts && Object.keys(s.extracts).length" class="sub">提取</div>
              <div v-for="(v, key) in s.extracts" :key="key" class="line">{{ key }} = {{ short(v) }}</div>
              <div v-if="s.body" class="sub">响应体</div>
              <ResponseBody v-if="s.body" :body="s.body" />
            </el-collapse-item>
          </template>
        </el-collapse>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import { copyText } from '../utils/clipboard'
import ResponseBody from '../components/ResponseBody.vue'

const route = useRoute()
const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const page = reactive({ current: 1, size: 20, total: 0 })
const filters = reactive({ type: '', passed: null, from: null, to: null })
const detailVisible = ref(false)
const detail = ref(null)

const stepView = ref('fail')
const openSteps = ref([])

function rowClass({ row }) { return row.passed === 1 ? '' : 'row-fail' }
function isFail(s) { return !s.skipped && !s.passed }
function resetAndLoad() { page.current = 1; load() }
function resetFilters() { Object.assign(filters, { type: '', passed: null, from: null, to: null }); page.current = 1; load() }

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const params = { page: page.current, size: page.size }
    if (filters.type) params.type = filters.type
    if (filters.passed !== null && filters.passed !== '') params.passed = filters.passed
    if (filters.from) params.from = Number(filters.from)
    if (filters.to) params.to = Number(filters.to)
    const res = await api.records.query(proj.id, params)
    list.value = res.data.list || []
    page.total = res.data.total || 0
  } finally { loading.value = false }
}

const st = computed(() => { try { return JSON.parse(detail.value?.detail || '[]') } catch (e) { return [] } })

function applyStepView() {
  const arr = st.value
  if (stepView.value === 'all') openSteps.value = arr.map((_, i) => i)
  else if (stepView.value === 'fail') openSteps.value = arr.map((s, i) => (isFail(s) ? i : -1)).filter((i) => i >= 0)
  else openSteps.value = []
}

async function openDetail(row) {
  const res = await api.records.get(row.id)
  detail.value = res.data
  detailVisible.value = true
  stepView.value = 'fail'
  await nextTick()
  applyStepView()
}

function short(v) { const s = String(v); return s.length > 80 ? s.slice(0, 80) + '…' : s }

function download(name, content, mime) {
  const blob = new Blob([content], { type: mime || 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = name
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
function exportJson() {
  if (!detail.value) return
  download('report-' + detail.value.id + '.json', JSON.stringify(detail.value, null, 2), 'application/json')
  ElMessage.success('已导出 JSON')
}
function exportHtml() {
  if (!detail.value) return
  const d = detail.value
  const rows = st.value.map((s, i) => `
    <div class="step ${s.passed ? 'ok' : 'fail'}">
      <div class="head"><span class="tag ${s.skipped ? 'skip' : (s.passed ? 'ok' : 'fail')}">${s.skipped ? 'SKIP' : (s.passed ? 'PASS' : 'FAIL')}</span> ${i + 1}. ${esc(s.caseName)} <span class="muted">HTTP ${s.httpStatus || '-'} · ${s.costMs}ms</span></div>
      ${s.error ? `<div class="err">${esc(s.error)}</div>` : ''}
      ${s.assertions && s.assertions.length ? '<div class="sub">断言</div>' + s.assertions.map((a) => `<div class="line">${a.passed ? '✓' : '✗'} ${esc(a.type)} ${esc(a.target || '')} 期望 ${esc(String(a.expected))} | 实际 ${esc(String(a.actual))}</div>`).join('') : ''}
      ${s.body ? `<div class="sub">响应体</div><pre>${esc(typeof s.body === 'string' ? s.body : JSON.stringify(s.body, null, 2))}</pre>` : ''}
    </div>`).join('')
  const html = `<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>测试报告 ${esc(String(d.id))}</title>
<style>body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:920px;margin:24px auto;color:#303133;padding:0 16px}
h1{font-size:18px}.meta{color:#909399;font-size:13px;margin-bottom:16px}.tag{display:inline-block;padding:2px 8px;border-radius:3px;color:#fff;font-size:12px}
.ok{background:#67c23a}.fail{background:#f56c6c}.skip{background:#909399}.step{border:1px solid #ebeef5;border-radius:6px;padding:10px;margin-bottom:10px}
.step.fail{border-color:#fbc4c4}.head{font-weight:600}.sub{font-size:12px;color:#606266;margin-top:6px}.line{font-family:Consolas,monospace;font-size:12px}
pre{background:#f5f7fa;padding:8px;border-radius:4px;overflow:auto;font-size:12px;white-space:pre-wrap;word-break:break-all}.muted{color:#909399;font-weight:normal;font-size:12px}.err{color:#f56c6c;font-size:12px}</style></head>
<body><h1>测试报告 · ${d.passed === 1 ? '<span class="tag ok">通过</span>' : '<span class="tag fail">失败</span>'}</h1>
<div class="meta">通过 ${d.passedSteps}/${d.totalSteps}，耗时 ${d.costMs}ms · ${esc(d.createTime || '')} · 记录ID ${esc(String(d.id))}</div>
${rows || '<div class="muted">无步骤明细</div>'}</body></html>`
  download('report-' + d.id + '.html', html, 'text/html;charset=utf-8')
  ElMessage.success('已导出 HTML')
}
function esc(s) { return String(s == null ? '' : s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c])) }

async function copyShare() {
  if (!detail.value) return
  const url = window.location.origin + window.location.pathname + '#/reports?recordId=' + detail.value.id
  await copyText(url)
  ElMessage.success('分享链接已复制：' + url)
}

watch(() => proj.id, load)
onMounted(async () => {
  load()
  const rid = route.query.recordId
  if (rid) {
    try {
      const res = await api.records.get(rid)
      detail.value = res.data
      detailVisible.value = true
      stepView.value = 'fail'
      await nextTick()
      applyStepView()
    } catch (e) { /* 拦截器已提示 */ }
  }
})
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.filters { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; }
.err { color: #f56c6c; margin-left: 8px; font-size: 12px; }
.line { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; margin: 2px 0; }
.sub { font-size: 12px; color: #606266; margin-top: 6px; }
.detail-bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.detail-actions { display: flex; gap: 6px; }
.step-ctrl { margin-bottom: 8px; }
</style>
<style>
.el-table .row-fail { background: #fef0f0 !important; }
</style>
