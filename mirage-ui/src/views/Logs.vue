<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header"><span>请求日志 <el-tag size="small">{{ proj.name }}</el-tag></span></div>
      </template>
      <div class="filters">
        <el-select v-model="filters.interfaceId" placeholder="接口" style="width: 200px" clearable filterable>
          <el-option v-for="it in interfaces" :key="it.id" :label="it.name" :value="it.id" />
        </el-select>
        <el-select v-model="filters.matched" placeholder="命中" style="width: 110px" clearable>
          <el-option label="命中" :value="1" /><el-option label="未命中" :value="0" />
        </el-select>
        <el-date-picker v-model="filters.from" type="datetime" placeholder="开始时间" value-format="x" format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
        <el-date-picker v-model="filters.to" type="datetime" placeholder="结束时间" value-format="x" format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-table :data="logs" v-loading="loading" border size="small" :row-class-name="rowClass" style="margin-top: 12px">
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="protocol" label="协议" width="70" />
        <el-table-column prop="clientAddr" label="来源" width="140" />
        <el-table-column label="命中" width="80">
          <template #default="{ row }">
            <el-tag :type="row.matched === 1 ? 'success' : 'danger'" size="small">{{ row.matched === 1 ? '命中' : '未命中' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="项目" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '—' }}</template>
        </el-table-column>
        <el-table-column label="接口" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.interfaceName || (row.interfaceId ? '#' + row.interfaceId : '—') }}</template>
        </el-table-column>
        <el-table-column label="规则" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ruleName || (row.ruleId ? '#' + row.ruleId : '—') }}</template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时ms" width="90" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button size="small" link @click="showDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load" @size-change="load"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="请求详情" width="780px">
      <el-tabs>
        <el-tab-pane label="请求原文"><pre class="raw">{{ detail.requestRaw }}</pre></el-tab-pane>
        <el-tab-pane label="解析字段"><pre class="raw">{{ pretty(detail.requestParsed) }}</pre></el-tab-pane>
        <el-tab-pane label="响应原文"><pre class="raw">{{ detail.responseRaw }}</pre></el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'

const proj = useProjectStore()
const logs = ref([])
const loading = ref(false)
const filters = reactive({ interfaceId: '', matched: null, from: null, to: null })
const page = reactive({ current: 1, size: 20, total: 0 })

const detailVisible = ref(false)
const detail = ref({})

const interfaces = ref([])
async function loadInterfaces() {
  if (!proj.id) return
  try { const res = await api.interfaces.list(proj.id); interfaces.value = res.data || [] } catch (e) { interfaces.value = [] }
}

function rowClass({ row }) {
  return row.matched === 1 ? '' : 'row-unmatched'
}

function pretty(text) {
  if (!text) return ''
  try { return JSON.stringify(JSON.parse(text), null, 2) } catch (e) { return text }
}

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const params = {
      page: page.current,
      size: page.size
    }
    if (filters.interfaceId) params.interfaceId = filters.interfaceId
    if (filters.matched !== null && filters.matched !== '') params.matched = filters.matched
    if (filters.from) params.from = Number(filters.from)
    if (filters.to) params.to = Number(filters.to)
    const res = await api.logs.query(proj.id, params)
    logs.value = res.data.list || []
    page.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function showDetail(row) {
  detail.value = row
  detailVisible.value = true
}

watch(() => proj.id, () => { load(); loadInterfaces() })
onMounted(() => { load(); loadInterfaces() })
</script>

<style scoped>
.filters { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.raw {
  background: #f5f7fa; padding: 12px; border-radius: 4px; max-height: 420px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all;
}
</style>
<style>
.el-table .row-unmatched { background: #fef0f0 !important; }
</style>
