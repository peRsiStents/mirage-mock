<template>
  <div class="page">
    <el-card v-if="!proj.id">
      <el-empty description="请先在顶部选择一个项目，或前往项目管理新建">
        <el-button type="primary" @click="$router.push('/projects')">去项目管理</el-button>
      </el-empty>
    </el-card>

    <div v-else v-loading="loading">
      <!-- 资源概览 -->
      <div class="stat-grid">
        <div class="stat-card" v-for="s in stats" :key="s.key" @click="go(s.route)">
          <div class="stat-icon" :style="{ background: s.color }"><el-icon><component :is="s.icon" /></el-icon></div>
          <div class="stat-body">
            <div class="stat-num">{{ s.value }}</div>
            <div class="stat-label">{{ s.label }}</div>
          </div>
        </div>
      </div>

      <el-row :gutter="12" style="margin-top:12px">
        <!-- Mock 流量 + 通过率 -->
        <el-col :span="14">
          <el-card shadow="never">
            <template #header><span>Mock 流量 & 测试质量</span></template>
            <el-row :gutter="16">
              <el-col :span="6">
                <div class="metric">
                  <div class="metric-num">{{ data.logTotal || 0 }}</div>
                  <div class="metric-label">累计请求</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="metric">
                  <div class="metric-num">{{ data.logToday || 0 }}</div>
                  <div class="metric-label">今日请求</div>
                  <div class="metric-sub" :class="{ 'num-danger': (data.matchedRateToday != null) && data.matchedRateToday < 100 && data.logToday > 0 }">命中 {{ data.matchedRateToday ?? 0 }}%</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="metric">
                  <div class="metric-num" :class="passRateClass">{{ data.runPassRate != null ? data.runPassRate + '%' : '—' }}</div>
                  <div class="metric-label">测试通过率（{{ data.runPassed || 0 }}/{{ data.runTotal || 0 }}）</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="metric">
                  <div class="metric-num" :class="{ 'num-danger': (data.scheduleFailCount || 0) > 0 }">{{ data.scheduleFailCount || 0 }}</div>
                  <div class="metric-label">失败定时任务</div>
                </div>
              </el-col>
            </el-row>
            <el-progress
              v-if="data.runTotal"
              :percentage="Number(data.runPassRate || 0)"
              :color="passRateColor"
              :stroke-width="10"
              style="margin-top:14px"
            />
          </el-card>
        </el-col>

        <!-- 最近测试运行 -->
        <el-col :span="10">
          <el-card shadow="never">
            <template #header><span>最近测试运行</span></template>
            <el-table :data="data.recentRuns || []" size="small" :show-header="false">
              <template #empty><div class="muted-sm">暂无运行记录</div></template>
              <el-table-column width="36">
                <template #default="{ row }">
                  <span :class="row.passed === 1 ? 'dot-ok' : 'dot-fail'">●</span>
                </template>
              </el-table-column>
              <el-table-column>
                <template #default="{ row }">
                  <span class="mono">HTTP {{ row.httpStatus || '-' }}</span>
                  <span class="muted-sm" style="margin-left:8px">{{ relTime(row.createTime) }}</span>
                  <div v-if="row.error" class="err-line">{{ row.error }}</div>
                </template>
              </el-table-column>
              <el-table-column width="70" align="right">
                <template #default="{ row }"><span class="muted-sm">{{ row.costMs }}ms</span></template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" style="margin-top:12px">
        <!-- 近 7 天趋势 -->
        <el-col :span="15">
          <el-card shadow="never">
            <template #header><span>近 7 天趋势</span></template>
            <div class="trend-row">
              <div class="trend-item">
                <div class="trend-label">请求量（今日 {{ reqToday }}）</div>
                <svg class="spark" viewBox="0 0 200 44" preserveAspectRatio="none">
                  <polyline :points="linePoints(reqSeries, 200, 44)" fill="none" stroke="#409eff" stroke-width="2" />
                </svg>
              </div>
              <div class="trend-item">
                <div class="trend-label">命中率（今日 {{ data.matchedRateToday ?? 0 }}%）</div>
                <svg class="spark" viewBox="0 0 200 44" preserveAspectRatio="none">
                  <polyline :points="linePoints(hitSeries, 200, 44)" fill="none" stroke="#67c23a" stroke-width="2" />
                </svg>
              </div>
              <div class="trend-item">
                <div class="trend-label">测试通过率（{{ passToday != null ? passToday + '%' : '今日无运行' }}）</div>
                <svg class="spark" viewBox="0 0 200 44" preserveAspectRatio="none">
                  <polyline :points="linePoints(passSeries, 200, 44)" fill="none" stroke="#e6a23c" stroke-width="2" />
                </svg>
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 近 7 天失败最多 -->
        <el-col :span="9">
          <el-card shadow="never">
            <template #header><span>近 7 天失败最多</span></template>
            <el-table :data="data.topFailCases || []" size="small" :show-header="false">
              <template #empty><div class="muted-sm">近 7 天无失败用例 🎉</div></template>
              <el-table-column>
                <template #default="{ row }"><span class="mono">{{ row.caseName }}</span></template>
              </el-table-column>
              <el-table-column width="70" align="right">
                <template #default="{ row }"><el-tag size="small" type="danger">{{ row.failCount }}次</el-tag></template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" style="margin-top:12px">
        <!-- 最近 Mock 请求 -->
        <el-col :span="24">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>最近 Mock 请求</span>
                <el-button size="small" link type="primary" @click="$router.push('/logs')">查看全部</el-button>
              </div>
            </template>
            <el-table :data="data.recentLogs || []" size="small">
              <template #empty><div class="muted-sm">暂无请求，发起一次请求或运行一次用例即可产生日志</div></template>
              <el-table-column label="协议" width="80">
                <template #default="{ row }"><el-tag size="small" :type="row.protocol === 'TCP' ? 'warning' : 'primary'">{{ row.protocol || 'HTTP' }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="clientAddr" label="来源" width="160" />
              <el-table-column label="命中" width="70">
                <template #default="{ row }"><el-tag size="small" :type="row.matched === 1 ? 'success' : 'info'">{{ row.matched === 1 ? '命中' : '未命中' }}</el-tag></template>
              </el-table-column>
              <el-table-column label="耗时" width="80"><template #default="{ row }">{{ row.costMs }}ms</template></el-table-column>
              <el-table-column label="时间"><template #default="{ row }">{{ fmt(row.createTime) }}</template></el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useProjectStore } from '../store/project'
import { Connection, Document, Key, Promotion, Share, AlarmClock, DataAnalysis } from '@element-plus/icons-vue'

const router = useRouter()
const proj = useProjectStore()
const loading = ref(false)
const data = ref({})
const trend = ref([])

// 趋势序列（请求量 / 命中率 / 通过率）
const reqSeries = computed(() => trend.value.map((t) => t.requests))
const hitSeries = computed(() => trend.value.map((t) => t.hitRate))
const passSeries = computed(() => trend.value.map((t) => (t.runPassRate == null ? 0 : t.runPassRate)))
const reqToday = computed(() => { const a = reqSeries.value; return a.length ? a[a.length - 1] : 0 })
const passToday = computed(() => {
  const a = trend.value
  if (!a.length) return null
  const last = a[a.length - 1].runPassRate
  return last == null ? null : last
})

/** 数组 → SVG polyline points（自适应 min/max 映射到 w×h） */
function linePoints(arr, w, h) {
  if (!arr.length) return ''
  const max = Math.max(...arr, 1)
  const min = Math.min(...arr, 0)
  const range = max - min || 1
  const step = arr.length > 1 ? w / (arr.length - 1) : 0
  return arr
    .map((v, i) => (i * step).toFixed(1) + ',' + (h - ((v - min) / range) * h).toFixed(1))
    .join(' ')
}

const stats = computed(() => [
  { key: 'iface', label: 'HTTP 接口', value: data.value.interfaceCount || 0, icon: Connection, color: '#409eff', route: '/interfaces' },
  { key: 'rule', label: 'Mock 规则', value: data.value.ruleCount || 0, icon: Document, color: '#67c23a', route: '/interfaces' },
  { key: 'listener', label: 'TCP 监听', value: data.value.listenerCount || 0, icon: Connection, color: '#e6a23c', route: '/listeners' },
  { key: 'case', label: '测试用例', value: data.value.caseCount || 0, icon: Promotion, color: '#909399', route: '/testcases' },
  { key: 'scenario', label: '测试场景', value: data.value.scenarioCount || 0, icon: Share, color: '#9254de', route: '/scenarios' },
  { key: 'schedule', label: '定时任务', value: data.value.scheduleCount || 0, icon: AlarmClock, color: '#f56c6c', route: '/schedules' }
])

const passRateClass = computed(() => {
  const r = Number(data.value.runPassRate || 0)
  if (!data.value.runTotal) return ''
  return r >= 80 ? 'num-ok' : (r >= 50 ? 'num-warn' : 'num-danger')
})
const passRateColor = computed(() => {
  const r = Number(data.value.runPassRate || 0)
  return r >= 80 ? '#67c23a' : (r >= 50 ? '#e6a23c' : '#f56c6c')
})

function go(route) { router.push(route) }

async function load() {
  if (!proj.id) return
  loading.value = true
  try {
    const res = await api.dashboard.overview(proj.id)
    data.value = res.data || {}
    loadTrend()
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  if (!proj.id) return
  try {
    const res = await api.dashboard.trend(proj.id, 7)
    trend.value = res.data || []
  } catch (e) {
    trend.value = []
  }
}

function pad(n) { return n < 10 ? '0' + n : '' + n }
function fmt(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (isNaN(d.getTime())) return String(t)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function relTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (isNaN(d.getTime())) return String(t)
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  return Math.floor(diff / 86400) + ' 天前'
}

watch(() => proj.id, load)
onMounted(load)
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 14px;
  cursor: pointer;
  transition: box-shadow .2s, transform .2s;
}
.stat-card:hover {
  box-shadow: 0 4px 14px rgba(0,0,0,.08);
  transform: translateY(-2px);
}
.stat-icon {
  width: 42px; height: 42px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 20px; flex-shrink: 0;
}
.stat-num { font-size: 24px; font-weight: 700; color: #303133; line-height: 1.2; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
.metric { text-align: center; }
.metric-num { font-size: 26px; font-weight: 700; color: #303133; }
.metric-label { font-size: 12px; color: #909399; margin-top: 4px; }
.metric-sub { font-size: 12px; color: #67c23a; margin-top: 2px; }
.trend-row { display: flex; gap: 18px; }
.trend-item { flex: 1; }
.trend-label { font-size: 12px; color: #606266; margin-bottom: 6px; }
.spark { width: 100%; height: 44px; display: block; }
.num-ok { color: #67c23a; }
.num-warn { color: #e6a23c; }
.num-danger { color: #f56c6c; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.muted-sm { color: #909399; font-size: 12px; }
.mono { font-family: 'JetBrains Mono', Consolas, Menlo, monospace; font-size: 12px; }
.dot-ok { color: #67c23a; }
.dot-fail { color: #f56c6c; }
.err-line { color: #f56c6c; font-size: 12px; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
@media (max-width: 1200px) {
  .stat-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>
