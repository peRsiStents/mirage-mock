<template>
  <div class="page">
    <el-card v-if="!proj.id"><el-empty description="请先在顶部选择一个项目" /></el-card>
    <el-card v-else>
      <template #header>
        <div class="card-header"><span>测试报告 <el-tag size="small">{{ proj.name }}</el-tag></span></div>
      </template>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.targetType === 'scenario' ? '场景' : '用例' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }"><el-tag :type="row.passed === 1 ? 'success' : 'danger'" size="small">{{ row.passed === 1 ? '通过' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="通过/总" width="100">
          <template #default="{ row }">{{ row.passedSteps }}/{{ row.totalSteps }}</template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button size="small" type="primary" link @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px; justify-content:flex-end"
        v-model:current-page="page.current" v-model:page-size="page.size" :total="page.total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        @current-change="load" @size-change="load" />
    </el-card>

    <el-dialog v-model="detailVisible" title="报告详情" width="860px" top="3vh">
      <div v-if="detail">
        <div style="margin-bottom:8px">
          <el-tag :type="detail.passed === 1 ? 'success' : 'danger'">{{ detail.passed === 1 ? '✓ 通过' : '✗ 失败' }}</el-tag>
          <span class="muted"> 通过 {{ detail.passedSteps }}/{{ detail.totalSteps }}，耗时 {{ detail.costMs }}ms · {{ detail.createTime }}</span>
        </div>
        <div v-for="(s, i) in steps(detail)" :key="i" class="step-result">
          <div>
            <el-tag size="small" :type="s.skipped ? 'info' : (s.passed ? 'success' : 'danger')">{{ s.skipped ? '跳过' : (s.passed ? '✓' : '✗') }}</el-tag>
            <b style="margin:0 6px">{{ i + 1 }}. {{ s.caseName }}</b>
            <span class="muted">HTTP {{ s.httpStatus }} · {{ s.costMs }}ms</span>
            <span v-if="s.error" class="err">{{ s.error }}</span>
          </div>
          <div v-if="s.assertions && s.assertions.length" class="sub">断言</div>
          <div v-for="(a, k) in s.assertions" :key="k" class="line">{{ a.passed ? '✓' : '✗' }} {{ a.type }} {{ a.target }} 期望 {{ a.expected }} | 实际 {{ a.actual }}</div>
          <div v-if="s.extracts && Object.keys(s.extracts).length" class="sub">提取</div>
          <div v-for="(v, key) in s.extracts" :key="key" class="line">{{ key }} = {{ short(v) }}</div>
          <div v-if="s.body" class="sub">响应体</div>
          <pre v-if="s.body" class="resp">{{ s.body }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { api } from '../api'
import { useProjectStore } from '../store/project'

const proj = useProjectStore()
const list = ref([])
const loading = ref(false)
const page = reactive({ current: 1, size: 20, total: 0 })
const detailVisible = ref(false)
const detail = ref(null)

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const res = await api.records.query(proj.id, { page: page.current, size: page.size })
    list.value = res.data.list || []
    page.total = res.data.total || 0
  } finally { loading.value = false }
}

async function openDetail(row) {
  const res = await api.records.get(row.id)
  detail.value = res.data
  detailVisible.value = true
}

function steps(rec) { try { return JSON.parse(rec.detail || '[]') } catch (e) { return [] } }
function short(v) { const s = String(v); return s.length > 80 ? s.slice(0, 80) + '…' : s }

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
.resp { background: #f5f7fa; padding: 8px; border-radius: 4px; max-height: 200px; overflow: auto;
  font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
</style>
